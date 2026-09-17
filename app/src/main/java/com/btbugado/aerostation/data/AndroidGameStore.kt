package com.btbugado.aerostation.data

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persiste os aplicativos que o usuário escolheu colocar na biblioteca principal. */
object AndroidGameStore {
    private const val PREFS = "android_games_prefs"
    private const val KEY = "entries"

    // Faz a Home reagir imediatamente mesmo permanecendo composta ao trocar de aba.
    var revision by mutableIntStateOf(0)
        private set

    fun load(context: Context): List<AndroidGame> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(AndroidGame(
                        packageName = o.getString("package"),
                        activityName = o.getString("activity"),
                        displayName = o.getString("name"),
                        iconUri = o.optString("icon", "").ifEmpty { null }
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, packageName: String, activityName: String, displayName: String): Boolean {
        val current = load(context)
        if (current.any { it.packageName == packageName }) return false

        val iconUri = copyIcon(context, packageName)
        val updated = current + AndroidGame(packageName, activityName, displayName, iconUri)
        save(context, updated)
        revision++
        return true
    }

    fun remove(context: Context, packageName: String) {
        val current = load(context)
        val updated = current.filterNot { it.packageName == packageName }
        if (updated.size == current.size) return
        save(context, updated)
        revision++
    }

    private fun save(context: Context, entries: List<AndroidGame>) {
        val array = JSONArray()
        entries.forEach { app ->
            array.put(JSONObject().apply {
                put("package", app.packageName)
                put("activity", app.activityName)
                put("name", app.displayName)
                app.iconUri?.let { put("icon", it) }
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).apply()
    }

    private fun copyIcon(context: Context, packageName: String): String? = runCatching {
        val pm = context.packageManager
        val drawable = pm.getApplicationIcon(packageName)
        val width = 512
        val height = 512
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        val dir = File(context.filesDir, "android_game_icons").apply { mkdirs() }
        val file = File(dir, "${packageName.replace(Regex("[^A-Za-z0-9_.-]"), "_")}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        file.toURI().toString()
    }.getOrNull()
}
