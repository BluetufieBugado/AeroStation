package com.btbugado.aerostation.data

import android.content.Context

/** Volumes do app (0..1). Música em looping + efeitos de navegação. */
object AudioSettingsStore {
    private const val PREFS = "audio"
    private const val KEY_MUSIC = "music_volume"
    private const val KEY_SFX = "sfx_volume"

    const val DEFAULT_MUSIC = 0.55f
    const val DEFAULT_SFX = 0.8f

    fun musicVolume(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_MUSIC, DEFAULT_MUSIC).coerceIn(0f, 1f)

    fun sfxVolume(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_SFX, DEFAULT_SFX).coerceIn(0f, 1f)

    fun setMusicVolume(context: Context, value: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_MUSIC, value.coerceIn(0f, 1f)).apply()
    }

    fun setSfxVolume(context: Context, value: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_SFX, value.coerceIn(0f, 1f)).apply()
    }
}
