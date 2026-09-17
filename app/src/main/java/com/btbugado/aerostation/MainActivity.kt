package com.btbugado.aerostation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.btbugado.aerostation.data.CrashReporter
import com.btbugado.aerostation.ui.RetroAeroApp
import com.btbugado.aerostation.ui.theme.RetroAeroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Primeiro de tudo: gravador de crash (antes do Compose existir).
        CrashReporter.install(this)
        enableEdgeToEdge()
        applyImmersiveMode()
        setContent {
            RetroAeroTheme {
                RetroAeroApp()
            }
        }
    }

    // O Android às vezes traz as barras de sistema de volta (depois de um
    // diálogo, troca de app e retorno, etc) — reaplica o modo imersivo
    // sempre que a janela ganha foco de novo.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            // Deixa a barra reaparecer com um "puxão" da borda (swipe),
            // em vez de qualquer toque acidental tirar do modo imersivo.
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
