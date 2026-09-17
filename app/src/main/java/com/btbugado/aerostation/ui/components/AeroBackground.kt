package com.btbugado.aerostation.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * No futuro: trocar por um GIF/vídeo escolhido pelo usuário, mantendo este
 * como fallback padrão.
 */
@Composable
fun AeroBackground(modifier: Modifier = Modifier) {
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

    Canvas(modifier = modifier.fillMaxSize()) {
        // Gradiente de fundo estilo céu/água
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(AeroSkyTop, AeroSkyMid, AeroSkyBottom)
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
    }
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
