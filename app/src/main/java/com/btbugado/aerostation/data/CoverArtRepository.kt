package com.btbugado.aerostation.data

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

object CoverArtRepository {

    private const val CONNECT_TIMEOUT_MS = 4000
    private const val READ_TIMEOUT_MS = 4000

    /**
     * Tenta achar uma capa automática pro jogo no libretro-thumbnails.
     * Retorna a URL (não baixa o arquivo — quem exibe usa Coil, que já
     * cuida de cache), ou null se não achar nada em nenhuma tentativa.
     */
    suspend fun findAutoCoverUrl(game: Game): String? = withContext(Dispatchers.IO) {
        val repo = CoverArtRepos.repoFor(game.console) ?: return@withContext null

        for (candidate in candidateNames(game.name)) {
            val url = buildBoxartUrl(repo, candidate) ?: continue
            if (urlExists(url)) return@withContext url
        }
        null
    }

    /**
     * Copia uma imagem escolhida na galeria pro armazenamento privado do app.
     * Necessário porque o acesso à URI do seletor de fotos do sistema não é
     * garantido além da sessão atual.
     */
    suspend fun copyCustomCover(context: Context, gameKey: String, sourceUri: Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
                // Preserva a extensão original (gif/webp/png): salvar tudo
                // como .jpg matava a animação e quebrava o sniff do Coil.
                val ext = coverExtension(context, sourceUri)
                val base = "${keyHash(gameKey)}_custom"
                // Limpa variantes antigas (ex: o .jpg da época sem animados).
                coversDir.listFiles { file -> file.name.startsWith(base + ".") }
                    ?.forEach { runCatching { it.delete() } }
                val destFile = File(coversDir, "$base.$ext")
                val input = context.contentResolver.openInputStream(sourceUri) ?: return@withContext null
                input.use { stream ->
                    destFile.outputStream().use { output -> stream.copyTo(output) }
                }
                destFile.toUri().toString()
            } catch (e: Exception) {
                null
            }
        }

    /** Extensão da imagem de origem (mime -> nome do arquivo -> jpg). */
    private fun coverExtension(context: Context, sourceUri: Uri): String {
        val fromMime = when (context.contentResolver.getType(sourceUri)?.lowercase()) {
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            else -> null
        }
        if (fromMime != null) return fromMime
        val fromName = runCatching {
            sourceUri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()
        }.getOrNull().takeIf { it in setOf("gif", "webp", "png", "jpg", "jpeg") }
        if (fromName != null) return if (fromName == "jpeg") "jpg" else fromName
        return "jpg"
    }

    /**
     * Variações de nome pra bater com a convenção No-Intro do
     * libretro-thumbnails. O [GameNameNormalizer] limpa tags ("PT BR",
     * "[!]") e expande apelidos ("SM64" -> "Super Mario 64"); aqui cada base
     * ganha as variantes de região. Limite de sondagens pra não estourar
     * a latência no celular (cada 404 também custa uma requisição).
     */
    private fun candidateNames(baseName: String): List<String> {
        val bases = GameNameNormalizer.candidateBases(baseName)
        val regions = listOf(
            "", " (USA)", " (World)", " (Europe)", " (Japan)",
            " (USA, Europe)", " (Japan, USA)"
        )
        val out = ArrayList<String>(14)
        for (base in bases) {
            if (base.contains('(')) {
                // Provável No-Intro pronta ("Jogo (USA)"): tenta só ela.
                out += sanitizeForLookup(base)
            } else {
                for (region in regions) out += sanitizeForLookup(base + region)
            }
            if (out.size >= MAX_CANDIDATES) break
        }
        return out.distinct().take(MAX_CANDIDATES)
    }

    private const val MAX_CANDIDATES = 16

    /** Mesma substituição de caracteres especiais usada nos nomes de arquivo do repositório. */
    private fun sanitizeForLookup(name: String): String =
        name.map { c -> if (c in "&*/:`<>?\\|") '_' else c }.joinToString("")

    private fun buildBoxartUrl(repo: String, gameName: String): String? = try {
        URI(
            "https",
            "raw.githubusercontent.com",
            "/libretro-thumbnails/$repo/master/Named_Boxarts/$gameName.png",
            null
        ).toASCIIString()
    } catch (e: Exception) {
        null
    }

    private fun urlExists(urlString: String): Boolean = try {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.requestMethod = "HEAD"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        val ok = connection.responseCode == HttpURLConnection.HTTP_OK
        connection.disconnect()
        ok
    } catch (e: Exception) {
        false
    }

    private fun keyHash(key: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}
