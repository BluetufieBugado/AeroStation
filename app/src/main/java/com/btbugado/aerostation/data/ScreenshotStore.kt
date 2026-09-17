package com.btbugado.aerostation.data

import android.content.Context
import org.json.JSONObject

/**
 * Capturas atribuídas a cada jogo (chave = uri da ROM, igual ao tempo de
 * jogo). A galeria resolve pelo horário (arquivo criado com o jogo aberto)
 * e salva aqui; a Home lê sem tocar no disco — as imagens carregam via Coil
 * com as permissões persistentes das pastas.
 */
object ScreenshotStore {
    private const val PREFS = "screenshots"
    private const val KEY = "map_json"

    /** Quantos prints por jogo no máximo (o verso roda entre eles). */
    private const val MAX_PER_GAME = 20

    fun loadMap(context: Context): Map<String, List<String>> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            val result = mutableMapOf<String, List<String>>()
            json.keys().forEach { key ->
                val array = json.optJSONArray(key) ?: return@forEach
                val uris = List(array.length()) { i -> array.optString(i, "") }
                    .filter { it.isNotBlank() }
                if (uris.isNotEmpty()) result[key] = uris
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveMap(context: Context, map: Map<String, List<String>>) {
        val json = JSONObject()
        map.forEach { (key, uris) ->
            val capped = uris.filter { it.isNotBlank() }.distinct().take(MAX_PER_GAME)
            if (capped.isNotEmpty()) {
                json.put(key, org.json.JSONArray(capped))
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, json.toString()).apply()
    }
}
