package com.btbugado.aerostation.data

import android.content.Context

/** Guarda qual aplicativo deve abrir cada console. */
object EmulatorConfigStore {
    private const val PREFS = "emulator_config"

    /**
     * Chave antiga da época em que PlayStation/Saturn/Dreamcast dividiam uma
     * única linha ("PlayStation / Saturn / Dreamcast"). Quem configurou antes
     * da separação não perde a escolha: ela vale como fallback pros três.
     */
    private const val LEGACY_DISC_GROUP = "PlayStation / Saturn / Dreamcast"
    private val legacyFallbackConsoles = setOf("PlayStation", "Saturn", "Dreamcast")

    fun getPackage(context: Context, console: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("package:$console", null)

    fun getActivity(context: Context, console: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("activity:$console", null)

    /** [getPackage] com fallback pra config da categoria agrupada antiga. */
    fun getPackageCompat(context: Context, console: String): String? =
        getPackage(context, console)
            ?: if (console in legacyFallbackConsoles) getPackage(context, LEGACY_DISC_GROUP) else null

    /** [getActivity] com o mesmo fallback. */
    fun getActivityCompat(context: Context, console: String): String? =
        getActivity(context, console)
            ?: if (console in legacyFallbackConsoles) getActivity(context, LEGACY_DISC_GROUP) else null

    fun set(context: Context, console: String, packageName: String, activityName: String? = null) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("package:$console", packageName)
            .apply {
                if (activityName == null) remove("activity:$console")
                else putString("activity:$console", activityName)
            }
            .apply()
    }

    fun clear(context: Context, console: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("package:$console")
            .remove("activity:$console")
            .apply()
    }
}
