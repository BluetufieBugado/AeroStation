package com.btbugado.aerostation.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

/** Perfil resumido (topo da tela de conquistas). */
data class RaProfile(
    /** Nome como digitado (pra exibir se a conta ainda não carregou). */
    val username: String,
    /** Nome canônico do servidor (case exato — o UserPic depende dele). */
    val displayName: String,
    /** URL direta da foto (vem do servidor, sem adivinhação). */
    val picUrl: String?,
    val totalPoints: Int,
    val totalSoftcorePoints: Int,
    val rank: Int
)

/** Um jogo jogado, com progresso de conquistas. */
data class RaPlayedGame(
    val gameId: Int,
    val title: String,
    val consoleName: String,
    val imageUrl: String?,
    val numAchieved: Int,
    val numPossible: Int,
    val numAchievedHardcore: Int,
    val lastPlayed: String
)

sealed interface RaResult<out T> {
    data class Ok<T>(val value: T) : RaResult<T>
    /** Usuário/chave errados (HTTP 401). */
    data object InvalidCredentials : RaResult<Nothing>
    /** Sem internet, timeout ou resposta inesperada. */
    data object NetworkError : RaResult<Nothing>
}

/**
 * Cliente mínimo da API do RetroAchievements (sem dependência nova: usa
 * HttpURLConnection + org.json, que já vêm no Android).
 *
 * Doc: https://retroachievements.org/dokuwiki/doku.php?id=api
 */
object RetroAchievementsApi {

    private const val BASE = "https://retroachievements.org/API/"
    private const val TIMEOUT_MS = 8000
    private const val GAMES_COUNT = 50

    /**
     * Conta (nome canônico + foto) via API_GetUserProfile. O servidor devolve
     * o case exato do usuário e o caminho do UserPic — sem isso a foto caía
     * no avatar padrão (o servidor de mídia é case-sensitive).
     */
    suspend fun getUserAccount(username: String, apiKey: String): RaResult<Pair<String, String?>> =
        withContext(Dispatchers.IO) {
            val json = getJson(
                "API_GetUserProfile.php" +
                    "?z=${encode(username)}&y=${encode(apiKey)}&u=${encode(username)}"
            ) ?: return@withContext RaResult.NetworkError

            if (json.optBoolean("Success", true).not() || json.has("Code")) {
                return@withContext RaResult.InvalidCredentials
            }
            runCatching {
                val canonical = json.optString("User", username).ifBlank { username }
                RaResult.Ok(canonical to mediaUrl(json.optString("UserPic", "")))
            }.getOrDefault(RaResult.NetworkError)
        }

    suspend fun getProfile(
        username: String,
        apiKey: String,
        displayName: String,
        picUrl: String?
    ): RaResult<RaProfile> =
        withContext(Dispatchers.IO) {
            val json = getJson(
                "API_GetUserSummary.php" +
                    "?z=${encode(username)}&y=${encode(apiKey)}&u=${encode(username)}&g=1&a=1"
            ) ?: return@withContext RaResult.NetworkError

            // Erro de credencial vem como {"Success":false,...} ou HTTP 401.
            if (json.optBoolean("Success", true).not() || json.has("Code")) {
                return@withContext RaResult.InvalidCredentials
            }
            runCatching {
                RaResult.Ok(
                    RaProfile(
                        username = username,
                        displayName = displayName,
                        picUrl = picUrl,
                        totalPoints = json.optInt("TotalPoints", 0),
                        totalSoftcorePoints = json.optInt("TotalSoftcorePoints", 0),
                        rank = json.optInt("Rank", 0)
                    )
                )
            }.getOrDefault(RaResult.NetworkError)
        }

    suspend fun getRecentlyPlayed(username: String, apiKey: String): RaResult<List<RaPlayedGame>> =
        withContext(Dispatchers.IO) {
            val array = getJsonArray(
                "API_GetUserRecentlyPlayedGames.php" +
                    "?z=${encode(username)}&y=${encode(apiKey)}&u=${encode(username)}" +
                    "&c=$GAMES_COUNT&o=0"
            ) ?: return@withContext RaResult.NetworkError

            runCatching {
                val games = ArrayList<RaPlayedGame>(array.length())
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    games += RaPlayedGame(
                        gameId = o.optInt("GameID", 0),
                        title = o.optString("Title", "Jogo ${o.optInt("GameID", 0)}"),
                        consoleName = o.optString("ConsoleName", ""),
                        // A API já devolve "/Images/xxxx.png" com o prefixo
                        // (antes duplicávamos e dava 404 em tudo).
                        imageUrl = mediaUrl(o.optString("ImageIcon", "")),
                        numAchieved = o.optInt("NumAchieved", 0),
                        numPossible = o.optInt("NumPossibleAchievements", 0),
                        numAchievedHardcore = o.optInt("NumAchievedHardcore", 0),
                        lastPlayed = o.optString("LastPlayed", "").take(10)
                    )
                }
                RaResult.Ok(games.toList())
            }.getOrDefault(RaResult.NetworkError)
        }

    /**
     * Monta a URL de mídia (ícone de jogo, UserPic...). A API devolve o
     * caminho relativo (ex: "/Images/060249.png", "/UserPic/Nome.png").
     * Aceita os três formatos pra nunca gerar URL quebrada.
     */
    private fun mediaUrl(path: String): String? {
        val trimmed = path.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("http")) return trimmed
        val relative = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return "https://media.retroachievements.org$relative"
    }

    /** GET que espera um objeto JSON. Null = rede falhou; 401 = credencial. */
    private fun getJson(path: String): JSONObject? {
        val connection = openConnection(path) ?: return null
        return try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> JSONObject(connection.readBody())
                HttpURLConnection.HTTP_UNAUTHORIZED -> JSONObject(
                    "{\"Success\":false,\"Code\":\"invalid_credentials\"}"
                )
                else -> null
            }
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun getJsonArray(path: String): JSONArray? {
        val connection = openConnection(path) ?: return null
        return try {
            // A tela valida a credencial antes pelo perfil (que diferencia
            // 401 de lista vazia); aqui qualquer falha vira erro de rede.
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            JSONArray(connection.readBody())
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(path: String): HttpURLConnection? = try {
        val connection = URI(BASE + path).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection
    } catch (e: Exception) {
        null
    }

    private fun HttpURLConnection.readBody(): String =
        inputStream.bufferedReader().use { it.readText() }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}
