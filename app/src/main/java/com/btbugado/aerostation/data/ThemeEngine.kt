package com.btbugado.aerostation.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Tema ativo em memória, como estado do Compose: qualquer leitura de
 * [active] (ou dos getters de cor em `ui.theme`) dentro de um @Composable
 * assina a troca — mudar de tema recompõe o app inteiro sozinho.
 *
 * O áudio não é Compose, então quem trocar o tema chama
 * [AppAudio.applyTheme] logo depois (a UI faz isso num Dispatchers.IO).
 */
object ThemeEngine {
    var active by mutableStateOf(AppTheme.default())
        private set

    fun init(context: Context) {
        active = ThemeStore.loadActive(context)
    }

    /** Ativa e persiste. Não mexe no áudio — o chamador decide quando. */
    fun activate(context: Context, theme: AppTheme) {
        ThemeStore.setActive(context, theme.id)
        active = ThemeStore.resolve(context, theme)
    }

    /** Recarrega o tema ativo do disco (ex: após editar e salvar). */
    fun refresh(context: Context) {
        active = ThemeStore.loadActive(context)
    }
}
