package com.btbugado.aerostation.data

import java.text.Normalizer

/**
 * Entende nomes de ROM "sujos" e gera bases de busca pra capa automática.
 *
 * Ex: "SM64 PT BR" -> limpa tags ("PT BR") -> "SM64" -> apelido -> "Super Mario 64".
 * O [CoverArtRepository] tenta cada base (com e sem região) e fica com a
 * primeira que existir no libretro-thumbnails.
 *
 * Tudo aqui é case-insensitive e ignora acentos na COMPARAÇÃO, mas preserva
 * o nome original nos candidatos (o repositório às vezes tem "Pokémon", às
 * vezes "Pokemon" — geramos os dois).
 */
object GameNameNormalizer {

    /** Palavras de tag que nunca fazem parte do título (tradução, região, hack...). */
    private val tagWords = setOf(
        "pt", "br", "ptbr", "ptr", "brasil", "brazil", "portugues", "portuguese",
        "traducao", "traduzido", "trad", "translation", "translated",
        "en", "english", "us", "usa", "eur", "europe", "european", "pal", "ntsc",
        "jp", "jap", "japan", "japanese", "fr", "french", "francais",
        "de", "german", "deutsch", "es", "esp", "spanish", "espanol",
        "it", "italian", "italiano", "nl", "dutch", "kr", "korea", "cn", "china",
        "world", "asia", "australia", "canada",
        "hack", "hacked", "improvement", "improved", "color", "colorized", "colour",
        "dx", "deluxe", "uncut", "uncensored", "fixed", "patched", "patch",
        "proto", "prototype", "beta", "demo", "sample", "preview", "kiosk",
        "overdump", "baddump", "bad", "alt", "alternate", "version", "edition",
        "rev", "review", "classic", "mini", "collection", "compilation",
        "randomizer", "rando", "trainer", "cheat", "unlocked", "remake", "goty"
    )

    /** Grupos [...] / (...) / {...} cujo conteúdo indica tag (região, versão...). */
    private fun isTagGroup(inner: String): Boolean {
        val folded = fold(inner).trim()
        if (folded.isEmpty()) return true
        if (folded.length <= 2) return true // (U), (E), [J], v1...
        if (folded.all { it.isDigit() }) return true // (1), [2]
        if (Regex("""v\d+(\.\d+)*""").matches(folded)) return true
        if (Regex("""\d+\.\d+""").matches(folded)) return true
        if (folded.startsWith("rev")) return true
        val tokens = folded.split(Regex("""[\s,._\-+!/]+""")).filter { it.isNotEmpty() }
        if (tokens.isNotEmpty() && tokens.all { it in tagWords }) return true
        // Contenção curta colada tipo "EnJa": pega por substring. Em textos
        // longos isso daria falso positivo ("adventure" contém "en"), então
        // só vale pra grupos curtos.
        val joined = tokens.joinToString(" ")
        return joined.length <= 6 && tagWords.any { joined.contains(it) }
    }

    /**
     * Apelidos comuns (siglas e formas curtas) -> título base.
     * Chaves em minúsculo sem acento, com espaços simples.
     */
    private val aliases: Map<String, String> = mapOf(
        // Mario
        "sm64" to "Super Mario 64",
        "mario64" to "Super Mario 64",
        "mario 64" to "Super Mario 64",
        "sm64ds" to "Super Mario 64 DS",
        "smw" to "Super Mario World",
        "mario world" to "Super Mario World",
        "smb" to "Super Mario Bros.",
        "mario1" to "Super Mario Bros.",
        "smb2" to "Super Mario Bros. 2",
        "smb3" to "Super Mario Bros. 3",
        "mario3" to "Super Mario Bros. 3",
        "sml" to "Super Mario Land",
        "sml2" to "Super Mario Land 2 - 6 Golden Coins",
        "smk" to "Super Mario Kart",
        "sms" to "Super Mario Sunshine",
        "smg" to "Super Mario Galaxy",
        "smg2" to "Super Mario Galaxy 2",
        "mk64" to "Mario Kart 64",
        "mario kart 64" to "Mario Kart 64",
        "mkdd" to "Mario Kart - Double Dash!!",
        "mkw" to "Mario Kart Wii",
        "mk7" to "Mario Kart 7",
        "mk8" to "Mario Kart 8",
        "mariorpg" to "Super Mario RPG - Legend of the Seven Stars",
        "pm64" to "Paper Mario",
        // Zelda (No-Intro usa "Legend of Zelda, The - ...")
        "lttp" to "Legend of Zelda, The - A Link to the Past",
        "alttp" to "Legend of Zelda, The - A Link to the Past",
        "link to the past" to "Legend of Zelda, The - A Link to the Past",
        "a link to the past" to "Legend of Zelda, The - A Link to the Past",
        "zelda link to the past" to "Legend of Zelda, The - A Link to the Past",
        "oot" to "Legend of Zelda, The - Ocarina of Time",
        "zelda oot" to "Legend of Zelda, The - Ocarina of Time",
        "zelda ocarina of time" to "Legend of Zelda, The - Ocarina of Time",
        "zelda - ocarina of time" to "Legend of Zelda, The - Ocarina of Time",
        "zelda majoras mask" to "Legend of Zelda, The - Majora's Mask",
        "zelda - majoras mask" to "Legend of Zelda, The - Majora's Mask",
        "majoras mask" to "Legend of Zelda, The - Majora's Mask",
        "botw" to "Legend of Zelda, The - Breath of the Wild",
        "totk" to "Legend of Zelda, The - Tears of the Kingdom",
        "wind waker" to "Legend of Zelda, The - Wind Waker",
        "oos" to "Legend of Zelda, The - Oracle of Seasons",
        "ooa" to "Legend of Zelda, The - Oracle of Ages",
        // Donkey Kong / Metroid / F-Zero / Star Fox / Kirby
        "dkc" to "Donkey Kong Country",
        "dkc2" to "Donkey Kong Country 2 - Diddy's Kong Quest",
        "dkc3" to "Donkey Kong Country 3 - Dixie Kong's Double Trouble!",
        "dk64" to "Donkey Kong 64",
        "sm" to "Super Metroid",
        "mzm" to "Metroid Zero Mission",
        "fzero" to "F-Zero",
        "fzerox" to "F-Zero X",
        "f-zero x" to "F-Zero X",
        "sf64" to "Star Fox 64",
        "starfox64" to "Star Fox 64",
        "star fox 64" to "Star Fox 64",
        "katam" to "Kirby and the Amazing Mirror",
        "kirby 64" to "Kirby 64 - The Crystal Shards",
        // Pokémon (formas com e sem hífen)
        "pokemon red" to "Pokémon - Red Version",
        "pokemon - red" to "Pokémon - Red Version",
        "pokemon blue" to "Pokémon - Blue Version",
        "pokemon - blue" to "Pokémon - Blue Version",
        "pokemon yellow" to "Pokémon - Yellow Version",
        "pokemon - yellow" to "Pokémon - Yellow Version",
        "pokemon gold" to "Pokémon - Gold Version",
        "pokemon - gold" to "Pokémon - Gold Version",
        "pokemon silver" to "Pokémon - Silver Version",
        "pokemon - silver" to "Pokémon - Silver Version",
        "pokemon crystal" to "Pokémon - Crystal Version",
        "pokemon - crystal" to "Pokémon - Crystal Version",
        "pokemon ruby" to "Pokémon - Ruby Version",
        "pokemon sapphire" to "Pokémon - Sapphire Version",
        "pokemon emerald" to "Pokémon - Emerald Version",
        "pokemon firered" to "Pokémon - FireRed Version",
        "pokemon leafgreen" to "Pokémon - LeafGreen Version",
        "pokemon diamond" to "Pokémon - Diamond Version",
        "pokemon pearl" to "Pokémon - Pearl Version",
        "pokemon platinum" to "Pokémon - Platinum Version",
        // Final Fantasy (números -> romanos)
        "ff1" to "Final Fantasy",
        "ff2" to "Final Fantasy II",
        "ff3" to "Final Fantasy III",
        "ff4" to "Final Fantasy IV",
        "ff5" to "Final Fantasy V",
        "ff6" to "Final Fantasy VI",
        "ff7" to "Final Fantasy VII",
        "ff8" to "Final Fantasy VIII",
        "ff9" to "Final Fantasy IX",
        "fft" to "Final Fantasy Tactics",
        // Sony clássicos
        "ct" to "Chrono Trigger",
        "mgs" to "Metal Gear Solid",
        "mgs2" to "Metal Gear Solid 2 - Sons of Liberty",
        "mgs3" to "Metal Gear Solid 3 - Snake Eater",
        "re" to "Resident Evil",
        "re1" to "Resident Evil",
        "re2" to "Resident Evil 2",
        "re3" to "Resident Evil 3 - Nemesis",
        "thps" to "Tony Hawk's Pro Skater",
        "thps2" to "Tony Hawk's Pro Skater 2",
        "thps3" to "Tony Hawk's Pro Skater 3",
        "thps4" to "Tony Hawk's Pro Skater 4",
        "gt1" to "Gran Turismo",
        "gt2" to "Gran Turismo 2",
        "crash" to "Crash Bandicoot",
        "ctr" to "Crash Team Racing",
        "spyro" to "Spyro the Dragon",
        "sotn" to "Castlevania - Symphony of the Night",
        "tekken 3" to "Tekken 3",
        // N64 / raros
        "goldeneye" to "GoldenEye 007",
        "007" to "GoldenEye 007",
        "pd" to "Perfect Dark",
        "banjo" to "Banjo-Kazooie",
        "tooie" to "Banjo-Tooie",
        "conker" to "Conker's Bad Fur Day",
        "diddy" to "Diddy Kong Racing",
        "mother2" to "EarthBound",
        "mother 2" to "EarthBound",
        // Sega
        "sonic1" to "Sonic the Hedgehog",
        "sonic 1" to "Sonic the Hedgehog",
        "sonic2" to "Sonic the Hedgehog 2",
        "sonic 2" to "Sonic the Hedgehog 2",
        "sonic3" to "Sonic the Hedgehog 3",
        "sonic 3" to "Sonic the Hedgehog 3",
        "sk" to "Sonic & Knuckles",
        "sadv" to "Sonic Advance",
        "sadx" to "Sonic Adventure DX",
        "sa2" to "Sonic Adventure 2",
        "sor1" to "Streets of Rage",
        "sor2" to "Streets of Rage 2",
        "sor3" to "Streets of Rage 3",
        "gunstar" to "Gunstar Heroes",
        "skies" to "Skies of Arcadia"
    )

    /** Minúsculo, sem acento, separadores viram espaço, espaços colapsados. */
    private fun foldKey(name: String): String =
        fold(name).lowercase()
            .replace(Regex("""[_\-.]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun fold(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")

    /** Tira grupos-tag e palavras-tag, preservando o resto (com acentos/case). */
    fun clean(rawName: String): String {
        var s = rawName.replace(Regex("""[_\-.]+"""), " ")
        // Remove grupos [...] / (...) / {...} que são só tags.
        s = Regex("""\[[^\]]*\]|\([^)]*\)|\{[^}]*\}""").replace(s) { match ->
            val inner = match.value.drop(1).dropLast(1)
            if (isTagGroup(inner)) " " else " ${match.value} "
        }
        // Remove palavras-tag soltas e tokens de versão.
        val kept = s.split(Regex("""\s+""")).filter { token ->
            val t = token.lowercase().trim(',', '.', '!', '+', '\'')
            if (t.isEmpty()) return@filter false
            if (t in tagWords) return@filter false
            if (Regex("""v\d+(\.\d+)*""").matches(t)) return@filter false
            if (Regex("""\d+\.\d+""").matches(t)) return@filter false
            if (Regex("""rev[a-z0-9]*""").matches(t)) return@filter false
            true
        }
        return kept.joinToString(" ").replace(Regex("""\s+"""), " ").trim()
    }

    /**
     * Bases de busca ordenadas (melhor palpite primeiro):
     * nome cru com região (provável No-Intro) -> expansão do apelido ->
     * nome limpo -> gêmeos sem acento.
     */
    fun candidateBases(rawName: String): List<String> {
        val out = ArrayList<String>(6)
        val trimmed = rawName.trim()
        if (trimmed.contains('(') || trimmed.contains('[')) {
            out += trimmed.replace(Regex("""\s+"""), " ").trim()
        }
        val cleaned = clean(trimmed)
        if (cleaned.isNotEmpty()) {
            aliases[foldKey(cleaned)]?.let { expanded ->
                out += expanded
                val foldedExpanded = fold(expanded)
                if (!foldedExpanded.equals(expanded, ignoreCase = true)) out += foldedExpanded
            }
            out += cleaned
            val foldedCleaned = fold(cleaned)
            if (!foldedCleaned.equals(cleaned, ignoreCase = true)) out += foldedCleaned
        }
        return out.distinct().filter { it.isNotEmpty() }
    }
}
