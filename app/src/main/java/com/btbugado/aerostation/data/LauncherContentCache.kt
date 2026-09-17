package com.btbugado.aerostation.data

import android.content.Context
import android.net.Uri

/**
 * Cache em memória do conteúdo pesado do launcher.
 * Mantém os resultados entre trocas de aba e só é invalidado por uma ação
 * explícita de refresh ou quando a pasta de ROMs muda.
 */
object LauncherContentCache {
    private var gamesFolderUri: String? = null
    private var games: List<Game>? = null

    private var installedApps: List<LauncherApp>? = null

    data class LauncherApp(
        val label: String,
        val packageName: String,
        val icon: android.graphics.Bitmap
    )

    fun getGames(folderUri: Uri): List<Game>? =
        if (gamesFolderUri == folderUri.toString()) games else null

    fun putGames(folderUri: Uri, value: List<Game>) {
        gamesFolderUri = folderUri.toString()
        games = value
    }

    fun invalidateGames() {
        gamesFolderUri = null
        games = null
    }

    fun getApps(): List<LauncherApp>? = installedApps

    fun putApps(value: List<LauncherApp>) {
        installedApps = value
    }

    /**
     * Permite limpar tudo caso futuramente o launcher precise de um refresh
     * geral. Não é usado nas trocas normais de aba.
     */
    fun clearAll() {
        invalidateGames()
        installedApps = null
    }
}
