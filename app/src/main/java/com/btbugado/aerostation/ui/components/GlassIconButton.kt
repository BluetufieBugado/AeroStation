package com.btbugado.aerostation.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.btbugado.aerostation.ui.theme.AeroGlassBorder
import com.btbugado.aerostation.ui.theme.AeroGlassWhite
import com.btbugado.aerostation.ui.theme.AeroGlassWhiteStrong

/**
 * Botão circular "de vidro" — mesmo acabamento do GameCard (gradiente sutil +
 * borda translúcida), só que redondo, pra ações rápidas de canto de tela.
 *
 * Suporta toque normal e toque longo com ações diferentes, pra um mesmo
 * botão acumular mais de uma função sem precisar de mais elementos na tela
 * (ex: toque = escolher/refresh, segurar = trocar de pasta).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    size: Dp = 44.dp,
    gamepadAutoFocus: Boolean = false,
    nextUp: FocusRequester? = null,
    nextDown: FocusRequester? = null,
    nextLeft: FocusRequester? = null,
    nextRight: FocusRequester? = null,
    focusRequester: FocusRequester? = null,
    content: @Composable () -> Unit
) {
    val touchOk = LocalTouchActionsEnabled.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(AeroGlassWhiteStrong, AeroGlassWhite)))
            .border(1.dp, AeroGlassBorder, CircleShape)
            .gamepadFocusable(
                autoFocus = gamepadAutoFocus,
                onConfirm = onClick,
                onSecondary = onLongClick,
                nextUp = nextUp,
                nextDown = nextDown,
                nextLeft = nextLeft,
                nextRight = nextRight,
                focusRequester = focusRequester
            )
            .combinedClickable(
                onLongClick = { if (touchOk) onLongClick?.invoke() },
                onClick = { if (touchOk) onClick() }
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
