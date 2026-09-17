package com.btbugado.aerostation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.btbugado.aerostation.ui.AppScreen
import com.btbugado.aerostation.ui.theme.AeroGlassBorder
import com.btbugado.aerostation.ui.theme.AeroGlassWhite
import com.btbugado.aerostation.ui.theme.AeroGlassWhiteStrong
import com.btbugado.aerostation.ui.theme.AeroTextPrimary
import com.btbugado.aerostation.ui.theme.AeroTextSecondary
import kotlin.math.cos
import kotlin.math.sin

/**
 * Barra inferior "de vidro" com as abas do app, lembrando o menu do
 * Nintendo Switch: Início (biblioteca de jogos), Apps (launcher de Android),
 * Conquistas (RetroAchievements) e Ajustes (configurações do RetroAero).
 */
@Composable
fun BottomNavBar(
    current: AppScreen,
    onSelect: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(AeroGlassWhiteStrong, AeroGlassWhite)))
            .border(1.dp, AeroGlassBorder, RoundedCornerShape(28.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavBarItem(
            icon = NavIconType.HOME,
            label = "Início",
            selected = current == AppScreen.HOME,
            onClick = { onSelect(AppScreen.HOME) }
        )
        NavBarItem(
            icon = NavIconType.CLOCK,
            label = "Jogados",
            selected = current == AppScreen.PLAYED,
            onClick = { onSelect(AppScreen.PLAYED) }
        )
        NavBarItem(
            icon = NavIconType.APPS,
            label = "Apps",
            selected = current == AppScreen.APPS,
            onClick = { onSelect(AppScreen.APPS) }
        )
        NavBarItem(
            icon = NavIconType.PHOTO,
            label = "Álbum",
            selected = current == AppScreen.GALLERY,
            onClick = { onSelect(AppScreen.GALLERY) }
        )
        NavBarItem(
            icon = NavIconType.TROPHY,
            label = "Conquistas",
            selected = current == AppScreen.ACHIEVEMENTS,
            onClick = { onSelect(AppScreen.ACHIEVEMENTS) }
        )
        NavBarItem(
            icon = NavIconType.SETTINGS,
            label = "Ajustes",
            selected = current == AppScreen.SETTINGS,
            onClick = { onSelect(AppScreen.SETTINGS) }
        )
    }
}

@Composable
private fun NavBarItem(
    icon: NavIconType,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background by animateColorAsState(
        targetValue = if (selected) AeroGlassWhiteStrong else Color.Transparent,
        label = "nav-item-bg"
    )
    val tint = if (selected) AeroTextPrimary else AeroTextSecondary
    val touchOk = LocalTouchActionsEnabled.current

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .gamepadFocusable(onConfirm = onClick)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (touchOk) onClick() }
            )
            // 6 abas agora: recuo menor pra caber sem espremer.
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NavIcon(type = icon, tint = tint)
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

private enum class NavIconType { HOME, CLOCK, APPS, PHOTO, TROPHY, SETTINGS }

/** Moldura de foto com sol e montanha (aba Álbum). */
private fun DrawScope.drawPhotoIcon(color: Color) {
    val w = size.width
    val h = size.height
    val strokeW = w * 0.11f
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.06f, h * 0.14f),
        size = Size(w * 0.88f, h * 0.72f),
        cornerRadius = CornerRadius(w * 0.08f),
        style = Stroke(width = strokeW, cap = StrokeCap.Round)
    )
    drawCircle(color = color, radius = w * 0.09f, center = Offset(w * 0.32f, h * 0.38f))
    drawLine(
        color = color,
        start = Offset(w * 0.10f, h * 0.78f),
        end = Offset(w * 0.45f, h * 0.48f),
        strokeWidth = strokeW,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(w * 0.45f, h * 0.48f),
        end = Offset(w * 0.62f, h * 0.64f),
        strokeWidth = strokeW,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(w * 0.62f, h * 0.64f),
        end = Offset(w * 0.90f, h * 0.40f),
        strokeWidth = strokeW,
        cap = StrokeCap.Round
    )
}

@Composable
private fun NavIcon(type: NavIconType, tint: Color, size: Dp = 22.dp) {
    Canvas(modifier = Modifier.size(size)) {
        when (type) {
            NavIconType.HOME -> drawHomeIcon(tint)
            NavIconType.CLOCK -> drawClockIcon(tint)
            NavIconType.PHOTO -> drawPhotoIcon(tint)
            NavIconType.APPS -> drawAppsIcon(tint)
            NavIconType.TROPHY -> drawTrophyIcon(tint)
            NavIconType.SETTINGS -> drawSettingsIcon(tint)
        }
    }
}

/** Relógio simples: aro + ponteiros de hora/minuto (aba Jogados). */
private fun DrawScope.drawClockIcon(color: Color) {
    val w = size.width
    val h = size.height
    val strokeW = w * 0.11f
    val center = Offset(w * 0.5f, h * 0.5f)
    val radius = w * 0.38f
    drawCircle(
        color = color,
        radius = radius,
        center = center,
        style = Stroke(width = strokeW, cap = StrokeCap.Round)
    )
    drawLine(
        color = color,
        start = center,
        end = Offset(center.x, center.y - radius * 0.55f),
        strokeWidth = strokeW,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = center,
        end = Offset(center.x + radius * 0.45f, center.y + radius * 0.15f),
        strokeWidth = strokeW,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawHomeIcon(color: Color) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)

    val roof = androidx.compose.ui.graphics.Path().apply {
        moveTo(w * 0.5f, h * 0.04f)
        lineTo(w * 0.95f, h * 0.46f)
        lineTo(w * 0.05f, h * 0.46f)
        close()
    }
    drawPath(roof, color = color, style = stroke)
    drawPath(roof, color = color, style = androidx.compose.ui.graphics.drawscope.Fill, alpha = 0.15f)

    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.22f, h * 0.44f),
        size = Size(w * 0.56f, h * 0.5f),
        cornerRadius = CornerRadius(w * 0.05f),
        style = stroke
    )
}

private fun DrawScope.drawAppsIcon(color: Color) {
    val cell = size.width * 0.38f
    val gap = size.width * 0.24f
    val corner = CornerRadius(size.width * 0.09f)

    listOf(
        Offset(0f, 0f),
        Offset(cell + gap, 0f),
        Offset(0f, cell + gap),
        Offset(cell + gap, cell + gap)
    ).forEach { topLeft ->
        drawRoundRect(
            color = color,
            topLeft = topLeft,
            size = Size(cell, cell),
            cornerRadius = corner
        )
    }
}

private fun DrawScope.drawTrophyIcon(color: Color) {
    val w = size.width
    val h = size.height
    val strokeW = w * 0.11f

    // Taça.
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.28f, h * 0.04f),
        size = Size(w * 0.44f, h * 0.42f),
        cornerRadius = CornerRadius(w * 0.08f),
        style = Stroke(width = strokeW, cap = StrokeCap.Round)
    )
    // Alças.
    drawArc(
        color = color,
        startAngle = 80f,
        sweepAngle = 195f,
        useCenter = false,
        topLeft = Offset(w * 0.04f, h * 0.10f),
        size = Size(w * 0.30f, h * 0.30f),
        style = Stroke(width = strokeW * 0.8f, cap = StrokeCap.Round)
    )
    drawArc(
        color = color,
        startAngle = -275f,
        sweepAngle = 195f,
        useCenter = false,
        topLeft = Offset(w * 0.66f, h * 0.10f),
        size = Size(w * 0.30f, h * 0.30f),
        style = Stroke(width = strokeW * 0.8f, cap = StrokeCap.Round)
    )
    // Haste e base.
    drawLine(
        color = color,
        start = Offset(w * 0.5f, h * 0.46f),
        end = Offset(w * 0.5f, h * 0.70f),
        strokeWidth = strokeW,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(w * 0.30f, h * 0.90f),
        end = Offset(w * 0.70f, h * 0.90f),
        strokeWidth = strokeW,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawSettingsIcon(color: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val outerRadius = size.minDimension * 0.28f
    val innerRadius = size.minDimension * 0.12f
    val teeth = 8
    val toothLength = size.minDimension * 0.14f
    val toothWidth = size.minDimension * 0.09f

    for (i in 0 until teeth) {
        val angle = (2 * Math.PI / teeth) * i
        val start = Offset(
            x = center.x + (outerRadius * cos(angle)).toFloat(),
            y = center.y + (outerRadius * sin(angle)).toFloat()
        )
        val end = Offset(
            x = center.x + ((outerRadius + toothLength) * cos(angle)).toFloat(),
            y = center.y + ((outerRadius + toothLength) * sin(angle)).toFloat()
        )
        drawLine(color = color, start = start, end = end, strokeWidth = toothWidth, cap = StrokeCap.Round)
    }

    drawCircle(color = color, radius = outerRadius, center = center, style = Stroke(width = size.minDimension * 0.09f))
    drawCircle(color = color, radius = innerRadius, center = center)
}
