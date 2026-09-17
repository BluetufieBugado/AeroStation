package com.btbugado.aerostation.data

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Abre a pesquisa do SteamGridDB no navegador externo. */
object SteamGridDbLauncher {
    private const val BASE_SEARCH_URL =
        "https://www.steamgriddb.com/search/grids/all/material/all?term="

    fun openSearch(context: Context, gameName: String): Boolean {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(BASE_SEARCH_URL + Uri.encode(gameName))
        )
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse { false }
    }
}
