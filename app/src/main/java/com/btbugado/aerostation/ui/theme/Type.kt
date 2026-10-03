package com.btbugado.aerostation.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Função (não val): a Typography de cima capturava AeroTextPrimary UMA vez
// no load da classe e nunca mudava de cor. Aqui ela é montada em composição,
// então acompanha o tema ativo.
@Composable
fun aeroTypography() = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        color = AeroTextPrimary
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        color = AeroTextPrimary
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = AeroTextSecondary
    )
)

// Mantido pro caso de algum uso legado fora de composição.
val AeroTypography = Typography()
