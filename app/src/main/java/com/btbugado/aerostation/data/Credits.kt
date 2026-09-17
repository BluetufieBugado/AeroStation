package com.btbugado.aerostation.data

/**
 * Créditos exibidos em Ajustes > Créditos.
 *
 * Pra adicionar outros: novo [CreditEntry] na seção certa, com artista,
 * obra e link da licença/fonte em [url].
 */
data class CreditEntry(
    val section: String,
    val name: String,
    val detail: String,
    val url: String? = null
)

object Credits {
    val entries: List<CreditEntry> = listOf(
        CreditEntry(
            section = "Música e sons",
            name = "Glass — A-duration Music",
            detail = "Música de fundo.",
            url = "https://www.youtube.com/watch?v=hds9-VXje4E&list=PLGx12MVtGIdNGVAP2GBoASoPQCXpMJ_eK&index=7"
        ),
        CreditEntry(
            section = "Música e sons",
            name = "frutiger aero chime one shot — simmys_recycle_bin",
            detail = "Efeito sonoro.",
            url = "https://freesound.org/people/simmys_recycle_bin/sounds/757406/"
        ),
        CreditEntry(
            section = "Dados e imagens",
            name = "RetroAchievements",
            detail = "Perfil, jogos jogados e progresso de conquistas (API).",
            url = "https://retroachievements.org"
        ),
        CreditEntry(
            section = "Dados e imagens",
            name = "libretro-thumbnails",
            detail = "Capas automáticas dos jogos (boxarts).",
            url = "https://github.com/libretro-thumbnails"
        ),
        CreditEntry(
            section = "Dados e imagens",
            name = "SteamGridDB",
            detail = "Busca manual de capas alternativas.",
            url = "https://www.steamgriddb.com"
        )
    )
}
