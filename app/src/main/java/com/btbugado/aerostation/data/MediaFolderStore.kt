package com.btbugado.aerostation.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pastas dedicadas da galeria (ex: Game Media da Samsung, pasta de um app
 * de gravação externo), além do MediaStore geral.
 *
 * Cada pasta tem um modo:
 * - matchNamesOnly=false ("Jogos"): mostra TUDO que for foto/vídeo.
 * - matchNamesOnly=true ("Mista"): mostra só arquivos cujo nome contém um
 *   jogo/app da biblioteca (ex: "Screenshot_..._Brawl Stars.jpg").
 */
data class MediaFolder(val uri: String, val matchNamesOnly: Boolean)

object MediaFolderStore {
    private const val PREFS = "media_folders"
    private const val KEY = "folders_json"

    fun load(context: Context): List<MediaFolder> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                MediaFolder(
                    uri = o.optString("uri", ""),
                    matchNamesOnly = o.optBoolean("mixed", false)
                )
            }.filter { it.uri.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(context: Context, uri: Uri, matchNamesOnly: Boolean) {
        // Leitura + escrita: sem escrita o apagar da galeria falha
        // (deleteDocument exige grant de escrita na árvore).
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        val updated = load(context)
            .filterNot { it.uri == uri.toString() } + MediaFolder(uri.toString(), matchNamesOnly)
        save(context, updated)
    }

    /**
     * Garante a escrita nas pastas já salvas (as adicionadas antes da
     * permissão de escrita só tinham leitura). O seletor de pasta concede
     * leitura+escrita na origem, então elevar depois funciona; best-effort.
     */
    fun ensureWriteAccess(context: Context) {
        for (folder in load(context)) {
            val uri = runCatching { Uri.parse(folder.uri) }.getOrNull() ?: continue
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
    }

    fun remove(context: Context, uri: String) {
        save(context, load(context).filterNot { it.uri == uri })
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    fun displayName(context: Context, uriString: String): String {
        runCatching {
            DocumentFile.fromTreeUri(context, Uri.parse(uriString))?.name
                ?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return uriString.substringAfterLast("%2F", uriString).substringAfterLast('/', uriString)
    }

    private fun save(context: Context, folders: List<MediaFolder>) {
        val array = JSONArray()
        folders.forEach {
            array.put(JSONObject().put("uri", it.uri).put("mixed", it.matchNamesOnly))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).apply()
    }
}
