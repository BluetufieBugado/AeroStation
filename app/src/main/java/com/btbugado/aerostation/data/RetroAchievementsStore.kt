package com.btbugado.aerostation.data

import android.content.Context

/**
 * Guarda as credenciais do RetroAchievements (usuário + chave de API).
 *
 * A chave é gerada no site (retroachievements.org > Settings) e fica SÓ no
 * aparelho, usada pra buscar perfil e progresso de conquistas na nova aba.
 */
object RetroAchievementsStore {
    private const val PREFS = "retroachievements"
    private const val KEY_USERNAME = "username"
    private const val KEY_API_KEY = "api_key"

    fun loadUsername(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_USERNAME, "").orEmpty()

    fun loadApiKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_API_KEY, "").orEmpty()

    fun hasCredentials(context: Context): Boolean =
        loadUsername(context).isNotBlank() && loadApiKey(context).isNotBlank()

    fun save(context: Context, username: String, apiKey: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USERNAME, username.trim())
            .putString(KEY_API_KEY, apiKey.trim())
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_USERNAME)
            .remove(KEY_API_KEY)
            .apply()
    }
}
