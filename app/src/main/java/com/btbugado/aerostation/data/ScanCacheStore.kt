package com.btbugado.aerostation.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Cache em DISCO da lista de jogos escaneada (o [LauncherContentCache] é só
 * memória e morre a cada abertura do app).
 *
 * Por que existe: pasta de PC tem milhares de arquivos de apoio (.dll, .dat…)
 * e cada .iso passa pelo sniff — o rescan completo a cada abertura deixava a
 * lista carregando por muito tempo. Com o disco, a abertura mostra a última
 * lista na hora e um rescan silencioso atualiza em seguida se algo mudou.
 *
 * Arquivo único com a pasta dona junto: trocou de pasta, o cache cai sozinho
 * (sem acumular lixo). Refresh manual continua forçando rescan total.
 */
object ScanCacheStore {
    private const val FILE = "scan_cache.json"

    fun loadDisk(context: Context, folderUri: Uri): List<Game>? {
        return runCatching {
            val raw = File(context.filesDir, FILE).takeIf { it.isFile }?.readText()
                ?: return null
            val json = JSONObject(raw)
            if (json.optString("folder", "") != folderUri.toString()) return null
            val array = json.optJSONArray("games") ?: return null
            val out = ArrayList<Game>(array.length())
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val uri = o.optString("u", "")
                val name = o.optString("n", "")
                if (uri.isBlank() || name.isBlank()) continue
                out += Game(
                    name = name,
                    uri = Uri.parse(uri),
                    extension = o.optString("e", ""),
                    console = o.optString("c", "").ifEmpty { null },
                    sizeBytes = o.optLong("s", 0L)
                )
            }
            return out.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    fun saveDisk(context: Context, folderUri: Uri, games: List<Game>) {
        runCatching {
            val array = JSONArray()
            for (game in games) {
                array.put(
                    JSONObject()
                        .put("n", game.name)
                        .put("u", game.uri.toString())
                        .put("e", game.extension)
                        .put("c", game.console ?: "")
                        .put("s", game.sizeBytes)
                )
            }
            File(context.filesDir, FILE).writeText(
                JSONObject()
                    .put("v", 1)
                    .put("folder", folderUri.toString())
                    .put("games", array)
                    .toString()
            )
        }
    }
}
