package com.btbugado.aerostation.data

import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tempo de jogo por ROM/app, chaveado pela URI (mesma chave dos overrides).
 *
 * Como os jogos rodam em emuladores EXTERNOS, medimos a "sessão" como o
 * tempo entre o boot (app pausa) e a volta ao launcher (app resume).
 * Ressalva honesta: tempo parado com o emulador aberto também conta.
 *
 * A sessão pendente é gravada com commit() síncrono no boot: se o Android
 * matar nosso processo pra dar RAM ao emulador, a volta ainda fecha a
 * sessão corretamente em vez de perder tudo.
 */
data class PlayStats(
    val totalMillis: Long = 0L,
    val sessions: Int = 0,
    val lastPlayedEpoch: Long = 0L
)

/**
 * Uma sessão fechada (jogo + início + fim). A galeria usa pra atribuir
 * screenshots do sistema — que não têm nome de jogo no arquivo — ao jogo
 * que estava aberto quando foram tirados.
 */
data class PlaySession(
    val gameKey: String,
    val startEpoch: Long,
    val endEpoch: Long
)

object PlayTimeStore {
    private const val PREFS = "play_time"
    private const val KEY_STATS = "stats_json"
    private const val KEY_SESSIONS = "sessions_json"
    private const val KEY_PENDING_KEY = "pending_key"
    private const val KEY_PENDING_START = "pending_start"
    /** Histórico pra cruzamento com screenshots (últimas N sessões). */
    private const val MAX_SESSIONS = 50

    /** Teto por sessão: largou o emulador aberto a noite toda não vira 10h. */
    private const val MAX_SESSION_MILLIS = 12L * 60 * 60 * 1000

    fun loadAll(context: Context): Map<String, PlayStats> {
        val raw = prefs(context).getString(KEY_STATS, null) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            val result = mutableMapOf<String, PlayStats>()
            json.keys().forEach { key ->
                val entry = json.getJSONObject(key)
                result[key] = PlayStats(
                    totalMillis = entry.optLong("total", 0L),
                    sessions = entry.optInt("sessions", 0),
                    lastPlayedEpoch = entry.optLong("last", 0L)
                )
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * Marca o início da sessão. Chamado SÓ quando o emulador realmente abriu
     * (não quando falta configuração).
     */
    fun beginSession(context: Context, gameKey: String, nowEpoch: Long = System.currentTimeMillis()) {
        val prefs = prefs(context)
        // Sessão de outra chave ainda pendente (não deveria acontecer, já que
        // só dá pra abrir um jogo por vez voltando ao app): fecha sem perder.
        prefs.getString(KEY_PENDING_KEY, null)?.let { pending ->
            if (pending != gameKey) endSession(context, nowEpoch)
        }
        prefs.edit()
            .putString(KEY_PENDING_KEY, gameKey)
            .putLong(KEY_PENDING_START, nowEpoch)
            .commit()
    }

    /**
     * Fecha a sessão pendente na volta ao app. Devolve (chave, stats
     * atualizados) ou null se não havia nada pendente.
     */
    fun endSession(context: Context, nowEpoch: Long = System.currentTimeMillis()): Pair<String, PlayStats>? {
        val prefs = prefs(context)
        val key = prefs.getString(KEY_PENDING_KEY, null) ?: return null
        val start = prefs.getLong(KEY_PENDING_START, 0L)
        prefs.edit().remove(KEY_PENDING_KEY).remove(KEY_PENDING_START).apply()
        if (start <= 0L) return null
        val delta = (nowEpoch - start).coerceIn(0L, MAX_SESSION_MILLIS)
        if (delta <= 0L) return null
        val all = loadAll(context).toMutableMap()
        val prev = all[key] ?: PlayStats()
        val updated = prev.copy(
            totalMillis = prev.totalMillis + delta,
            sessions = prev.sessions + 1,
            lastPlayedEpoch = nowEpoch
        )
        all[key] = updated
        saveAll(context, all)
        appendSession(context, PlaySession(key, start, nowEpoch))
        return key to updated
    }

    /** Últimas sessões (mais nova por último). */
    fun loadSessions(context: Context): List<PlaySession> {
        val raw = prefs(context).getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            val array = org.json.JSONArray(raw)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                PlaySession(
                    gameKey = o.optString("k", ""),
                    startEpoch = o.optLong("s", 0L),
                    endEpoch = o.optLong("e", 0L)
                )
            }.filter { it.gameKey.isNotBlank() && it.endEpoch > it.startEpoch }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun appendSession(context: Context, session: PlaySession) {
        val updated = (loadSessions(context) + session).takeLast(MAX_SESSIONS)
        val array = org.json.JSONArray()
        updated.forEach {
            array.put(
                org.json.JSONObject()
                    .put("k", it.gameKey)
                    .put("s", it.startEpoch)
                    .put("e", it.endEpoch)
            )
        }
        prefs(context).edit().putString(KEY_SESSIONS, array.toString()).apply()
    }

    private fun saveAll(context: Context, all: Map<String, PlayStats>) {
        val json = JSONObject()
        all.forEach { (key, stats) ->
            if (stats.totalMillis <= 0L) return@forEach
            json.put(
                key,
                JSONObject()
                    .put("total", stats.totalMillis)
                    .put("sessions", stats.sessions)
                    .put("last", stats.lastPlayedEpoch)
            )
        }
        prefs(context).edit().putString(KEY_STATS, json.toString()).apply()
    }

    /** "2h35" / "45min" / "30s" — selo compacto pros cards. */
    fun formatShort(millis: Long): String {
        val seconds = millis / 1000
        if (seconds < 60) return "${seconds}s"
        val minutes = seconds / 60
        if (minutes < 60) return "${minutes}min"
        val hours = minutes / 60
        val rest = (minutes % 60).toString().padStart(2, '0')
        return "${hours}h$rest"
    }

    /** "hoje" / "ontem" / "há 5 dias" / "12/03/25" — última sessão. */
    fun formatLastPlayed(epochMillis: Long, nowEpoch: Long = System.currentTimeMillis()): String {
        if (epochMillis <= 0L) return "—"
        val days = (nowEpoch - epochMillis) / 86_400_000L
        return when {
            days < 1 -> "hoje"
            days < 2 -> "ontem"
            days < 30 -> "há $days dias"
            else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(epochMillis))
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
