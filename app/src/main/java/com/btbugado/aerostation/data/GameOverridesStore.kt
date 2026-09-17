package com.btbugado.aerostation.data

import android.content.Context
import org.json.JSONObject

/**
 * Guarda as customizações por jogo (nome/capa), chaveadas pela URI (SAF)
 * do arquivo da ROM. Usa JSON dentro de SharedPreferences — não é o volume
 * de dados que justifique um banco de verdade.
 *
 * A chave (URI do documento) tende a ficar estável entre scans desde que
 * o usuário não mova os arquivos nem re-selecione uma pasta diferente.
 */
object GameOverridesStore {

    private const val PREFS_NAME = "game_overrides_prefs"
    private const val KEY_JSON = "overrides_json"

    fun load(context: Context): Map<String, GameOverride> {
        val raw = prefs(context).getString(KEY_JSON, null) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            val result = mutableMapOf<String, GameOverride>()
            json.keys().forEach { key ->
                val entry = json.getJSONObject(key)
                result[key] = GameOverride(
                    customName = entry.optString("name", "").ifEmpty { null },
                    artUri = entry.optString("art", "").ifEmpty { null },
                    artSource = runCatching { ArtSource.valueOf(entry.optString("source", "NONE")) }
                        .getOrDefault(ArtSource.NONE),
                    console = entry.optString("console", "").ifEmpty { null }
                )
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun save(context: Context, overrides: Map<String, GameOverride>) {
        val json = JSONObject()
        overrides.forEach { (key, override) ->
            if (override.customName == null && override.artUri == null && override.console == null) {
                return@forEach
            }
            val entry = JSONObject()
            override.customName?.let { entry.put("name", it) }
            override.artUri?.let { entry.put("art", it) }
            override.console?.let { entry.put("console", it) }
            entry.put("source", override.artSource.name)
            json.put(key, entry)
        }
        prefs(context).edit().putString(KEY_JSON, json.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
