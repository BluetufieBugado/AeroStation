package com.btbugado.aerostation.data

import android.content.Context

/** Modo de entrada escolhido pelo usuário nas Configurações > Geral. */
enum class ControlMode {
    /** Só touch screen: sem cursor e sem controle virtual na tela. */
    TOUCH,

    /** Controle virtual na tela + cursor visível para navegar nos menus. */
    GAMEPAD
}

/** Guarda o modo de entrada (touch x controle virtual). */
object ControlModeStore {
    private const val PREFS = "control_mode"
    private const val KEY = "mode"

    fun load(context: Context): ControlMode {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        // Padrão: touch (celular é touch-first). O usuário ativa o modo
        // controle em Configurações > Geral quando quiser o pad virtual.
        // Controle físico continua mostrando o cursor independente do modo.
        return runCatching { if (raw != null) ControlMode.valueOf(raw) else ControlMode.TOUCH }
            .getOrDefault(ControlMode.TOUCH)
    }

    fun save(context: Context, mode: ControlMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, mode.name)
            .apply()
    }
}
