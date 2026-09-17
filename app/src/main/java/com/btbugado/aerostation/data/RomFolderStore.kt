package com.btbugado.aerostation.data

import android.content.Context
import android.net.Uri

/**
 * Lembra qual pasta de ROMs o usuário escolheu, pra não pedir de novo
 * toda vez que o app abre. Usa SharedPreferences por simplicidade —
 * é só uma URI, não precisa de nada mais robusto que isso.
 */
object RomFolderStore {

    private const val PREFS_NAME = "rom_folder_prefs"
    private const val KEY_TREE_URI = "tree_uri"

    fun save(context: Context, uri: Uri) {
        prefs(context).edit()
            .putString(KEY_TREE_URI, uri.toString())
            .apply()
    }

    fun load(context: Context): Uri? {
        val raw = prefs(context).getString(KEY_TREE_URI, null) ?: return null
        return Uri.parse(raw)
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_TREE_URI).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
