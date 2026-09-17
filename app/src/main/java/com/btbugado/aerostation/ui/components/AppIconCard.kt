package com.btbugado.aerostation.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.btbugado.aerostation.ui.theme.AeroGlassBorder
import com.btbugado.aerostation.ui.theme.AeroGlassWhite
import com.btbugado.aerostation.ui.theme.AeroGlassWhiteStrong
import com.btbugado.aerostation.ui.theme.AeroTextPrimary

/**
 * Ícone quadrado "de vidro" pra um app do Android instalado, mesmo
 * acabamento do GameCard — pra aba de Apps ficar visualmente consistente
 * com a biblioteca de jogos.
 */
@Composable
fun AppIconCard(
    title: String,
    icon: ImageBitmap,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    gamepadAutoFocus: Boolean = false,
    nextUp: FocusRequester? = null,
    nextDown: FocusRequester? = null,
    nextLeft: FocusRequester? = null,
    nextRight: FocusRequester? = null,
    focusRequester: FocusRequester? = null,
    onFocusGained: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "press-scale")
    val touchOk = LocalTouchActionsEnabled.current

    Column(
        modifier = modifier
            .aspectRatio(0.8f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(colors = listOf(AeroGlassWhiteStrong, AeroGlassWhite))
            )
            .border(1.dp, AeroGlassBorder, RoundedCornerShape(18.dp))
            .gamepadFocusable(
                autoFocus = gamepadAutoFocus,
                onConfirm = onClick,
                nextUp = nextUp,
                nextDown = nextDown,
                nextLeft = nextLeft,
                nextRight = nextRight,
                focusRequester = focusRequester,
                onFocusGained = onFocusGained
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { if (touchOk) onClick() }
            )
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 22.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = icon,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(0.72f)
            )
        }
        Text(
            text = title,
            color = AeroTextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
