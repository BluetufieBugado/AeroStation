package com.btbugado.aerostation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AeroColorScheme = darkColorScheme(
    primary = AeroLeafGreen,
    secondary = AeroSkyMid,
    background = AeroSkyTop,
    surface = AeroGlassWhite,
    onPrimary = AeroTextPrimary,
    onBackground = AeroTextPrimary,
    onSurface = AeroTextPrimary,
)

@Composable
fun RetroAeroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AeroColorScheme,
        typography = AeroTypography,
        content = content
    )
}
