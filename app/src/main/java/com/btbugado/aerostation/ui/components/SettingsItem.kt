package com.btbugado.aerostation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.btbugado.aerostation.ui.theme.AeroGlassBorder
import com.btbugado.aerostation.ui.theme.AeroGlassWhite
import com.btbugado.aerostation.ui.theme.AeroGlassWhiteStrong
import com.btbugado.aerostation.ui.theme.AeroTextPrimary
import com.btbugado.aerostation.ui.theme.AeroTextSecondary

/**
 * Linha "de vidro" pra uma opção de configuração — título, subtítulo
 * opcional e um slot pra trailing (switch, texto do valor atual, seta, etc).
 *
 * Ainda não tem nenhuma tela usando isso de verdade; existe pra já deixar
 * a peça pronta assim que a primeira configuração for definida.
 */
@Composable
fun SettingsItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    gamepadAutoFocus: Boolean = false,
    onSecondary: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val touchOk = LocalTouchActionsEnabled.current
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { if (touchOk) onClick() }
        )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(AeroGlassWhiteStrong, AeroGlassWhite)))
            .border(1.dp, AeroGlassBorder, RoundedCornerShape(16.dp))
            .gamepadFocusable(
                autoFocus = gamepadAutoFocus,
                onConfirm = onClick,
                onSecondary = onSecondary,
                onBack = onBack
            )
            .then(clickableModifier)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = AeroTextPrimary)
            if (subtitle != null) {
                Text(text = subtitle, color = AeroTextSecondary, fontSize = 13.sp)
            }
        }
        trailing?.invoke()
    }
}
