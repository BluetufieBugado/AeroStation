package com.btbugado.aerostation.data

import android.content.Context

/**
 * Como abrir um jogo da biblioteca: com o preview 3D + inserção (padrão)
 * ou direto no emulador, pra quem prefere sem cerimônia.
 */
object GameLaunchSettingsStore {
    private const val PREFS = "game_launch"
    private const val KEY_PREVIEW = "cartridge_preview"
    private const val KEY_FLIP = "tile_flip"
    private const val KEY_FLIP_SECONDS = "tile_flip_seconds"

    fun isPreviewEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PREVIEW, true)

    fun setPreviewEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PREVIEW, enabled).apply()
    }

    /** Tiles da Home viram sozinhos (frente/verso estilo Windows Phone). */
    fun isFlipEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_FLIP, true)

    fun setFlipEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_FLIP, enabled).apply()
    }

    /**
     * Segundos parado na frente antes de virar (2..10, padrão 4).
     * Só a pausa da frente é configurável; a dobra e o verso são fixos.
     */
    fun getFlipSeconds(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_FLIP_SECONDS, 4).coerceIn(2, 10)

    fun setFlipSeconds(context: Context, seconds: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_FLIP_SECONDS, seconds.coerceIn(2, 10)).apply()
    }
}
