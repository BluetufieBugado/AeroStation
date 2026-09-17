package com.btbugado.aerostation.data

/**
 * Extensões de ROM reconhecidas, mapeadas para o nome do console.
 *
 * Isso serve duas vezes: (1) pro scanner filtrar o que é ROM e o que não é,
 * e (2) mais pra frente, pra decidir automaticamente qual core do libretro
 * carregar quando o usuário tocar num jogo.
 *
 * Formatos "container" genéricos (.zip, .7z) ficam de fora do mapa —
 * eles são aceitos pelo scanner, mas sem console definido, já que o
 * conteúdo real só se sabe abrindo o arquivo.
 */
object RomExtensions {

    val consoleByExtension: Map<String, String> = mapOf(
        // Nintendo
        "nes" to "NES",
        "fds" to "NES (Famicom Disk System)",
        "sfc" to "Super Nintendo",
        "smc" to "Super Nintendo",
        "gb" to "Game Boy",
        "gbc" to "Game Boy Color",
        "gba" to "Game Boy Advance",
        "nds" to "Nintendo DS",
        "n64" to "Nintendo 64",
        "z64" to "Nintendo 64",
        "v64" to "Nintendo 64",
        "ndd" to "Nintendo 64",
        "u1" to "Nintendo 64",
        // Nintendo 3DS (formatos próprios, sem ambiguidade).
        "3ds" to "Nintendo 3DS",
        "3dsx" to "Nintendo 3DS",
        "cci" to "Nintendo 3DS",
        "cxi" to "Nintendo 3DS",
        // Nintendo Switch (nsp/xci próprios).
        "nsp" to "Nintendo Switch",
        "xci" to "Nintendo Switch",
        // Wii U (wud/wux/wua próprios).
        "wud" to "Wii U",
        "wux" to "Wii U",
        "wua" to "Wii U",
        // GameCube / Wii (gcm/wbfs/wad próprios; ciso/gcz/rvz servem aos
        // dois no Dolphin — vão pro GameCube, corrija com Definir plataforma).
        "gcm" to "GameCube",
        "wbfs" to "Wii",
        "wad" to "Wii",
        "ciso" to "GameCube",
        "gcz" to "GameCube",
        "rvz" to "Wii",

        // Sega
        "sms" to "Master System",
        "gg" to "Game Gear",
        "md" to "Mega Drive",
        "gen" to "Mega Drive",
        // .smd (Super Magic Drive) é um formato clássico de ROM de Genesis
        // que faltava — sem ele o jogo nem aparecia no scan.
        "smd" to "Mega Drive",
        "32x" to "32X",
        // Dreamcast tem formato próprio (.cdi DiscJuggler e .gdi GD-ROM):
        // sem ambiguidade, vão direto pro Dreamcast. O resto (.chd/.cue/
        // .bin/.iso) é ambíguo com PlayStation/Saturn e se resolve por
        // pasta/conteúdo/override.
        "cdi" to "Dreamcast",
        "gdi" to "Dreamcast",

        // Sony / Sega (formatos de disco — ambíguos por natureza: iso, bin,
        // cue e chd são usados por PlayStation, Saturn, Dreamcast, PS2, PSP
        // e Sega CD. O scanner fareja o CONTEÚDO do .iso (DiscImageSniffer)
        // e só usa este padrão no resto; pra corrigir um jogo, segure nele
        // > Definir plataforma (override por jogo).
        "iso" to "PlayStation",
        "bin" to "PlayStation",
        "cue" to "PlayStation",
        "chd" to "PlayStation",
        // PSP comprimido (formato próprio, sem ambiguidade).
        "cso" to "PSP",
        // PS Vita (vpk próprio).
        "vpk" to "PS Vita",
        // J2ME (só .jar pra não duplicar com o .jad descritor).
        "jar" to "J2ME",

        // NEC / Atari / outros clássicos
        "pce" to "PC Engine",
        "a26" to "Atari 2600",
        "a52" to "Atari 5200",
        "a78" to "Atari 7800",

        // Outros portáteis clássicos
        "col" to "ColecoVision",
        "int" to "Intellivision",
        "ws" to "WonderSwan",
        "wsc" to "WonderSwan Color",
        "ngp" to "Neo Geo Pocket",
        "ngc" to "Neo Geo Pocket Color"
    )

    /**
     * Um console por linha nas Configurações > Emuladores — de propósito NÃO
     * derivado do mapa de extensões acima, pra nenhum formato ambíguo gerar
     * categoria agrupada (ex: o antigo "PlayStation / Saturn / Dreamcast",
     * que nenhum emulador cobre sozinho).
     */
    val consoles: List<String> = listOf(
        "NES",
        "NES (Famicom Disk System)",
        "Super Nintendo",
        "Game Boy",
        "Game Boy Color",
        "Game Boy Advance",
        "Nintendo DS",
        "Nintendo 3DS",
        "Nintendo Switch",
        "Wii U",
        "GameCube",
        "Wii",
        "Nintendo 64",
        "Master System",
        "Game Gear",
        "Mega Drive",
        "32X",
        "PlayStation",
        "PlayStation 2",
        "PlayStation 3",
        "PSP",
        "PS Vita",
        "Saturn",
        "Dreamcast",
        "Xbox",
        "Xbox 360",
        "Xbox One",
        "J2ME",
        "PC Engine",
        "Atari 2600",
        "Atari 5200",
        "Atari 7800",
        "ColecoVision",
        "Intellivision",
        "WonderSwan",
        "WonderSwan Color",
        "Neo Geo Pocket",
        "Neo Geo Pocket Color"
    )

    /** Formatos container aceitos mesmo sem console conhecido de antemão. */
    val containerExtensions: Set<String> = setOf("zip", "7z")

    /** Todas as extensões que o scanner deve considerar como possível ROM. */
    val allAcceptedExtensions: Set<String> =
        consoleByExtension.keys + containerExtensions

    fun consoleFor(extension: String): String? =
        consoleByExtension[extension.lowercase()]

    /** Extensões cujo console NÃO dá pra saber só pelo formato. */
    private val ambiguousExtensions = setOf("iso", "bin", "cue", "chd")

    fun isAmbiguous(extension: String): Boolean =
        ambiguousExtensions.contains(extension.lowercase())

    /**
     * Palavras-chave de pasta -> console (ex: ROMs/PS2/jogo.chd). Comparação
     * exata após normalizar (minúsculas, separadores viram espaço): a palavra
     * "ps2" casa "PS2 Games", mas "meus psps" não casa nada (evita falso
     * positivo). Só usado pros formatos ambíguos acima.
     */
    private val consoleByFolderWord: Map<String, String> = mapOf(
        "ps1" to "PlayStation",
        "psx" to "PlayStation",
        "psone" to "PlayStation",
        "playstation" to "PlayStation",
        "ps2" to "PlayStation 2",
        "psx2" to "PlayStation 2",
        "psp" to "PSP",
        "saturn" to "Saturn",
        "dreamcast" to "Dreamcast",
        "dc" to "Dreamcast",
        "n64" to "Nintendo 64",
        "snes" to "Super Nintendo",
        "sfc" to "Super Nintendo",
        "nes" to "NES",
        "famicom" to "NES",
        "fc" to "NES",
        "fds" to "NES (Famicom Disk System)",
        "genesis" to "Mega Drive",
        "megadrive" to "Mega Drive",
        "md" to "Mega Drive",
        "sms" to "Master System",
        "gg" to "Game Gear",
        "32x" to "32X",
        "gba" to "Game Boy Advance",
        "gbc" to "Game Boy Color",
        "gb" to "Game Boy",
        "gameboy" to "Game Boy",
        "nds" to "Nintendo DS",
        "ds" to "Nintendo DS",
        "pce" to "PC Engine",
        "turbografx" to "PC Engine",
        "coleco" to "ColecoVision",
        "colecovision" to "ColecoVision",
        "intellivision" to "Intellivision",
        "wonderswan" to "WonderSwan",
        "ngp" to "Neo Geo Pocket",
        "ngpc" to "Neo Geo Pocket Color",
        "gc" to "GameCube",
        "gamecube" to "GameCube",
        "wii" to "Wii",
        "3ds" to "Nintendo 3DS",
        "switch" to "Nintendo Switch",
        "wiiu" to "Wii U",
        "ps3" to "PlayStation 3",
        "vita" to "PS Vita",
        "j2me" to "J2ME",
        "java" to "J2ME",
        "xbox" to "Xbox"
    )

    private val consoleByFolderPhrase: Map<String, String> = mapOf(
        "playstation 1" to "PlayStation",
        "playstation 2" to "PlayStation 2",
        "playstation portable" to "PSP",
        "sega saturn" to "Saturn",
        "sega dreamcast" to "Dreamcast",
        "nintendo 64" to "Nintendo 64",
        "super nintendo" to "Super Nintendo",
        "nintendo entertainment system" to "NES",
        "mega drive" to "Mega Drive",
        "sega genesis" to "Mega Drive",
        "master system" to "Master System",
        "sega master system" to "Master System",
        "game gear" to "Game Gear",
        "game boy advance" to "Game Boy Advance",
        "game boy color" to "Game Boy Color",
        "game boy" to "Game Boy",
        "nintendo ds" to "Nintendo DS",
        "pc engine" to "PC Engine",
        "turbografx 16" to "PC Engine",
        "atari 2600" to "Atari 2600",
        "atari 5200" to "Atari 5200",
        "atari 7800" to "Atari 7800",
        "wonderswan color" to "WonderSwan Color",
        "neo geo pocket color" to "Neo Geo Pocket Color",
        "neo geo pocket" to "Neo Geo Pocket",
        "family computer disk system" to "NES (Famicom Disk System)",
        "nintendo gamecube" to "GameCube",
        "nintendo 3ds" to "Nintendo 3DS",
        "nintendo switch" to "Nintendo Switch",
        "nintendo wii u" to "Wii U",
        "wii u" to "Wii U",
        "playstation 3" to "PlayStation 3",
        "ps vita" to "PS Vita",
        "playstation vita" to "PS Vita",
        "java me" to "J2ME",
        "xbox 360" to "Xbox 360",
        "xbox one" to "Xbox One"
    )

    /** Frases da maior pra menor: "Xbox 360 Games" casa "xbox 360" antes do "xbox" solto. */
    private val sortedPhrases: List<Pair<String, String>> =
        consoleByFolderPhrase.entries
            .sortedByDescending { it.key.length }
            .map { it.key to it.value }

    /**
     * Tenta deduzir o console pelo nome das pastas ancestrais (da mais
     * próxima pra mais distante). Frases por contains (maior primeiro),
     * depois palavras exatas. Null = nada reconhecido.
     */
    fun consoleForFolders(dirNames: List<String>): String? {
        for (raw in dirNames) {
            val normalized = raw.lowercase()
                .replace(Regex("[_\\-.]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (normalized.isEmpty()) continue
            for ((phrase, console) in sortedPhrases) {
                if (normalized.contains(phrase)) return console
            }
            for (word in normalized.split(' ')) {
                consoleByFolderWord[word]?.let { return it }
            }
        }
        return null
    }
}
