package com.btbugado.aerostation.data

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import com.btbugado.aerostation.R
import java.util.Locale

/**
 * Idioma do app (Configurações > Idioma): segue o sistema ou força
 * português/inglês/espanhol. Aplicado via [wrap] no attachBaseContext da
 * Activity e da Application; trocar exige recreate() (a Activity
 * recriada relê todos os resources no idioma novo).
 */
object LanguageStore {
    const val SYSTEM = "system"

    /** Códigos na ordem do seletor. */
    val options = listOf(SYSTEM, "pt", "en", "es")

    private const val PREFS = "language_prefs"
    private const val KEY = "locale"

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, SYSTEM) ?: SYSTEM

    fun set(context: Context, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, code).apply()
    }

    /** Rótulo do seletor (traduzido via resources). */
    fun labelRes(code: String): Int = when (code) {
        "pt" -> R.string.lang_pt
        "en" -> R.string.lang_en
        "es" -> R.string.lang_es
        else -> R.string.lang_system
    }

    fun wrap(base: Context): Context {
        val code = get(base)
        if (code == SYSTEM) return base
        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /** Salva + recria a Activity pra aplicar o idioma na hora. */
    fun applyAndRecreate(activity: Activity, code: String) {
        set(activity, code)
        Locale.setDefault(if (code == SYSTEM) Locale.getDefault() else Locale(code))
        activity.recreate()
    }
}
