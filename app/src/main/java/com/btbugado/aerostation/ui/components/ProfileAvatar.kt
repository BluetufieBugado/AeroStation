package com.btbugado.aerostation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Avatar do usuário no canto superior esquerdo.
 * Se não houver foto, mostra uma silhueta simples dentro do círculo.
 *
 * Toque troca a foto (galeria); segurar restaura a foto do
 * RetroAchievements (quando conectado) — no controle, X faz o mesmo.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileAvatar(
    imagePath: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    nextUp: FocusRequester? = null,
    nextDown: FocusRequester? = null,
    nextLeft: FocusRequester? = null,
    nextRight: FocusRequester? = null,
    focusRequester: FocusRequester? = null
) {
    val touchOk = LocalTouchActionsEnabled.current
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.82f))
            .gamepadFocusable(
                onConfirm = onClick,
                onSecondary = onLongClick,
                nextUp = nextUp,
                nextDown = nextDown,
                nextLeft = nextLeft,
                nextRight = nextRight,
                focusRequester = focusRequester
            )
            .combinedClickable(
                onClick = { if (touchOk) onClick() },
                onLongClick = { if (touchOk) onLongClick?.invoke() }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (imagePath != null) {
            AsyncImage(
                model = imagePath,
                contentDescription = "Foto de perfil",
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
            )
        } else {
            Canvas(modifier = Modifier.size(30.dp)) {
                val centerX = size.width / 2f
                drawCircle(
                    color = Color(0xFF7D9FB2),
                    radius = size.width * 0.22f,
                    center = androidx.compose.ui.geometry.Offset(centerX, size.height * 0.32f)
                )
                val path = Path().apply {
                    moveTo(size.width * 0.22f, size.height * 0.88f)
                    quadraticBezierTo(
                        centerX, size.height * 0.46f,
                        size.width * 0.78f, size.height * 0.88f
                    )
                    close()
                }
                drawPath(path, color = Color(0xFF7D9FB2))
            }
        }
    }
}
