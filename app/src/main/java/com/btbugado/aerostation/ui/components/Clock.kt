package com.btbugado.aerostation.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.btbugado.aerostation.ui.theme.AeroTextPrimary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Relógio simples (HH:mm) que fica no lugar onde antes tinha o título
 * "Minha Biblioteca". Atualiza a cada 30s — não precisa de mais que isso
 * pra um relógio sem segundos, e poupa recomposição à toa.
 */
@Composable
fun LiveClock(modifier: Modifier = Modifier) {
    var currentTime by remember { mutableStateOf(formatNow()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = formatNow()
            delay(30_000)
        }
    }

    Text(
        text = currentTime,
        style = MaterialTheme.typography.headlineMedium,
        color = AeroTextPrimary,
        modifier = modifier
    )
}

private fun formatNow(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
