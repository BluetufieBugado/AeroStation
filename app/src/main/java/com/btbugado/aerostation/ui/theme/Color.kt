package com.btbugado.aerostation.ui.theme

import androidx.compose.ui.graphics.Color
import com.btbugado.aerostation.data.ThemeEngine

// Paleta Frutiger Aero: céu/água, vidro, gloss branco.
//
// De propósito são GETTERS (não vals): cada leitura pega a cor do tema
// ativo no [ThemeEngine]. Como o ativo é um mutableStateOf, qualquer
// @Composable que leia essas cores recompõe sozinho na troca de tema —
// sem precisar migrar os ~100 pontos de uso espalhados pelo app.
val AeroSkyTop: Color get() = Color(ThemeEngine.active.colors.skyTop)
val AeroSkyMid: Color get() = Color(ThemeEngine.active.colors.skyMid)
val AeroSkyBottom: Color get() = Color(ThemeEngine.active.colors.skyBottom)
val AeroLeafGreen: Color get() = Color(ThemeEngine.active.colors.leafGreen)
val AeroGlassWhite: Color get() = Color(ThemeEngine.active.colors.glassWhite)
val AeroGlassWhiteStrong: Color get() = Color(ThemeEngine.active.colors.glassWhiteStrong)
val AeroGlassBorder: Color get() = Color(ThemeEngine.active.colors.glassBorder)
val AeroHighlight: Color get() = Color(ThemeEngine.active.colors.highlight)
val AeroTextPrimary: Color get() = Color(ThemeEngine.active.colors.textPrimary)
val AeroTextSecondary: Color get() = Color(ThemeEngine.active.colors.textSecondary)
val AeroAccentOrange: Color get() = Color(ThemeEngine.active.colors.accentOrange)
