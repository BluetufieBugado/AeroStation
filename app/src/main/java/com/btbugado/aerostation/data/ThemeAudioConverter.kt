package com.btbugado.aerostation.data

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Normaliza áudio na importação do tema, pra ninguém precisar de conversor
 * externo: o usuário escolhe mp3/wav/ogg/m4a/flac (o que tiver) e sai:
 * - Música -> music.m4a (AAC, pequeno, toca em tudo).
 * - Efeito -> <slot>.wav (PCM, à prova de bala no SoundPool).
 *
 * Por que não OGG? O Android só tem DECODIFICADOR Vorbis/Opus — nenhum
 * aparelho traz codificador. AAC tem (todo aparelho grava áudio de vídeo
 * em AAC), então é o alvo portátil de verdade.
 *
 * Tudo em STREAMING (nunca segura a música inteira na RAM: numa versão
 * anterior um ArrayList<Short> encaixotado estourava o heap em aparelho
 * modesto) e em thread de IO (blocking). Qualquer falha cai no fallback:
 * copiar o original como está (o app já toca esses formatos nativamente).
 */
object ThemeAudioConverter {
    private const val TIMEOUT_US = 10_000L
    private const val MAX_INPUT_BYTES = 60L * 1024L * 1024L

    /**
     * Teto de PCM decodificado por conversão (~15 min estéreo 44.1k).
     * Passou disso = aborta e cai no fallback (cópia do original).
     */
    private const val MAX_SHORTS = 15L * 60L * 44100L * 2L

    /**
     * Importa e normaliza. Retorna o NOME do arquivo gravado na pasta do
     * tema (o que vai no theme.json) ou null se nem a cópia deu certo.
     * kind: "music" ou o slot do efeito ("move", "confirm"...).
     */
    fun importAudio(context: Context, themeId: String, uri: Uri, kind: String): String? {
        val isMusic = kind == "music"
        val ext = sourceExt(context, uri).lowercase()
        // Atalhos sem transcodificar: já está no formato alvo.
        if (isMusic && ext in setOf("m4a", "aac")) {
            return ThemeStore.importMedia(context, themeId, uri, "music")
        }
        if (!isMusic && ext == "wav") {
            return ThemeStore.importMedia(context, themeId, uri, kind)
        }
        // Arquivo gigante? Nem tenta: copia original (rápido e seguro).
        val size = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: -1L
        if (size in 0..MAX_INPUT_BYTES) {
            val outName = if (isMusic) "music.m4a" else "$kind.wav"
            val outFile = themeFile(context, themeId, outName)
            val done = if (isMusic) {
                transcodeToM4a(context, uri, outFile)
            } else {
                decodeToWav(context, uri, outFile)
            }
            if (done) return outName
            runCatching { outFile.delete() }
        }
        // Fallback: original como está (o player toca nativo mesmo assim).
        return ThemeStore.importMedia(context, themeId, uri, kind)
    }

    /**
     * Normaliza um arquivo JÁ extraído (importação de .zip): música vira
     * music.m4a, efeito vira <slot>.wav. Mesma ext de destino? Só renomeia
     * (sem recomprimir à toa). Falha? Devolve false e o chamador mantém o
     * original. Roda em IO.
     */
    fun normalizeFile(context: Context, input: File, isMusic: Boolean, output: File): Boolean {
        if (!input.exists() || input.length() <= 0L) return false
        if (input.length() > MAX_INPUT_BYTES) return false
        val targetExt = if (isMusic) "m4a" else "wav"
        if (input.extension.lowercase() == targetExt) {
            return runCatching {
                input.copyTo(output, overwrite = true)
                output.exists() && output.length() > 0L
            }.getOrDefault(false)
        }
        val uri = Uri.fromFile(input)
        val ok = if (isMusic) {
            transcodeToM4a(context, uri, output)
        } else {
            decodeToWav(context, uri, output)
        }
        if (!ok) runCatching { output.delete() }
        return ok
    }

    // ---- decodificação compartilhada ----

    private class DecodeSession(
        val extractor: MediaExtractor,
        val decoder: MediaCodec,
        val srcFormat: MediaFormat
    )

    /** Abre extractor + decoder do primeiro track de áudio (sem start). */
    private fun openDecoder(context: Context, uri: Uri): DecodeSession? {
        val extractor = MediaExtractor()
        try {
            runCatching { extractor.setDataSource(context, uri, null) }.getOrNull()
                ?: run { runCatching { extractor.release() }; return null }
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (!mime.startsWith("audio/")) continue
                val decoder = runCatching { MediaCodec.createDecoderByType(mime) }.getOrNull()
                    ?: continue
                extractor.selectTrack(i)
                return DecodeSession(extractor, decoder, format)
            }
            runCatching { extractor.release() }
            return null
        } catch (e: Exception) {
            runCatching { extractor.release() }
            return null
        }
    }

    private fun closeSession(session: DecodeSession?) {
        if (session == null) return
        runCatching { session.decoder.stop() }
        runCatching { session.decoder.release() }
        runCatching { session.extractor.release() }
    }

    /** PCM 16-bit LE de um buffer de saída do decoder (float converte). */
    private fun pcm16Bytes(
        output: ByteBuffer,
        size: Int,
        encoding: Int
    ): ByteArray {
        val start = output.position()
        return if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            val count = size / 4
            val bytes = ByteArray(count * 2)
            output.position(start)
            for (i in 0 until count) {
                if (output.position() + 4 > start + size) break
                val sample = output.float.coerceIn(-1f, 1f)
                val pcm = (sample * Short.MAX_VALUE).toInt().toShort()
                bytes[i * 2] = (pcm.toInt() and 0xFF).toByte()
                bytes[i * 2 + 1] = ((pcm.toInt() shr 8) and 0xFF).toByte()
            }
            bytes
        } else {
            // 16-bit: copia direto (corta sobra ímpar, se houver).
            val count = (size / 2) * 2
            val bytes = ByteArray(count)
            output.position(start)
            var i = 0
            while (i < count && output.position() < start + size) {
                bytes[i] = output.get()
                i++
            }
            bytes.copyOf(i)
        }
    }

    // ---- WAV em streaming ----

    /** Qualquer áudio -> <slot>.wav, escrevendo por chunks no arquivo. */
    private fun decodeToWav(context: Context, uri: Uri, outFile: File): Boolean {
        val session = openDecoder(context, uri) ?: return false
        var ok = false
        try {
            session.decoder.configure(session.srcFormat, null, null, 0)
            session.decoder.start()
            RandomAccessFile(outFile, "rw").use { raf ->
                raf.write(ByteArray(44)) // cabeçalho provisório
                var sampleRate = 44100
                var channels = 2
                var totalBytes = 0L
                val over = pumpDecoder(session,
                    onFormat = { rate, ch ->
                        sampleRate = rate
                        channels = ch
                    },
                    onChunk = { bytes ->
                        raf.write(bytes)
                        totalBytes += bytes.size
                        totalBytes <= MAX_SHORTS * 2L
                    }
                )
                if (!over) return false
                if (totalBytes <= 0L || totalBytes > Int.MAX_VALUE) return false
                raf.seek(0)
                raf.write(wavHeader(totalBytes.toInt(), sampleRate, channels))
                ok = true
            }
            if (!ok) runCatching { outFile.delete() }
            return ok && outFile.exists() && outFile.length() > 0L
        } catch (e: Exception) {
            runCatching { outFile.delete() }
            return false
        } finally {
            closeSession(session)
        }
    }

    /**
     * Bomba do decoder: alimenta a entrada com o extractor e entrega cada
     * chunk decodificado (PCM 16-bit LE) no [onChunk]. Retorna false se
     * [onChunk] mandar parar (ex: teto) ou em erro fatal. Taxa/canais saem
     * no [onFormat] (podem mudar no primeiro formato).
     */
    private fun pumpDecoder(
        session: DecodeSession,
        onFormat: (sampleRate: Int, channels: Int) -> Unit,
        onChunk: (bytes: ByteArray) -> Boolean
    ): Boolean {
        val decoder = session.decoder
        val extractor = session.extractor
        val info = MediaCodec.BufferInfo()
        var sampleRate = 44100
        var channels = 2
        var inputDone = false
        var outputDone = false
        return runCatching {
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val input = decoder.getInputBuffer(inIndex) ?: return false
                        val read = extractor.readSampleData(input, 0)
                        if (read < 0) {
                            decoder.queueInputBuffer(
                                inIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(
                                inIndex, 0, read, extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex >= 0 -> {
                        val output = decoder.getOutputBuffer(outIndex)
                        if (output != null && info.size > 0) {
                            val fmt = decoder.outputFormat
                            sampleRate = fmt.intOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                            channels = fmt.intOr(MediaFormat.KEY_CHANNEL_COUNT, channels)
                            onFormat(sampleRate, channels)
                            val encoding = fmt.intOr(
                                MediaFormat.KEY_PCM_ENCODING,
                                AudioFormat.ENCODING_PCM_16BIT
                            )
                            if (!onChunk(pcm16Bytes(output, info.size, encoding))) {
                                decoder.releaseOutputBuffer(outIndex, false)
                                return false
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val fmt = decoder.outputFormat
                        sampleRate = fmt.intOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        channels = fmt.intOr(MediaFormat.KEY_CHANNEL_COUNT, channels)
                        onFormat(sampleRate, channels)
                    }
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (inputDone) outputDone = true
                    }
                }
            }
            true
        }.getOrDefault(false)
    }

    // ---- M4A em streaming (decode + encode intercalados) ----

    /** Qualquer áudio -> music.m4a (AAC LC 128k), sem acumular nada. */
    private fun transcodeToM4a(context: Context, uri: Uri, outFile: File): Boolean {
        val session = openDecoder(context, uri) ?: return false
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxStarted = false
        var trackIndex = -1
        var ok = false
        try {
            session.decoder.configure(session.srcFormat, null, null, 0)
            session.decoder.start()
            var sampleRate = 44100
            var channels = 2
            val decInfo = MediaCodec.BufferInfo()
            val encInfo = MediaCodec.BufferInfo()
            var decInputDone = false
            var decOutputDone = false
            var encInputDone = false
            var encOutputDone = false
            // Fragmento PCM aguardando vaga no encoder (+ se fecha o stream).
            var pending: ByteArray? = null
            var pendingPos = 0
            var pendingEos = false
            var totalShortsFed = 0L
            var fedAnything = false

            fun ensureEncoder(): Boolean {
                if (encoder != null) return true
                return runCatching {
                    val fmt = MediaFormat.createAudioFormat(
                        MediaFormat.MIMETYPE_AUDIO_AAC,
                        sampleRate,
                        channels
                    ).apply {
                        setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                        setInteger(
                            MediaFormat.KEY_AAC_PROFILE,
                            android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC
                        )
                        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
                    }
                    val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                    enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    enc.start()
                    muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    encoder = enc
                    true
                }.getOrDefault(false)
            }

            /** Despeja o pendente nos buffers de entrada do encoder. */
            fun feedPending(): Boolean {
                val enc = encoder ?: return true
                val chunk = pending ?: return true
                while (pendingPos < chunk.size) {
                    val inIndex = enc.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex < 0) return true // tenta de novo no próximo giro
                    val input = enc.getInputBuffer(inIndex) ?: return false
                    input.clear()
                    val take = minOf(chunk.size - pendingPos, input.capacity())
                    input.put(chunk, pendingPos, take)
                    pendingPos += take
                    val eos = pendingEos && pendingPos >= chunk.size
                    val ptsUs = (totalShortsFed * 1_000_000L) /
                        (sampleRate.toLong() * channels.coerceAtLeast(1))
                    totalShortsFed += (take / 2)
                    enc.queueInputBuffer(
                        inIndex, 0, take, ptsUs,
                        if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                    )
                    if (eos) encInputDone = true
                    if (totalShortsFed > MAX_SHORTS) return false
                }
                pending = null
                pendingPos = 0
                pendingEos = false
                return true
            }

            while (!encOutputDone) {
                // 1. Entrada do decoder.
                if (!decInputDone) {
                    val inIndex = session.decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val input = session.decoder.getInputBuffer(inIndex)
                        if (input == null) return false
                        val read = session.extractor.readSampleData(input, 0)
                        if (read < 0) {
                            session.decoder.queueInputBuffer(
                                inIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            decInputDone = true
                        } else {
                            session.decoder.queueInputBuffer(
                                inIndex, 0, read, session.extractor.sampleTime, 0
                            )
                            session.extractor.advance()
                        }
                    }
                }
                // 2. Saída do decoder -> pendente pro encoder.
                if (!decOutputDone && pending == null) {
                    val outIndex = session.decoder.dequeueOutputBuffer(decInfo, TIMEOUT_US)
                    when {
                        outIndex >= 0 -> {
                            val output = session.decoder.getOutputBuffer(outIndex)
                            if (output != null && decInfo.size > 0) {
                                val fmt = session.decoder.outputFormat
                                sampleRate = fmt.intOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                                channels = fmt.intOr(MediaFormat.KEY_CHANNEL_COUNT, channels)
                                val encoding = fmt.intOr(
                                    MediaFormat.KEY_PCM_ENCODING,
                                    AudioFormat.ENCODING_PCM_16BIT
                                )
                                pending = pcm16Bytes(output, decInfo.size, encoding)
                                pendingPos = 0
                                pendingEos = decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            } else if (decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                pending = ByteArray(0)
                                pendingPos = 0
                                pendingEos = true
                            }
                            session.decoder.releaseOutputBuffer(outIndex, false)
                            if (decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                decOutputDone = true
                            }
                        }
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val fmt = session.decoder.outputFormat
                            sampleRate = fmt.intOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                            channels = fmt.intOr(MediaFormat.KEY_CHANNEL_COUNT, channels)
                        }
                        outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (decInputDone) decOutputDone = true
                        }
                    }
                }
                // 3. Garante encoder antes de alimentar (usa taxa/canais reais).
                if (pending != null && encoder == null) {
                    if (!ensureEncoder()) return false
                }
                // 4. Pendente -> encoder. Fim seco sem bytes? Manda EOS vazio.
                if (pending == null && decOutputDone && encoder == null) {
                    if (!ensureEncoder()) return false
                    pending = ByteArray(0)
                    pendingPos = 0
                    pendingEos = true
                }
                if (!feedPending()) return false
                // 5. Saída do encoder -> muxer.
                val enc = encoder
                if (enc != null && !encOutputDone) {
                    val outIndex = enc.dequeueOutputBuffer(encInfo, TIMEOUT_US)
                    when {
                        outIndex >= 0 -> {
                            if (encInfo.size > 0 && muxStarted &&
                                encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                            ) {
                                val output = enc.getOutputBuffer(outIndex)
                                if (output == null) return false
                                muxer?.writeSampleData(trackIndex, output, encInfo)
                                fedAnything = true
                            }
                            enc.releaseOutputBuffer(outIndex, false)
                            if (encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                encOutputDone = true
                            }
                        }
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            trackIndex = muxer?.addTrack(enc.outputFormat) ?: -1
                            if (trackIndex < 0) return false
                            muxer?.start()
                            muxStarted = true
                        }
                        outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (encInputDone) encOutputDone = true
                        }
                    }
                }
                // Encoder existe mas a entrada secou sem EOS explícito?
                if (pending == null && decOutputDone && encoder != null && !encInputDone && fedAnything) {
                    pending = ByteArray(0)
                    pendingPos = 0
                    pendingEos = true
                }
            }
            ok = muxStarted && fedAnything && outFile.exists() && outFile.length() > 0L
            if (!ok) runCatching { outFile.delete() }
            return ok
        } catch (e: Exception) {
            runCatching { outFile.delete() }
            return false
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            closeSession(session)
        }
    }

    // ---- pequenos ----

    /** getInteger com default (o overload com default só existe na API 29+). */
    private fun MediaFormat.intOr(key: String, def: Int): Int =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(def) else def

    private fun wavHeader(dataSize: Int, sampleRate: Int, channels: Int): ByteArray {
        val ch = channels.coerceIn(1, 8)
        val out = ByteArray(44)
        fun writeInt(offset: Int, value: Int) {
            out[offset] = (value and 0xFF).toByte()
            out[offset + 1] = ((value shr 8) and 0xFF).toByte()
            out[offset + 2] = ((value shr 16) and 0xFF).toByte()
            out[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }
        fun writeShort(offset: Int, value: Int) {
            out[offset] = (value and 0xFF).toByte()
            out[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }
        "RIFF".forEachIndexed { i, c -> out[i] = c.code.toByte() }
        writeInt(4, 36 + dataSize)
        "WAVE".forEachIndexed { i, c -> out[8 + i] = c.code.toByte() }
        "fmt ".forEachIndexed { i, c -> out[12 + i] = c.code.toByte() }
        writeInt(16, 16)
        writeShort(20, 1)
        writeShort(22, ch)
        writeInt(24, sampleRate)
        writeInt(28, sampleRate * ch * 2)
        writeShort(32, (ch * 2))
        writeShort(34, 16)
        "data".forEachIndexed { i, c -> out[36 + i] = c.code.toByte() }
        writeInt(40, dataSize)
        return out
    }

    private fun themeFile(context: Context, themeId: String, name: String): File {
        val folder = File(File(context.filesDir, "themes"), themeId).apply { mkdirs() }
        return File(folder, name)
    }

    private fun sourceExt(context: Context, uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
            }
        }.getOrNull().orEmpty()
        return name.substringAfterLast('.', "").lowercase()
            .takeIf { it.isNotEmpty() && it.length <= 5 } ?: "bin"
    }
}
