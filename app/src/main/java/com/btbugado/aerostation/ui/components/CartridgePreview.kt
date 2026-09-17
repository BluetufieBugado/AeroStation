package com.btbugado.aerostation.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.btbugado.aerostation.ui.theme.AeroGlassBorder
import com.btbugado.aerostation.ui.theme.AeroGlassWhite
import com.btbugado.aerostation.ui.theme.AeroGlassWhiteStrong
import com.btbugado.aerostation.ui.theme.AeroTextPrimary
import com.btbugado.aerostation.ui.theme.AeroTextSecondary
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay

/**
 * Cartucho "3D" todo em código: corpo de vidro desenhado em Canvas
 * (faixa de grip, espessura lateral, brilho) + a capa como etiqueta +
 * tilt com distância de câmera. Pseudo-3D de propósito: sem engine, sem
 * modelo — suficiente pra vitrine e pra inserção.
 */
@Composable
fun GlassCartridge(
    artUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
    tiltY: Float = 0f,
    tiltX: Float = 0f,
    cartWidth: androidx.compose.ui.unit.Dp = 190.dp
) {
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .width(cartWidth)
            .aspectRatio(0.78f)
            .graphicsLayer {
                rotationY = tiltY
                rotationX = tiltX
                cameraDistance = 12f * density.density
            }
    ) {
        // Corpo de vidro.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = 18.dp.toPx()
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(AeroGlassWhiteStrong, AeroGlassWhite)
                ),
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
            )
            // Faixa do grip (topo) + divisória.
            drawRect(
                color = Color.Black.copy(alpha = 0.12f),
                size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.2f)
            )
            drawLine(
                color = Color.White.copy(alpha = 0.4f),
                start = androidx.compose.ui.geometry.Offset(0f, size.height * 0.2f),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height * 0.2f),
                strokeWidth = 1.dp.toPx()
            )
            // Ranhuras do grip.
            val ridges = 4
            for (i in 0 until ridges) {
                val x = size.width * (0.2f + 0.2f * i)
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = androidx.compose.ui.geometry.Offset(x, size.height * 0.04f),
                    end = androidx.compose.ui.geometry.Offset(x, size.height * 0.16f),
                    strokeWidth = 2.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
            // Espessura fake (lateral direita + base) e fio de luz à esquerda.
            drawRect(
                color = Color.Black.copy(alpha = 0.18f),
                topLeft = androidx.compose.ui.geometry.Offset(size.width - 7.dp.toPx(), 0f),
                size = androidx.compose.ui.geometry.Size(7.dp.toPx(), size.height)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.12f),
                topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 7.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width, 7.dp.toPx())
            )
            drawLine(
                color = Color.White.copy(alpha = 0.3f),
                start = androidx.compose.ui.geometry.Offset(2.dp.toPx(), radius),
                end = androidx.compose.ui.geometry.Offset(2.dp.toPx(), size.height - radius),
                strokeWidth = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawRoundRect(
                color = AeroGlassBorder,
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )
        }
        // Etiqueta: a capa do jogo.
        if (artUrl != null) {
            AsyncImage(
                model = artUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 14.dp)
                    .fillMaxWidth(0.76f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
            )
        }
        // Reflexo diagonal do vidro.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val band = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.05f, size.height * 0.35f)
                lineTo(size.width * 0.45f, 0f)
                lineTo(size.width * 0.75f, 0f)
                lineTo(size.width * 0.35f, size.height * 0.35f)
                close()
            }
            drawPath(band, color = Color.White.copy(alpha = 0.10f))
        }
    }
}

/**
 * A Home avisa por aqui quando um overlay fullscreen (preview/inserção)
 * abre ou fecha, pra raiz esconder a BottomNavBar — sem isso ela ficava
 * visível e tocável por cima do véu (z-index não atravessa subárvores).
 */
val LocalHideNavForOverlay = compositionLocalOf<((Boolean) -> Unit)?> { null }

/**
 * Preview em TELA CHEIA estilo Wii (sem janelinha comprimida): nome do jogo
 * no topo à esquerda, cartucho flutuante no centro e duas pílulas
 * Voltar/Jogar embaixo. Jogar fecha aqui e devolve pra inserção.
 *
 * Layout em SpaceBetween pra caber em paisagem baixa: topo, centro (peso) e
 * base se distribuem sozinhos em qualquer altura.
 */
@Composable
fun CartridgePreviewDialog(
    displayName: String,
    consoleName: String?,
    artUrl: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val touchOk = LocalTouchActionsEnabled.current
    val swing = rememberInfiniteTransition(label = "cart-swing")
    val tiltY by swing.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cart-tilt"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 20.dp, bottom = 22.dp, start = 76.dp, end = 32.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Topo à esquerda: nome + console.
            Column {
                Text(
                    text = displayName,
                    color = AeroTextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (!consoleName.isNullOrBlank()) {
                    Text(
                        text = consoleName.uppercase(),
                        color = AeroTextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }

            // Centro: cartucho flutuante.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                GlassCartridge(
                    artUrl = artUrl,
                    title = displayName,
                    tiltY = tiltY,
                    tiltX = 6f,
                    cartWidth = 150.dp
                )
            }

            // Base: pílulas Voltar | Jogar.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WiiPillButton(
                    label = "Voltar",
                    primary = false,
                    autoFocus = false,
                    onClick = { if (touchOk) onDismiss() },
                    onConfirm = onDismiss,
                    onBack = onDismiss
                )
                Spacer(modifier = Modifier.width(18.dp))
                WiiPillButton(
                    label = "Jogar",
                    primary = true,
                    autoFocus = true,
                    onClick = { if (touchOk) onConfirm() },
                    onConfirm = onConfirm,
                    onBack = onDismiss
                )
            }
        }
    }
}

/**
 * Pílula brilhante estilo Wii: gradiente branco, borda azul na primária.
 */
@Composable
private fun WiiPillButton(
    label: String,
    primary: Boolean,
    autoFocus: Boolean,
    onClick: () -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(168.dp)
            .height(54.dp)
            .clip(RoundedCornerShape(50))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White, Color(0xFFDCE9F5))
                )
            )
            .border(
                2.dp,
                if (primary) Color(0xFF5EB3F7) else Color(0xFF9FB6CC),
                RoundedCornerShape(50)
            )
            .gamepadFocusable(
                autoFocus = autoFocus,
                onConfirm = onConfirm,
                onBack = onBack
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Text(
            text = label,
            color = Color(0xFF3A4A5A),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Inserção: o cartucho desce do topo até o slot, flash, e [onDone] boota o
 * jogo. Dura ~850ms; toques e teclas são engolidos no caminho.
 */
@Composable
fun CartridgeInsertOverlay(
    artUrl: String?,
    title: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val travel = remember { Animatable(0f) } // 0 = fora (topo), 1 = encaixado
    val flash = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        travel.animateTo(1f, animationSpec = tween(650, easing = FastOutSlowInEasing))
        flash.animateTo(1f, animationSpec = tween(200))
        delay(120)
        onDone()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        val maxHPx = with(density) { maxHeight.toPx() }
        val cartHPx = with(density) { (190.dp / 0.78f).toPx() }
        val slotHPx = with(density) { 96.dp.toPx() }
        val bottomMarginPx = with(density) { 110.dp.toPx() }
        // Do topo (fora da tela) até o slot, passando um pouco pra "encaixar".
        val startY = -cartHPx
        val endY = maxHPx - bottomMarginPx - slotHPx - cartHPx + with(density) { 26.dp.toPx() }
        val yPx = startY + (endY - startY) * travel.value

        GlassCartridge(
            artUrl = artUrl,
            title = title,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer { translationY = yPx }
        )

        // Slot.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -110.dp)
                .width(220.dp)
                .aspectRatio(220f / 96f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .border(2.dp, Color.White.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
        )

        // Flash do encaixe.
        if (flash.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.75f * flash.value))
            )
        }
    }
}
