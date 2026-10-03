package com.btbugado.aerostation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Montado em composição (não mais val estático): dialogs e componentes M3
// acompanham as cores do tema ativo.
@Composable
fun RetroAeroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AeroLeafGreen,
            secondary = AeroSkyMid,
            background = AeroSkyTop,
            surface = AeroGlassWhite,
            onPrimary = AeroTextPrimary,
            onBackground = AeroTextPrimary,
            onSurface = AeroTextPrimary,
        ),
        typography = aeroTypography(),
        content = content
    )
}
