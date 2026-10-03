package com.btbugado.aerostation.data

import com.btbugado.aerostation.R

/**
 * Créditos exibidos em Ajustes > Créditos.
 *
 * Pra adicionar outros: novo [CreditEntry] na seção certa, com artista,
 * obra e link da licença/fonte em [url]. Seções e detalhes são IDs de
 * string (traduzidos); nome e url ficam como estão.
 */
data class CreditEntry(
    val sectionRes: Int,
    val name: String,
    val detailRes: Int,
    val url: String? = null
)

object Credits {
    fun entries(): List<CreditEntry> = listOf(
        CreditEntry(
            sectionRes = R.string.credits_section_music_sounds,
            name = "Glass — A-duration Music",
            detailRes = R.string.credits_detail_bg_music,
            url = "https://www.youtube.com/watch?v=hds9-VXje4E&list=PLGx12MVtGIdNGVAP2GBoASoPQCXpMJ_eK&index=7"
        ),
        CreditEntry(
            sectionRes = R.string.credits_section_music_sounds,
            name = "frutiger aero chime one shot — simmys_recycle_bin",
            detailRes = R.string.credits_detail_sound_effect,
            url = "https://freesound.org/people/simmys_recycle_bin/sounds/757406/"
        ),
        CreditEntry(
            sectionRes = R.string.credits_section_data_images,
            name = "RetroAchievements",
            detailRes = R.string.credits_detail_retroachievements,
            url = "https://retroachievements.org"
        ),
        CreditEntry(
            sectionRes = R.string.credits_section_data_images,
            name = "libretro-thumbnails",
            detailRes = R.string.credits_detail_auto_covers,
            url = "https://github.com/libretro-thumbnails"
        ),
        CreditEntry(
            sectionRes = R.string.credits_section_data_images,
            name = "SteamGridDB",
            detailRes = R.string.credits_detail_manual_covers,
            url = "https://www.steamgriddb.com"
        )
    )
}
