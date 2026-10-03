package com.btbugado.aerostation.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import coil.compose.AsyncImage
import com.btbugado.aerostation.data.AppTheme
import com.btbugado.aerostation.data.ThemeEngine
import com.btbugado.aerostation.data.ThemeStore
import com.btbugado.aerostation.ui.theme.AeroGlassWhite
import com.btbugado.aerostation.ui.theme.AeroGlassWhiteStrong
import com.btbugado.aerostation.ui.theme.AeroHighlight
import com.btbugado.aerostation.ui.theme.AeroSkyBottom
import com.btbugado.aerostation.ui.theme.AeroSkyMid
import com.btbugado.aerostation.ui.theme.AeroSkyTop
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fundo animado padrão inspirado no Frutiger Aero: gradiente "céu/água" com
 * "bolhas" de vidro translúcidas subindo lentamente + ondas suaves na parte
 * inferior (aceno ao XMB da Sony, mas em vidro/aqua Aero). Tudo desenhado em
 * Canvas (sem bitmap/gif), então o custo de performance é mínimo.
 *
 * Com tema personalizado: se o tema tem fundo, ele aparece em tela cheia
 * por baixo de tudo (com véu escuro pra manter o texto legível):
 * - Imagem ou GIF/WebP animado: via Coil (o gif anima sozinho).
 * - Vídeo curto: player em loop, mudo, pausado fora do app.
 * As bolhas/ondas de vidro continuam por cima, nas cores do tema.
 */
@Composable
fun AeroBackground(modifier: Modifier = Modifier) {
    // Lê o ativo aqui: trocar de tema recompõe só o fundo (e o resto do app
    // pelas cores, cada um na sua).
    val bgImagePath = ThemeEngine.active.backgroundImagePath
    val bgCrop = ThemeEngine.active.backgroundFit != AppTheme.FIT_FIT
    val bubbles = remember {
        List(14) {
            Bubble(
                startX = Random.nextFloat(),
                radius = Random.nextFloat() * 40f + 20f,
                speed = Random.nextFloat() * 0.4f + 0.2f,
                phase = Random.nextFloat()
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "aero-bg")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Fundo do tema (quando há): cobre tudo, sem distorcer.
        if (bgImagePath != null) {
            if (ThemeStore.isVideoBackground(bgImagePath)) {
                VideoBackground(path = bgImagePath, crop = bgCrop)
            } else {
                AsyncImage(
                    model = bgImagePath,
                    contentDescription = null,
                    contentScale = if (bgCrop) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Véu escuro: texto branco continua legível em foto clara.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f))
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Gradiente de fundo estilo céu/água (some se há foto? não: vira
            // véu colorido por cima da foto, mantendo a identidade do tema).
            drawRect(
                brush = Brush.verticalGradient(
                    colors = if (bgImagePath == null) {
                        listOf(AeroSkyTop, AeroSkyMid, AeroSkyBottom)
                    } else {
                        listOf(
                            AeroSkyTop.copy(alpha = 0.45f),
                            AeroSkyMid.copy(alpha = 0.25f),
                            AeroSkyBottom.copy(alpha = 0.35f)
                        )
                    }
                )
            )

        // Faixa de "brilho" horizontal sutil (efeito glossy)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(AeroHighlight.copy(alpha = 0.12f), Color.Transparent),
                startY = 0f,
                endY = size.height * 0.35f
            )
        )

        // Bolhas de vidro subindo
        bubbles.forEach { bubble ->
            val progress = (time * bubble.speed + bubble.phase) % 1f
            val y = size.height * (1f - progress)
            val x = size.width * bubble.startX
            val alpha = (0.25f * (1f - progress)).coerceIn(0f, 0.25f)

            drawCircle(
                color = AeroGlassWhite.copy(alpha = alpha),
                radius = bubble.radius,
                center = Offset(x, y)
            )
        }

        // Ondas XMB/Aero na parte inferior: 3 camadas senoidais derivando
        // na horizontal, preenchidas com gradiente de vidro + filete glossy
        // na crista. De trás pra frente (a da frente mais opaca).
        val stepPx = 8f
        waves.forEach { wave ->
            val baseY = size.height * wave.baseYFrac
            val amp = size.height * wave.ampFrac
            val fill = Path()
            val crest = Path()
            var x = 0f
            var first = true
            while (x <= size.width + stepPx) {
                val xc = x.coerceAtMost(size.width)
                val y = baseY - amp * sin(
                    (2f * PI * (wave.cycles * xc / size.width + wave.drift * time) + wave.phase).toFloat()
                )
                if (first) {
                    fill.moveTo(xc, size.height)
                    fill.lineTo(xc, y)
                    crest.moveTo(xc, y)
                    first = false
                } else {
                    fill.lineTo(xc, y)
                    crest.lineTo(xc, y)
                }
                x += stepPx
            }
            fill.lineTo(size.width, size.height)
            fill.close()
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        wave.color.copy(alpha = wave.alphaTop),
                        wave.color.copy(alpha = wave.alphaBottom)
                    ),
                    startY = baseY - amp,
                    endY = size.height
                )
            )
            // Brilho da crista: halo largo sutil + filete fino (gloss Aero).
            drawPath(
                path = crest,
                color = AeroHighlight.copy(alpha = 0.10f),
                style = Stroke(width = 7f)
            )
            drawPath(
                path = crest,
                color = AeroHighlight.copy(alpha = 0.38f),
                style = Stroke(width = 2f)
            )
        }
        } // Canvas
    } // Box
}

private data class Wave(
    /** Altura base da camada (fração da altura, ex: 0.82 = 82%). */
    val baseYFrac: Float,
    /** Amplitude (fração da altura). */
    val ampFrac: Float,
    /** Nº de ciclos na largura da tela. */
    val cycles: Float,
    /** Velocidade de deriva (ciclos por loop de 20s). */
    val drift: Float,
    val phase: Float,
    val color: Color,
    val alphaTop: Float,
    val alphaBottom: Float,
)

private val waves = listOf(
    // drift em ciclos inteiros por loop: sem salto quando o tempo reinicia.
    Wave(
        baseYFrac = 0.80f, ampFrac = 0.022f, cycles = 1.5f, drift = 1f,
        phase = 0f, color = AeroGlassWhite, alphaTop = 0.30f, alphaBottom = 0.06f
    ),
    Wave(
        baseYFrac = 0.87f, ampFrac = 0.028f, cycles = 2f, drift = -1f,
        phase = 1.7f, color = AeroGlassWhiteStrong, alphaTop = 0.34f, alphaBottom = 0.08f
    ),
    Wave(
        baseYFrac = 0.93f, ampFrac = 0.020f, cycles = 2.6f, drift = 2f,
        phase = 3.4f, color = AeroHighlight, alphaTop = 0.30f, alphaBottom = 0.10f
    ),
)

private data class Bubble(
    val startX: Float,
    val radius: Float,
    val speed: Float,
    val phase: Float
)

/**
 * Vídeo de fundo em loop: TextureView + MediaPlayer preenchendo por CROP
 * (matriz de escala + centralização — a receita de vídeo-papel-de-parede).
 * Se o vídeo for 16:9 e a tela 2340x1080, ele cresce até cobrir e o
 * excedente vaza pra fora: sem faixas pretas. (O VideoView sozinho só sabe
 * "fit", e o zoom na view não mexe na superfície dele em todo aparelho —
 * foi o que deixou as bordas pra trás.)
 *
 * Mudo (a música do tema já toca por outro canal) e pausado quando o app
 * sai de cena (emulador por cima etc). Prefira vídeos curtos.
 */
@Composable
private fun VideoBackground(path: String, crop: Boolean) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var textureView by remember(path) { mutableStateOf<TextureView?>(null) }
    var surface by remember(path) { mutableStateOf<Surface?>(null) }
    var player by remember(path) { mutableStateOf<MediaPlayer?>(null) }
    var videoSize by remember(path) { mutableStateOf<Pair<Int, Int>?>(null) }
    var viewSize by remember(path) { mutableStateOf<Pair<Int, Int>?>(null) }
    // Deveria estar tocando? (false no pause: relógio do watchdog para).
    var shouldPlay by remember(path) { mutableStateOf(true) }

    fun applyCropTransform() {
        val tv = textureView ?: return
        // "Ajustar": sem matriz (o TextureView mostra tudo sozinho).
        if (!crop) {
            tv.setTransform(null)
            return
        }
        val (vidW, vidH) = videoSize ?: return
        val (vw, vh) = viewSize ?: return
        if (vidW <= 0 || vidH <= 0 || vw <= 0 || vh <= 0) return
        val scale = maxOf(vw.toFloat() / vidW, vh.toFloat() / vidH)
        val dx = (vw - vidW * scale) / 2f
        val dy = (vh - vidH * scale) / 2f
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate(dx, dy)
        tv.setTransform(matrix)
    }

    // Trocar Preencher/Ajustar com o tema ativo reaplica na hora.
    LaunchedEffect(crop) { applyCropTransform() }

    // DONO ÚNICO do player: nasce uma vez por vídeo e é solto no dispose.
    // A superfície entra e sai (abre jogo, apaga tela...): em vez de criar
    // outro player a cada volta (vazava decoder até congelar), gruda a
    // superfície nova no MESMO player via setSurface.
    LaunchedEffect(path) {
        val mp = MediaPlayer()
        player = mp
        runCatching {
            mp.setDataSource(path)
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            mp.setOnVideoSizeChangedListener { _, w, h ->
                if (w > 0 && h > 0) {
                    videoSize = w to h
                    applyCropTransform()
                }
            }
            mp.setOnPreparedListener {
                surface?.let { runCatching { mp.setSurface(it) } }
                applyCropTransform()
                if (shouldPlay) runCatching { mp.start() }
            }
            mp.prepareAsync()
        }
        try {
            awaitCancellation()
        } finally {
            runCatching { mp.stop() }
            runCatching { mp.release() }
            if (player === mp) player = null
        }
    }

    // Superfície nova? Gruda no player atual e garante o play.
    LaunchedEffect(path, surface, player) {
        val mp = player ?: return@LaunchedEffect
        val s = surface ?: return@LaunchedEffect
        runCatching { mp.setSurface(s) }
        applyCropTransform()
        if (shouldPlay) runCatching { if (!mp.isPlaying) mp.start() }
    }

    // Cão de guarda: se deveria tocar e parou (codec soluçou, superfície
    // trocou no meio do loop...), retoma sozinho. Só roda com o fundo ativo.
    LaunchedEffect(path) {
        while (true) {
            delay(5000)
            val mp = player ?: continue
            if (!shouldPlay || surface == null) continue
            val playing = runCatching { mp.isPlaying }.getOrDefault(true)
            if (!playing) runCatching { mp.start() }
        }
    }

    DisposableEffect(lifecycleOwner, path) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    shouldPlay = false
                    runCatching { player?.pause() }
                }
                Lifecycle.Event.ON_RESUME -> {
                    shouldPlay = true
                    runCatching {
                        player?.let { if (!it.isPlaying) it.start() }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        st: SurfaceTexture, width: Int, height: Int
                    ) {
                        surface = Surface(st)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        st: SurfaceTexture, width: Int, height: Int
                    ) = Unit

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        surface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
                textureView = this
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val size = coordinates.size
                if (size.height > 0) {
                    viewSize = size.width to size.height
                    applyCropTransform()
                }
            }
    )

    DisposableEffect(path) {
        // O player é solto no finally do LaunchedEffect dono; aqui só a
        // superfície (senão o dispose leria o player do tema NOVO e o
        // mataria por engano na troca de fundo).
        onDispose {
            runCatching { surface?.release() }
            surface = null
        }
    }
}
