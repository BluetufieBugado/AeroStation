package com.btbugado.aerostation.data

import android.content.Context

/**
 * Barra de navegação inferior: auto-hide no scroll (padrão ligado).
 * O esconder/mostrar MANUAL por gesto é só estado de sessão (não persiste).
 */
object NavBarStore {
    private const val PREFS = "navbar"
    private const val KEY_AUTO_HIDE = "auto_hide"

    fun isAutoHideEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_HIDE, true)

    fun setAutoHideEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_HIDE, enabled).apply()
    }
}
