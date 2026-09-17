package com.btbugado.aerostation.data

/**
 * Mapeia o nome do console (já usado em [RomExtensions]) pro nome do
 * repositório correspondente em github.com/libretro-thumbnails/.
 *
 * Confirmados por busca: Game Boy Advance e Nintendo DS (os dois testados
 * no scanner). Os demais seguem o mesmo padrão de nomenclatura observado
 * nesses dois e no repositório "índice" (libretro/libretro-thumbnails),
 * mas não foram checados um a um — se algum estiver errado, o pior caso
 * é a busca automática simplesmente não achar capa pra aquele console
 * (sem quebrar nada), e o usuário sempre pode customizar na mão.
 */
object CoverArtRepos {

    private val repoByConsole: Map<String, String> = mapOf(
        "NES" to "Nintendo_-_Nintendo_Entertainment_System",
        "NES (Famicom Disk System)" to "Nintendo_-_Family_Computer_Disk_System",
        "Super Nintendo" to "Nintendo_-_Super_Nintendo_Entertainment_System",
        "Game Boy" to "Nintendo_-_Game_Boy",
        "Game Boy Color" to "Nintendo_-_Game_Boy_Color",
        "Game Boy Advance" to "Nintendo_-_Game_Boy_Advance",
        "Nintendo DS" to "Nintendo_-_Nintendo_DS",
        "Nintendo 3DS" to "Nintendo_-_Nintendo_3DS",
        "Nintendo Switch" to "Nintendo_-_Switch",
        "Wii U" to "Nintendo_-_Wii_U",
        "GameCube" to "Nintendo_-_GameCube",
        "Wii" to "Nintendo_-_Wii",
        "Nintendo 64" to "Nintendo_-_Nintendo_64",
        "Master System" to "Sega_-_Master_System_-_Mark_III",
        "Game Gear" to "Sega_-_Game_Gear",
        "Mega Drive" to "Sega_-_Mega_Drive_-_Genesis",
        "32X" to "Sega_-_32X",
        "PlayStation" to "Sony_-_PlayStation",
        "PlayStation 2" to "Sony_-_PlayStation_2",
        "PlayStation 3" to "Sony_-_PlayStation_3",
        "PSP" to "Sony_-_PlayStation_Portable",
        "PS Vita" to "Sony_-_PlayStation_Vita",
        "Xbox" to "Microsoft_-_Xbox",
        "Xbox 360" to "Microsoft_-_Xbox_360",
        "Xbox One" to "Microsoft_-_Xbox_One",
        "Saturn" to "Sega_-_Saturn",
        "Dreamcast" to "Sega_-_Dreamcast",
        "PC Engine" to "NEC_-_PC_Engine_-_TurboGrafx_16",
        "Atari 2600" to "Atari_-_2600",
        "Atari 5200" to "Atari_-_5200",
        "Atari 7800" to "Atari_-_7800",
        "ColecoVision" to "Coleco_-_ColecoVision",
        "Intellivision" to "Mattel_-_Intellivision",
        "WonderSwan" to "Bandai_-_WonderSwan",
        "WonderSwan Color" to "Bandai_-_WonderSwan_Color",
        "Neo Geo Pocket" to "SNK_-_Neo_Geo_Pocket",
        "Neo Geo Pocket Color" to "SNK_-_Neo_Geo_Pocket_Color"
    )

    fun repoFor(console: String?): String? = console?.let { repoByConsole[it] }
}
