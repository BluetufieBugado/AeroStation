package com.btbugado.aerostation.data

import android.content.Context

/**
 * Lembra se o controle virtual estava visível ou escondido (físico em uso).
 *
 * O estado só muda por input real: tecla do físico esconde, toque na tela
 * mostra. Ao reabrir o app, o pad volta como estava — esperando o mesmo
 * controle de antes.
 */
object VirtualPadVisibilityStore {
    private const val PREFS = "virtual_pad_visibility"
    private const val KEY_VISIBLE = "visible"

    /** Padrão true: primeira execução mostra o pad (mais descobrível). */
    fun load(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_VISIBLE, true)

    fun save(context: Context, visible: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VISIBLE, visible)
            .apply()
    }
}
