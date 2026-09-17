package com.btbugado.aerostation.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.security.DigestInputStream
import java.text.Normalizer

/**
 * Última conquista desbloqueada num jogo (best-effort, exibida no preview).
 *
 * O RetroAchievements identifica jogos por HASH (MD5), então o caminho
 * principal é: MD5 da ROM -> API_GetGameFromHash -> progresso
 * (API_GetGameInfoAndUserProgress) -> conquista com DateAwarded mais
 * recente. Fallback pra discos comprimidos (.chd/.cso/.gdi/.cdi, cujo MD5
 * do arquivo nunca bate): nome do nosso console -> ID do console no RA
 * (via API_GetConsoleIDs) -> candidatos na lista daquele console (match de
 * título em 3 níveis, com apelidos do [GameNameNormalizer]) -> progresso de
 * cada gêmeo regional, preferindo o que tem unlocks.
 *
 * Tudo com cache em arquivos (consoles 30d, lista de jogos 7d, hash pra
 * sempre, progresso 30min) pra não estourar a API a cada preview aberto.
 * Sem credencial, sem match ou sem unlock: null silencioso (a linha some,
 * sem erro).
 */
data class LastUnlock(
    val achievementTitle: String,
    val achievementDesc: String?,
    /** "01/05/24" (ou o cru se não der pra reformatar). */
    val dateText: String,
    /** Veio do DateAwardedHardcore (jogador hardcore). */
    val hardcore: Boolean = false
)

/**
 * Progresso num jogo resolvido. [best] null = jogo achado, mas zero
 * unlocks (mostra "Nenhuma ainda (0/54)" em vez de sumir — assim dá pra
 * distinguir "te achei, vai jogar" de "nem te reconheci").
 */
data class GameProgress(
    val awarded: Int,
    val possible: Int,
    val gameTitle: String,
    val best: LastUnlock?,
    /** Diagnóstico (candidatos, contagens, campos da API) — copiar no toque. */
    val debug: String = ""
)

object GameAchievements {
    private const val BASE = "https://retroachievements.org/API/"
    private const val TIMEOUT_MS = 8000
    /** Listas de jogos (PS1/PSP passam de 1MB): timeout folgado. */
    private const val LIST_TIMEOUT_MS = 25000
    private const val CACHE_DIR = "ra_cache"
    private const val CONSOLES_TTL = 30L * 86_400_000L
    private const val GAMES_TTL = 7L * 86_400_000L
    private const val PROGRESS_TTL = 30L * 60_000L

    suspend fun lastUnlock(
        context: Context,
        displayName: String,
        console: String,
        gameUri: Uri,
        extension: String
    ): GameProgress? = withContext(Dispatchers.IO) {
        val username = RetroAchievementsStore.loadUsername(context)
        val apiKey = RetroAchievementsStore.loadApiKey(context)
        if (username.isBlank() || apiKey.isBlank()) return@withContext null
        runCatching {
            val dbg = ArrayList<String>()
            dbg += "user=$username"
            // 1) Hash MD5: identificação OFICIAL do RA (é assim que o
            // RetroArch/emuladores resolvem o jogo — exato, sem adivinhação
            // de título). Na primeira vez pode demorar (ISO grande), depois
            // sai do cache pra sempre.
            hashForGame(context, gameUri, extension)?.let { md5 ->
                dbg += "hash=$md5"
                gameFromHash(username, apiKey, md5)?.let { game ->
                    dbg += "hashGame=${game.first}"
                    progressWithDebug(context, username, apiKey, game.first, game.second, dbg)
                        ?.let { return@withContext it }
                } ?: dbg.add("hashGame=NONE")
            } ?: dbg.add("hash=SKIPPED")
            // 2) Fallback por título (discos comprimidos .chd/.cso, .gdi/.cdi
            // e hashes fora do padrão, cujo MD5 do arquivo nunca bate).
            val consoleId = matchConsoleId(context, username, apiKey, console)
                ?: return@withContext null.also { dbg += "console=NONE" }
            dbg += "console=$consoleId"
            val candidates = matchGames(context, username, apiKey, consoleId, displayName)
            dbg += "cands=" + candidates.joinToString(",") { "${it.first}" }
            if (candidates.isEmpty()) return@withContext null
            // O RA separa regiões em IDs diferentes ("Sheep Raider" US vs
            // "Sheep, Dog 'n' Wolf" EU, dublagens PT-BR linkadas à parte…).
            // Checa os gêmeos e fica com o que TEM unlocks; senão, o primeiro.
            var first: GameProgress? = null
            for ((id, title) in candidates) {
                val progress = progressWithDebug(context, username, apiKey, id, title, dbg)
                    ?: continue
                if (first == null) first = progress
                if (progress.awarded > 0) return@withContext progress
            }
            first
        }.getOrNull()
    }

    /** fetchProgress + linhas de diagnóstico (contagens e campos da API). */
    private fun progressWithDebug(
        context: Context,
        username: String,
        apiKey: String,
        gameId: Int,
        gameTitle: String,
        dbg: MutableList<String>
    ): GameProgress? {
        val data = fetchProgress(context, username, apiKey, gameId, gameTitle)
            ?: return null.also { dbg += "p$gameId=FETCH_FAIL" }
        dbg += "p$gameId=${data.progress.awarded}/${data.progress.possible} keys=${data.sampleKeys}"
        return data.progress.copy(debug = (dbg + "p$gameId=${data.progress.awarded}/${data.progress.possible}").joinToString("\n"))
    }

    /** Progresso + amostra dos nomes de campo do primeiro achievement. */
    private data class ProgressData(val progress: GameProgress, val sampleKeys: String)

    // Hash MD5 da ROM.

    /**
     * Extensões cujo MD5 do arquivo NUNCA bate com o RA (containers
     * comprimidos/próprios: o RA hasheia o conteúdo descomprimido). Essas
     * pulam direto pro fallback por título.
     */
    private val unhashableExtensions = setOf("chd", "cso", "gdi", "cdi", "m3u", "zip", "7z")

    /** MD5 em hex com cache (validado pelo tamanho do arquivo). */
    private fun hashForGame(context: Context, gameUri: Uri, extension: String): String? {
        val ext = extension.lowercase()
        if (ext in unhashableExtensions) return null
        // .cue é texto: o hash oficial sai do .bin referenciado na mesma pasta.
        val target = if (ext == "cue") resolveCueBin(context, gameUri) ?: return null else gameUri
        val size = runCatching {
            DocumentFile.fromSingleUri(context, target)?.length() ?: -1L
        }.getOrDefault(-1L)
        val cache = cacheFile(context, "ra_hash_${target.toString().hashCode().toUInt().toString(16)}.json")
        if (cache.exists()) {
            runCatching {
                val json = JSONObject(cache.readText())
                val cachedSize = json.optLong("size", -1L)
                // Tamanho igual (ou ilegível agora) = ROM é a mesma, reusa.
                if (cachedSize < 0L || size < 0L || cachedSize == size) {
                    return json.optString("md5", "").ifBlank { null }
                }
            }
        }
        val md5 = computeMd5(context, target) ?: return null
        runCatching {
            cache.writeText(JSONObject().put("md5", md5).put("size", size).toString())
        }
        return md5
    }

    /** Lê a cue sheet (só o começo) e resolve o .bin irmão na mesma pasta. */
    private fun resolveCueBin(context: Context, cueUri: Uri): Uri? {
        val head = runCatching {
            context.contentResolver.openInputStream(cueUri)?.use { input ->
                val buf = ByteArray(65536)
                val n = input.read(buf)
                if (n <= 0) null else String(buf, 0, n, Charsets.UTF_8)
            }
        }.getOrNull() ?: return null
        val binName = Regex("""(?im)^\s*FILE\s+"([^"]+)"""").find(head)
            ?.groupValues?.getOrNull(1)?.trim().takeIf { !it.isNullOrEmpty() }
            ?: return null
        return runCatching {
            val cueDoc = DocumentFile.fromSingleUri(context, cueUri) ?: return null
            cueDoc.parentFile?.findFile(binName)?.uri
        }.getOrNull()
    }

    private fun computeMd5(context: Context, uri: Uri): String? {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            context.contentResolver.openInputStream(uri)?.use { input ->
                DigestInputStream(input, digest).use { dis ->
                    val buf = ByteArray(262144)
                    while (dis.read(buf) != -1) { /* só bombeia */ }
                }
            } ?: return null
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    /** Resolve o hash no RA. 404/sem ID = hash desconhecido (jogo sem suporte). */
    private fun gameFromHash(username: String, apiKey: String, md5: String): Pair<Int, String>? {
        val json = getObject(
            "API_GetGameFromHash.php?z=${encode(username)}&y=${encode(apiKey)}&h=$md5"
        ) ?: return null
        val id = json.optInt("ID", 0)
        if (id == 0) return null
        return id to json.optString("Title", "Jogo $id")
    }

    // Consoles RA.

    private fun matchConsoleId(
        context: Context,
        username: String,
        apiKey: String,
        console: String
    ): Int? {
        val (alternatives, exclusions) = consoleKeywords[console] ?: return null
        val array = cachedArray(
            context,
            "ra_consoles.json",
            CONSOLES_TTL
        ) {
            getArray(
                "API_GetConsoleIDs.php?z=${encode(username)}&y=${encode(apiKey)}"
            )
        } ?: return null
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val tokens = tokenize(o.optString("Name", ""))
            if (tokens.isEmpty()) continue
            if (exclusions.any { it in tokens }) continue
            if (alternatives.any { alt -> alt.all { it in tokens } }) {
                return o.optInt("ID", 0).takeIf { it != 0 }
            }
        }
        return null
    }

    /**
     * Nosso console -> alternativas de tokens obrigatórios + tokens
     * proibidos (ex: "Game Boy" não pode casar "Game Boy Color").
     */
    private val consoleKeywords: Map<String, Pair<List<List<String>>, List<String>>> = mapOf(
        "NES" to (listOf(listOf("nintendo", "entertainment"), listOf("famicom")) to listOf("disk")),
        "NES (Famicom Disk System)" to (listOf(listOf("famicom", "disk"), listOf("family", "disk")) to emptyList()),
        "Super Nintendo" to (listOf(listOf("super", "nintendo")) to emptyList()),
        "Nintendo 64" to (listOf(listOf("nintendo", "64")) to emptyList()),
        "Game Boy" to (listOf(listOf("game", "boy")) to listOf("color", "colour", "advance", "light")),
        "Game Boy Color" to (listOf(listOf("game", "boy", "color"), listOf("game", "boy", "colour")) to emptyList()),
        "Game Boy Advance" to (listOf(listOf("game", "boy", "advance")) to emptyList()),
        "Nintendo DS" to (listOf(listOf("nintendo", "ds")) to emptyList()),
        "Nintendo 3DS" to (listOf(listOf("3ds")) to emptyList()),
        "GameCube" to (listOf(listOf("gamecube")) to emptyList()),
        "Wii" to (listOf(listOf("wii")) to listOf("u")),
        "Wii U" to (listOf(listOf("wii", "u")) to emptyList()),
        "Master System" to (listOf(listOf("master", "system")) to emptyList()),
        "Game Gear" to (listOf(listOf("game", "gear")) to emptyList()),
        "Mega Drive" to (listOf(listOf("mega", "drive"), listOf("genesis")) to emptyList()),
        "32X" to (listOf(listOf("32x")) to emptyList()),
        "PlayStation" to (listOf(listOf("playstation")) to listOf("2", "3", "portable", "vita")),
        "PlayStation 2" to (listOf(listOf("playstation", "2")) to emptyList()),
        "PSP" to (listOf(listOf("playstation", "portable"), listOf("psp")) to emptyList()),
        "PS Vita" to (listOf(listOf("playstation", "vita"), listOf("vita")) to emptyList()),
        "Saturn" to (listOf(listOf("saturn")) to emptyList()),
        "Dreamcast" to (listOf(listOf("dreamcast")) to emptyList()),
        "PC Engine" to (listOf(listOf("pc", "engine"), listOf("turbografx")) to emptyList()),
        "Atari 2600" to (listOf(listOf("atari", "2600")) to emptyList()),
        "Atari 5200" to (listOf(listOf("atari", "5200")) to emptyList()),
        "Atari 7800" to (listOf(listOf("atari", "7800")) to emptyList()),
        "ColecoVision" to (listOf(listOf("colecovision")) to emptyList()),
        "Intellivision" to (listOf(listOf("intellivision")) to emptyList()),
        "WonderSwan" to (listOf(listOf("wonderswan")) to listOf("color", "colour")),
        "WonderSwan Color" to (listOf(listOf("wonderswan", "color"), listOf("wonderswan", "colour")) to emptyList()),
        "Neo Geo Pocket" to (listOf(listOf("neo", "geo", "pocket")) to listOf("color", "colour")),
        "Neo Geo Pocket Color" to (listOf(listOf("neo", "geo", "pocket", "color"), listOf("neo", "geo", "pocket", "colour")) to emptyList())
    )

    // Jogo RA (id + título) dentro do console.

    /**
     * Todos os (id, título RA) candidatos, em ordem de confiança (exato >
     * conjunto > contenção), sem repetir ID, no máximo 5. O chamador decide
     * (prefere o que tem unlocks — resolve os gêmeos regionais).
     */
    private fun matchGames(
        context: Context,
        username: String,
        apiKey: String,
        consoleId: Int,
        displayName: String
    ): List<Pair<Int, String>> {
        // f=1: só jogos COM conquistas (lista bem menor — PS1/PSP estouravam
        // o timeout de 8s). Cache v2 invalida o antigo (que vinha com f=0).
        val array = cachedArray(
            context,
            "ra_games_${consoleId}_v2.json",
            GAMES_TTL
        ) {
            getArray(
                "API_GetGameList.php?z=${encode(username)}&y=${encode(apiKey)}&i=$consoleId&h=0&f=1",
                LIST_TIMEOUT_MS
            )
        } ?: return emptyList()
        // Formas do nosso título (apelidos expandidos + limpo).
        val candidates = (
            listOf(displayName) +
                GameNameNormalizer.candidateBases(displayName)
            ).map { foldTitle(it) }.filter { it.isNotEmpty() }.toSet()
        if (candidates.isEmpty()) return emptyList()
        val found = ArrayList<Pair<Int, String>>(5)
        fun add(id: Int, title: String) {
            if (id != 0 && found.none { it.first == id } && found.size < 5) {
                found += id to title.ifBlank { displayName }
            }
        }
        fun entries(): Sequence<Triple<Int, String, Set<String>>> = sequence {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val title = o.optString("Title", "")
                if (title.isBlank()) continue
                yield(Triple(o.optInt("ID", 0), title, foldTitle(title).split(' ').toSet()))
            }
        }
        // 1) igualdade exata normalizada.
        for ((id, title, _) in entries()) {
            if (foldTitle(title) in candidates) add(id, title)
        }
        // 2) mesmo conjunto de palavras (ex: "The Legend of Zelda - A Link
        // to the Past" vs "Legend of Zelda, The - A Link to the Past").
        val candidateSets = candidates.map { it.split(' ').toSet() }
        for ((id, title, raSet) in entries()) {
            if (raSet.size >= 2 && candidateSets.any { it == raSet }) add(id, title)
        }
        // 3) contenção: nosso título contido no do RA ou vice-versa (ex:
        // "lego batman" em "lego batman the videogame"). Exige 2+ palavras
        // no lado menor pra "mario" não casar "super mario world".
        for ((id, title, raSet) in entries()) {
            if (raSet.size < 2) continue
            val hit = candidateSets.any { cand ->
                cand.size >= 2 && (cand.all { it in raSet } || raSet.all { it in cand })
            }
            if (hit) add(id, title)
        }
        return found
    }

    // Progresso -> último unlock.

    private fun fetchProgress(
        context: Context,
        username: String,
        apiKey: String,
        gameId: Int,
        gameTitle: String
    ): ProgressData? {
        val json = cachedObject(
            context,
            "ra_progress_${username.hashCode()}_$gameId.json",
            PROGRESS_TTL
        ) {
            getObject(
                "API_GetGameInfoAndUserProgress.php" +
                    "?z=${encode(username)}&y=${encode(apiKey)}" +
                    "&u=${encode(username)}&g=$gameId"
            )
        } ?: return null
        val achievements = json.optJSONObject("Achievements") ?: return null
        var bestTitle: String? = null
        var bestDesc: String? = null
        var bestDate = ""
        var bestHardcore = false
        var awarded = 0
        var possible = 0
        var sampleKeys = ""
        val keys = achievements.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val a = achievements.optJSONObject(key) ?: continue
            if (sampleKeys.isEmpty()) {
                sampleKeys = a.keys().asSequence().toList().joinToString(",")
            }
            possible++
            // O endpoint de progresso usa DateEarned/DateEarnedHardcore (os
            // nomes DateAwarded* aparecem em outros endpoints — ler só eles
            // zerava o placar de todo mundo). Mantidos como fallback.
            // Quem joga de hardcore ganha o *Hardcore, e o softcore vem
            // vazio: vale o mais recente dos dois.
            val soft = a.optString("DateEarned", "").ifBlank { a.optString("DateAwarded", "") }
            val hard = a.optString("DateEarnedHardcore", "").ifBlank { a.optString("DateAwardedHardcore", "") }
            val date = maxOf(soft, hard)
            if (date.isBlank()) continue
            awarded++
            if (date > bestDate) {
                bestDate = date
                bestTitle = a.optString("Title", "").ifBlank { null }
                bestDesc = a.optString("Description", "").ifBlank { null }
                bestHardcore = hard.isNotBlank() && hard >= soft
            }
        }
        if (possible <= 0) return null
        val best = bestTitle?.let {
            LastUnlock(
                achievementTitle = it,
                achievementDesc = bestDesc,
                dateText = prettyDate(bestDate),
                hardcore = bestHardcore
            )
        }
        return ProgressData(
            GameProgress(
                awarded = awarded,
                possible = possible,
                gameTitle = gameTitle,
                best = best
            ),
            sampleKeys
        )
    }

    private fun prettyDate(raw: String): String = runCatching {
        // "2024-05-01 13:22:10" -> "01/05/24".
        val date = raw.take(10).split('-')
        if (date.size == 3) "${date[2]}/${date[1]}/${date[0].takeLast(2)}" else raw.take(10)
    }.getOrDefault(raw.take(10))

    // Normalização e HTTP com cache em arquivos.

    private fun tokenize(name: String): Set<String> =
        name.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()

    private fun foldTitle(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun cacheFile(context: Context, name: String): File =
        File(context.filesDir, CACHE_DIR).apply { mkdirs() }.let { File(it, name) }

    private fun cachedArray(
        context: Context,
        name: String,
        ttlMillis: Long,
        fetch: () -> JSONArray?
    ): JSONArray? {
        val file = cacheFile(context, name)
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < ttlMillis) {
            runCatching { return JSONArray(file.readText()) }
        }
        val fresh = fetch() ?: return null
        runCatching { file.writeText(fresh.toString()) }
        return fresh
    }

    private fun cachedObject(
        context: Context,
        name: String,
        ttlMillis: Long,
        fetch: () -> JSONObject?
    ): JSONObject? {
        val file = cacheFile(context, name)
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < ttlMillis) {
            runCatching { return JSONObject(file.readText()) }
        }
        val fresh = fetch() ?: return null
        runCatching { file.writeText(fresh.toString()) }
        return fresh
    }

    private fun getArray(path: String, timeoutMs: Int = TIMEOUT_MS): JSONArray? {
        val connection = openConnection(path, timeoutMs) ?: return null
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) null
            else JSONArray(connection.readBody())
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun getObject(path: String, timeoutMs: Int = TIMEOUT_MS): JSONObject? {
        val connection = openConnection(path, timeoutMs) ?: return null
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) null
            else JSONObject(connection.readBody())
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(path: String, timeoutMs: Int = TIMEOUT_MS): HttpURLConnection? = try {
        val connection = URI(BASE + path).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.instanceFollowRedirects = true
        connection
    } catch (_: Exception) {
        null
    }

    private fun HttpURLConnection.readBody(): String =
        inputStream.bufferedReader().use { it.readText() }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}
