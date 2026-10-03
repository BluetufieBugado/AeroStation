package com.btbugado.aerostation.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.btbugado.aerostation.R
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/**
 * Áudio do app, 100% gerado em código (sem assets, sem dependência nova):
 * - Efeitos curtos via [SoundPool]: cursor, confirmar, abrir/fechar dialog, launch.
 * - Música ambiente em looping via [MediaPlayer] (pad de 8s sintetizado).
 *
 * SONS PERSONALIZADOS, em ordem de prioridade:
 * 1. Arquivos do TEMA ATIVO (Configurações > Temas) — vale pra música e pros 5 efeitos.
 * 2. Arquivos em `app/src/main/res/raw/` (compile): bgm_loop, sfx_move,
 *    sfx_confirm, sfx_open, sfx_close, sfx_launch (mp3, wav ou ogg).
 * 3. Sintetizados em código (fallback de fábrica).
 */
object AppAudio {

    private const val RATE = 22050

    private var appContext: Context? = null
    private var ready = false

    private var soundPool: SoundPool? = null
    private var moveId = 0
    private var confirmId = 0
    private var openId = 0
    private var closeId = 0
    private var launchId = 0

    private var player: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var focusGranted = false

    /** Trava do player: reset/prepare (IO) nunca entrelaçam com pause/start (main). */
    private val playerLock = Any()

    /** Estado desejado (liga/desliga conforme ciclo de vida + foco + volume). */
    private var musicWanted = false
    private var appResumed = true
    private var hasFocus = true

    /**
     * Supressão pelo viewer de vídeo da galeria (contador, não boolean: cada
     * vídeo composto incrementa, cada dispose decrementa — recomposição com
     * troca de URI nunca deixa a música presa em nenhum dos dois estados).
     * O VideoView não pede foco de áudio, então sem isso a música tocaria por
     * cima do vídeo; fotos não suprem (só o VideoPlayer chama).
     */
    private var videoSuppressCount = 0

    private fun musicAttributes() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private fun toastMain(msg: String) {
        val ctx = appContext ?: return
        Handler(Looper.getMainLooper()).post {
            runCatching { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
        }
    }

    // Retentativa de música: foco negado NÃO tem callback — sem isso, uma
    // negação (ou um LOSS atrasado do emulador morrendo na volta) virava
    // silêncio até o próximo ciclo de vida. Tenta de novo poucas vezes e
    // desiste (se o dono legítimo for outro app, não ficamos puxando).
    private val mainHandler = Handler(Looper.getMainLooper())
    private var focusRetriesLeft = 0
    private val focusRetryRunnable = Runnable {
        var repost = false
        synchronized(playerLock) {
            if (focusRetriesLeft > 0) focusRetriesLeft--
            val ctx = appContext
            val playing: Boolean = runCatching { player?.isPlaying == true }.getOrDefault(true)
            if (ready && musicWanted && appResumed && ctx != null &&
                AudioSettingsStore.musicVolume(ctx) > 0f && !playing
            ) {
                // Otimista de novo: o foco pode ter liberado nesse meio-tempo.
                hasFocus = true
                updateMusicPlayback()
                repost = focusRetriesLeft > 0
            } else {
                focusRetriesLeft = 0
            }
        }
        if (repost) repostFocusRetry()
    }

    // Função separada de propósito: o Runnable não pode se referenciar no
    // próprio inicializador (ciclo de inferência do compilador).
    private fun repostFocusRetry() {
        mainHandler.postDelayed(focusRetryRunnable, 1500)
    }

    private fun scheduleFocusRetry() {
        if (focusRetriesLeft > 0) return // cadeia já rolando
        focusRetriesLeft = 4
        mainHandler.postDelayed(focusRetryRunnable, 700)
    }

    private fun cancelFocusRetry() {
        focusRetriesLeft = 0
        mainHandler.removeCallbacks(focusRetryRunnable)
    }

    /**
     * Prepara tudo. Chamado uma vez na abertura do app, em thread de IO
     * (gera os .wav no cache, carrega o SoundPool e prepara o player).
     */
    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (ready) {
            updateMusicPlayback()
            return
        }
        runCatching {
            // v2: invalida os .wav gerados com o header sem data-size (silenciosos).
            val dir = File(app.cacheDir, "audio_v2").apply { mkdirs() }
            soundPool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            loadSfx(dir, ThemeEngine.active)

            audioManager = app.getSystemService(AudioManager::class.java)
            player = MediaPlayer().apply {
                setAudioAttributes(musicAttributes())
                setupBgm(this, dir, ThemeEngine.active)
                isLooping = true
                prepare()
            }
            ready = true
            musicWanted = true
            updateMusicPlayback()
        }
    }

    /**
     * Reaplica os sons/música do tema ativo (chamado ao trocar de tema).
     * Roda em thread de IO (prepare() bloqueia) — a UI chama com
     * `scope.launch(Dispatchers.IO) { AppAudio.applyTheme(context) }`.
     *
     * Blindado contra o "música some": reset() limpa os atributos (reaplico),
     * tudo roda sob lock (pausa do ciclo de vida não entra no meio do
     * prepare) e se a música do tema falhar, volta pro padrão com aviso —
     * em vez de deixar o player morto e mudo.
     */
    fun applyTheme(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (!ready) return
        val theme = ThemeEngine.active
        synchronized(playerLock) {
            runCatching {
                val dir = File(app.cacheDir, "audio_v2").apply { mkdirs() }
                loadSfx(dir, theme)
                val mediaPlayer = player ?: return@runCatching
                runCatching { if (mediaPlayer.isPlaying) mediaPlayer.pause() }
                val themeOk = runCatching {
                    mediaPlayer.reset()
                    mediaPlayer.setAudioAttributes(musicAttributes())
                    setupBgm(mediaPlayer, dir, theme)
                    mediaPlayer.isLooping = true
                    mediaPlayer.prepare()
                }.isSuccess
                if (!themeOk) {
                    runCatching {
                        mediaPlayer.reset()
                        mediaPlayer.setAudioAttributes(musicAttributes())
                        setupBgm(mediaPlayer, dir, AppTheme.default())
                        mediaPlayer.isLooping = true
                        mediaPlayer.prepare()
                    }
                    toastMain(context.getString(R.string.audio_toast_theme_music_fallback))
                }
                updateMusicPlayback()
            }
        }
    }

    /** (Re)carrega os 5 efeitos: arquivo do tema > res/raw > sintetizado. */
    private fun loadSfx(dir: File, theme: AppTheme) {
        unloadSfx()
        moveId = loadTone(dir, "sfx_move.wav", theme.soundPaths["move"]) { sweep(500f, 760f, 0.07) }
        confirmId = loadTone(dir, "sfx_confirm.wav", theme.soundPaths["confirm"]) {
            jingle(listOf(523.25f to 0.08, 783.99f to 0.12))
        }
        openId = loadTone(dir, "sfx_open.wav", theme.soundPaths["open"]) { sweep(350f, 700f, 0.12) }
        closeId = loadTone(dir, "sfx_close.wav", theme.soundPaths["close"]) { sweep(700f, 350f, 0.12) }
        launchId = loadTone(dir, "sfx_launch.wav", theme.soundPaths["launch"]) {
            jingle(listOf(523.25f to 0.09, 659.25f to 0.09, 783.99f to 0.09, 1046.5f to 0.18))
        }
    }

    private fun unloadSfx() {
        val pool = soundPool ?: return
        for (id in listOf(moveId, confirmId, openId, closeId, launchId)) {
            if (id != 0) runCatching { pool.unload(id) }
        }
        moveId = 0; confirmId = 0; openId = 0; closeId = 0; launchId = 0
    }

    /**
     * Aponta o player pra fonte certa, em ordem: música do tema > res/raw do
     * usuário (mp3/wav/ogg) > pad ambiente sintetizado.
     */
    private fun setupBgm(mediaPlayer: MediaPlayer, dir: File, theme: AppTheme) {
        val app = appContext ?: return
        val themeMusic = theme.musicPath?.let { File(it) }
            ?.takeIf { it.exists() && it.length() > 0L }
        if (themeMusic != null) {
            mediaPlayer.setDataSource(themeMusic.absolutePath)
            return
        }
        val bgmResId = rawId("bgm_loop")
        if (bgmResId != 0) {
            // Arquivo do usuário em res/raw (mp3/wav/ogg).
            val afd = app.resources.openRawResourceFd(bgmResId)
            mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            // fd fica aberto pela vida do app (player em looping).
        } else {
            val bgm = File(dir, "bgm_loop.wav")
            if (!bgm.exists() || bgm.length() == 0L) {
                bgm.outputStream().use { it.write(ambientLoop()) }
            }
            mediaPlayer.setDataSource(bgm.absolutePath)
        }
    }

    // Efeitos (chamados da UI; viram no-op antes do init ou com volume 0).

    fun playMove() = play(moveId)
    fun playConfirm() = play(confirmId)
    fun playOpen() = play(openId)
    fun playClose() = play(closeId)
    fun playLaunch() = play(launchId)

    private fun play(soundId: Int) {
        val ctx = appContext ?: return
        if (!ready || soundId == 0) return
        val volume = AudioSettingsStore.sfxVolume(ctx)
        if (volume <= 0f) return
        runCatching { soundPool?.play(soundId, volume, volume, 1, 0, 1f) }
    }

    // Música.

    fun setMusicVolume(volume: Float) {
        val ctx = appContext ?: return
        AudioSettingsStore.setMusicVolume(ctx, volume)
        updateMusicPlayback()
    }

    fun onAppPaused() {
        appResumed = false
        cancelFocusRetry()
        // Devolve o foco (o emulador/jogo assume limpo) e zera o estado: na
        // volta pedimos de novo. Sem isso, emulador que nunca devolve o foco
        // (ex: DuckStation, melonDS) deixava a música muda pra sempre, pois
        // o hasFocus=false ficava travado e nunca re-requestávamos.
        runCatching {
            focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        }
        focusRequest = null
        focusGranted = false
        updateMusicPlayback()
    }

    fun onAppResumed() {
        appResumed = true
        // Otimista de propósito: pede o foco de novo no update. Se outro app
        // ainda segurar, o request com GAIN retoma (ou nega e segue pausado).
        hasFocus = true
        updateMusicPlayback()
    }

    /**
     * Chamado ao ABRIR um vídeo da galeria: o VideoView não pede foco de
     * áudio, então o listener de foco nunca pausaria a música sozinho — ela
     * tocaria por cima do vídeo (o bug). Suprime até o onVideoClosed.
     */
    fun onVideoOpened() {
        videoSuppressCount++
        updateMusicPlayback()
    }

    /**
     * Chamado ao fechar um vídeo da galeria: libera a supressão do
     * onVideoOpened e, como o player do vídeo pode não devolver o ganho de
     * foco (ou o evento se perde), e a música ficava muda pra sempre, Restabelece do zero — abandona qualquer pedido
     * travado e pede de novo. Se outro app (ex: Spotify) segurar o foco de
     * verdade, o request é negado e seguimos pausados, sem roubar nada.
     */
    fun onVideoClosed() {
        videoSuppressCount = (videoSuppressCount - 1).coerceAtLeast(0)
        runCatching {
            focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        }
        focusRequest = null
        focusGranted = false
        hasFocus = true
        updateMusicPlayback()
    }

    private fun updateMusicPlayback() {
        // Sob o mesmo lock do applyTheme: ninguém pausa/dá start no meio
        // de um reset/prepare (synchronized é reentrante, sem deadlock).
        synchronized(playerLock) {
            val ctx = appContext ?: return
            val volume = AudioSettingsStore.musicVolume(ctx)
            val want = ready && musicWanted && appResumed && hasFocus && volume > 0f &&
                videoSuppressCount == 0
            val mediaPlayer = player ?: return
            runCatching {
                if (want) {
                    ensureFocus()
                    mediaPlayer.setVolume(volume, volume)
                    if (!mediaPlayer.isPlaying) mediaPlayer.start()
                } else {
                    if (mediaPlayer.isPlaying) mediaPlayer.pause()
                }
            }
            // Deveria estar tocando e não está (foco negado, LOSS atrasado)?
            // Reagenda tentativas em vez de aceitar o silêncio. O hasFocus
            // fica de fora de propósito: é justamente ele que pode estar
            // travado em false sem callback pra avisar. A supressão de vídeo
            // fica de fora pelo motivo oposto: silêncio intencional, sem retry.
            val shouldBePlaying = ready && musicWanted && appResumed && volume > 0f &&
                videoSuppressCount == 0
            val silent = shouldBePlaying &&
                runCatching { mediaPlayer.isPlaying != true }.getOrDefault(false)
            if (silent) scheduleFocusRetry() else cancelFocusRetry()
        }
    }

    private fun ensureFocus() {
        if (focusGranted) return
        val manager = audioManager ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(
                { change ->
                    // Perdeu o foco (ex: emulador/jogo assumiu) = pausa;
                    // recuperou = volta. Sem ducking parcial, simples e audível.
                    hasFocus = change == AudioManager.AUDIOFOCUS_GAIN
                    updateMusicPlayback()
                },
                Handler(Looper.getMainLooper())
            )
            .build()
        focusRequest = request
        focusGranted =
            manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        // Sem foco concedido, não força: updateMusicPlayback tenta de novo
        // no próximo evento (ou segue pausado, sem travar nada).
        if (!focusGranted) hasFocus = false
    }

    // Síntese (gera .wav 16-bit mono no cache).

    /**
     * ID de recurso em `res/raw` pelo nome (0 = não existe). Via lookup por
     * string de propósito: assim o código compila COM ou SEM os arquivos do
     * usuário — quem manda é a presença deles na rebuild.
     */
    private fun rawId(name: String): Int {
        val ctx = appContext ?: return 0
        return runCatching {
            ctx.resources.getIdentifier(name, "raw", ctx.packageName)
        }.getOrDefault(0)
    }

    private fun loadTone(dir: File, name: String, themePath: String?, gen: () -> FloatArray): Int {
        // Arquivo do tema tem prioridade máxima (ex: música/efeito do pacote).
        themePath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0L }?.let {
            return soundPool?.load(it.absolutePath, 1) ?: 0
        }
        // Arquivo do usuário tem prioridade (ex: sfx_move.wav -> "sfx_move").
        val resId = rawId(name.substringBeforeLast('.'))
        if (resId != 0) return soundPool?.load(appContext, resId, 1) ?: 0
        val file = File(dir, name)
        if (!file.exists() || file.length() == 0L) {
            file.outputStream().use { it.write(wavBytes(gen())) }
        }
        return soundPool?.load(file.absolutePath, 1) ?: 0
    }

    /** Tom com varredura de frequência e decaimento exponencial. */
    private fun sweep(fromHz: Float, toHz: Float, seconds: Double): FloatArray {
        val n = (seconds * RATE).toInt().coerceAtLeast(1)
        val out = FloatArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val freq = fromHz + (toHz - fromHz) * t
            phase += 2.0 * PI * freq / RATE
            out[i] = (sin(phase) * Math.exp(-3.0 * t)).toFloat()
        }
        return out
    }

    /** Sequência de notas (frequência + duração cada). */
    private fun jingle(notes: List<Pair<Float, Double>>): FloatArray {
        val total = notes.sumOf { (it.second * RATE).toInt() }
        val out = FloatArray(total)
        var pos = 0
        for ((freq, seconds) in notes) {
            val n = (seconds * RATE).toInt()
            var phase = 0.0
            for (i in 0 until n) {
                val t = i.toDouble() / n
                phase += 2.0 * PI * freq / RATE
                // Ataque rápido + decaimento por nota, sem clique entre elas.
                val env = (1.0 - Math.exp(-40.0 * t)) * Math.exp(-2.5 * t)
                out[pos++] = (sin(phase) * env * 0.8).toFloat()
            }
        }
        return out
    }

    /** Pad ambiente de 8s (Am“F“C“G) com crossfade entre acordes. */
    private fun ambientLoop(): ByteArray {
        val seconds = 8.0
        val n = (seconds * RATE).toInt()
        val out = FloatArray(n)
        // A2/E3/A3, F2/C3/F3, C3/E3/G3, G2/D3/G3 — 2s cada.
        val chords = listOf(
            floatArrayOf(110f, 164.81f, 220f),
            floatArrayOf(87.31f, 130.81f, 174.61f),
            floatArrayOf(130.81f, 164.81f, 196f),
            floatArrayOf(98f, 146.83f, 196f)
        )
        val chordLen = n / chords.size
        val fade = (0.5 * RATE).toInt()
        for ((c, chord) in chords.withIndex()) {
            val phases = DoubleArray(chord.size)
            for (i in 0 until chordLen) {
                val global = c * chordLen + i
                if (global >= n) break
                // Envelope: sobe no começo do acorde, desce no fim (crossfade).
                val attack = (i.toDouble() / fade).coerceIn(0.0, 1.0)
                val release = ((chordLen - i).toDouble() / fade).coerceIn(0.0, 1.0)
                val env = attack.coerceAtMost(release)
                    .let { it * it * (3 - 2 * it) } // smoothstep, sem clique
                var sample = 0.0
                for ((o, freq) in chord.withIndex()) {
                    phases[o] += 2.0 * PI * freq / RATE
                    sample += sin(phases[o])
                }
                out[global] += (sample / chord.size * env * 0.35).toFloat()
            }
        }
        // Fade geral nas bordas pra emendar o loop sem clique.
        val edge = (0.25 * RATE).toInt()
        for (i in 0 until edge) {
            val t = i.toDouble() / edge
            out[i] = (out[i] * t).toFloat()
            out[n - 1 - i] = (out[n - 1 - i] * t).toFloat()
        }
        return wavBytes(out)
    }

    private fun wavBytes(samples: FloatArray): ByteArray {
        val dataSize = samples.size * 2
        val out = ByteArray(44 + dataSize)
        fun writeInt(offset: Int, value: Int) {
            out[offset] = (value and 0xFF).toByte()
            out[offset + 1] = ((value shr 8) and 0xFF).toByte()
            out[offset + 2] = ((value shr 16) and 0xFF).toByte()
            out[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }
        fun writeShort(offset: Int, value: Short) {
            out[offset] = (value.toInt() and 0xFF).toByte()
            out[offset + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
        }
        "RIFF".forEachIndexed { i, c -> out[i] = c.code.toByte() }
        writeInt(4, 36 + dataSize)
        "WAVE".forEachIndexed { i, c -> out[8 + i] = c.code.toByte() }
        "fmt ".forEachIndexed { i, c -> out[12 + i] = c.code.toByte() }
        writeInt(16, 16)
        writeShort(20, 1) // PCM
        writeShort(22, 1) // mono
        writeInt(24, RATE)
        writeInt(28, RATE * 2)
        writeShort(32, 2)
        writeShort(34, 16)
        "data".forEachIndexed { i, c -> out[36 + i] = c.code.toByte() }
        writeInt(40, dataSize) // tamanho do bloco de dados (sem ele o som sai vazio!)
        for (i in samples.indices) {
            val pcm = (samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            writeShort(44 + i * 2, pcm)
        }
        return out
    }
}
