package com.btbugado.aerostation.data

/**
 * Conhecimento específico dos emuladores que já queremos suportar bem.
 *
 * @param libretroCore nome do núcleo no RetroArch (ex:
 * "mupen64plus_next_libretro_android.so"). Null = app standalone; com núcleo,
 * o boot usa o RetroArch com esse core (receita do wiki do Daijisho).
 */
data class EmulatorTarget(
    val packageName: String,
    val activityName: String,
    val displayName: String,
    val libretroCore: String? = null
)

object EmulatorRegistry {
    private const val DUCKSTATION_PACKAGE = "com.github.stenzek.duckstation"

    private const val DOLPHIN_PACKAGE = "org.dolphinemu.dolphinemu"
    private const val DOLPHIN_MMJR2_PACKAGE = "org.dolphinemu.mmjr"
    private const val DOLPHIN_MMJR_PACKAGE = "org.mm.jr"

    private const val VITA3K_PACKAGE = "org.vita3k.emulator"
    private const val VITA3K_PLUS_PACKAGE = "org.vita3kplus.emulator"
    private const val VITA3K_EMULATOR_ACTIVITY = "org.vita3k.emulator.Emulator"

    // Eden tem 3 sabores do mesmo código (mesma activity): Standard,
    // Optimized (disfarçado de Genshin pra liberar otimizações de driver)
    // e Legacy (Snapdragon 865 e chipsets sem suporte no Standard).
    private const val EDEN_PACKAGE = "dev.eden.eden_emulator"
    private const val EDEN_OPTIMIZED_PACKAGE = "com.miHoYo.Yuanshen"
    private const val EDEN_LEGACY_PACKAGE = "dev.legacy.eden_emulator"
    private const val EDEN_EMULATION_ACTIVITY = "org.yuzu.yuzu_emu.activities.EmulationActivity"

    private const val SKYLINE_PACKAGE = "skyline.emu"
    private const val SKYLINE_EMULATION_ACTIVITY = "emu.skyline.EmulationActivity"

    private const val RETROARCH_PACKAGE = "com.retroarch"
    private const val RETROARCH_ACTIVITY = "com.retroarch.browser.retroactivity.RetroActivityFuture"

    /**
     * Qualquer variante do RetroArch (Play Store 32-bit, aarch64 64-bit,
     * ra32, builds debug ".debug" etc). O N64 SÓ funciona bem no 64-bit
     * (aarch64) — o Mupen64Plus-Next GLES3 nem existe no pacote 32-bit.
     */
    fun isRetroArchPackage(packageName: String): Boolean =
        packageName == RETROARCH_PACKAGE || packageName.startsWith("$RETROARCH_PACKAGE.")

    /** Entrada RetroArch pra um núcleo (id sem sufixo, ex: "mgba"). */
    private fun retroArch(coreId: String) = EmulatorTarget(
        packageName = RETROARCH_PACKAGE,
        activityName = RETROARCH_ACTIVITY,
        displayName = "RetroArch ($coreId)",
        libretroCore = "${coreId}_libretro_android.so"
    )

    private val targetsByConsole = mapOf(
        // MyBoy! cobre GB/GBC/GBA no mesmo pacote. LinkBoy idem, com
        // MainActivity + VIEW confirmados via dumpsys.
        "Game Boy" to listOf(
            EmulatorTarget("com.fastemulator.gba", "com.fastemulator.gba.EmulatorActivity", "My Boy!"),
            EmulatorTarget(
                "com.pixelrespawn.linkboy",
                "com.pixelrespawn.linkboy.MainActivity",
                "LinkBoy"
            ),
            retroArch("gambatte")
        ),
        "Game Boy Color" to listOf(
            EmulatorTarget("com.fastemulator.gba", "com.fastemulator.gba.EmulatorActivity", "My Boy!"),
            EmulatorTarget(
                "com.pixelrespawn.linkboy",
                "com.pixelrespawn.linkboy.MainActivity",
                "LinkBoy"
            ),
            retroArch("gambatte")
        ),
        "Game Boy Advance" to listOf(
            EmulatorTarget("com.fastemulator.gba", "com.fastemulator.gba.EmulatorActivity", "My Boy!"),
            EmulatorTarget(
                "com.pixelrespawn.linkboy",
                "com.pixelrespawn.linkboy.MainActivity",
                "LinkBoy"
            ),
            retroArch("mgba")
        ),
        // .emu do Broglia seguem o padrão Imagine (mesmo do Snes9x EX+
        // confirmado em issue do Daijisho).
        // Super8Plus: dumpsys mostra que NÃO há activity com filtro VIEW —
        // boot direto impossível; a entrada vale selo + sugestão, e o boot
        // cai na home com aviso (igual ARMSX2 não-exportado).
        "NES" to listOf(
            EmulatorTarget("com.explusalpha.nesemu", "com.imagine.BaseActivity", "NES.emu"),
            EmulatorTarget("com.super8bit.free", "", "Super8Plus"),
            retroArch("fceumm")
        ),
        "NES (Famicom Disk System)" to listOf(
            retroArch("fceumm")
        ),
        "Nintendo DS" to listOf(
            EmulatorTarget("me.magnum.melondualds", "me.magnum.melonds.ui.emulator.EmulatorActivity", "WatermelonDS"),
            EmulatorTarget("me.magnum.melondualds.nightly", "me.magnum.melonds.ui.emulator.EmulatorActivity", "WatermelonDS Nightly"),
            EmulatorTarget("me.magnum.melonds", "me.magnum.melonds.ui.emulator.EmulatorActivity", "melonDS"),
            EmulatorTarget("com.dsemu.drastic", "com.dsemu.drastic.DraSticActivity", "DraStic"),
            retroArch("melonds")
        ),
        // MainActivity é a LISTA de jogos (sem boot direto); o boot usa a
        // EmulationActivity via citraBootIntents (VIEW + GamePath), que tenta
        // as activities de cada fork (MMJ/oficial/Canary/Antutu) até uma abrir.
        "Nintendo 3DS" to listOf(
            EmulatorTarget("org.citra.emu", "org.citra.emu.ui.main.MainActivity", "Citra MMJ"),
            EmulatorTarget(
                "com.antutu.ABenchMark",
                "org.citra.emu.ui.main.MainActivity",
                "Citra MMJ (Antutu)"
            )
        ),
        "Nintendo Switch" to listOf(
            EmulatorTarget(
                EDEN_PACKAGE,
                EDEN_EMULATION_ACTIVITY,
                "Eden"
            ),
            EmulatorTarget(
                EDEN_OPTIMIZED_PACKAGE,
                EDEN_EMULATION_ACTIVITY,
                "Eden Optimized"
            ),
            EmulatorTarget(
                EDEN_LEGACY_PACKAGE,
                EDEN_EMULATION_ACTIVITY,
                "Eden Legacy"
            ),
            // Skyline Edge: boot via VIEW com a URI na EmulationActivity
            // (receita do ES-DE) — coberto pelo VIEW genérico do launcher,
            // sem intent própria.
            EmulatorTarget(
                SKYLINE_PACKAGE,
                SKYLINE_EMULATION_ACTIVITY,
                "Skyline Edge"
            )
        ),
        "Nintendo 64" to listOf(
            EmulatorTarget(
                "org.mupen64plusae.v3.fzurita",
                "paulscode.android.mupen64plusae.SplashActivity",
                "M64Plus FZ"
            ),
            // N64 no Android tem DOIS builds do Mupen64Plus-Next: GLES2
            // (mupen64plus_next) e GLES3 (mupen64plus_next_gles3). Em aparelho
            // moderno o GLES3 é o que presta — o GLES2 dá tela preta/gráfico
            // quebrado. O nome sem sufixo quebrava o boot (core inexistente
            // -> RetroArch abre num hang de tela preta sem erro). Ordem:
            // GLES3 primeiro, depois parallel_n64 e o GLES2 legado.
            retroArch("mupen64plus_next_gles3"),
            retroArch("parallel_n64"),
            retroArch("mupen64plus_next")
        ),
        "Super Nintendo" to listOf(
            EmulatorTarget("com.explusalpha.Snes9xPlus", "com.imagine.BaseActivity", "Snes9x EX+"),
            retroArch("snes9x")
        ),
        "Master System" to listOf(
            retroArch("genesis_plus_gx")
        ),
        "Game Gear" to listOf(
            retroArch("genesis_plus_gx")
        ),
        // MD.emu (Broglia, padrão Imagine igual ao NES.emu/Snes9x EX+)
        // como standalone primeiro — mesmo padrão dos outros consoles
        // Nintendo, que funcionavam justamente por não dependerem só do
        // RetroArch. O RetroArch (genesis_plus_gx) segue de fallback.
        "Mega Drive" to listOf(
            EmulatorTarget("com.explusalpha.megaemu", "com.imagine.BaseActivity", "MD.emu"),
            retroArch("genesis_plus_gx")
        ),
        "32X" to listOf(
            retroArch("picodrive")
        ),
        "PC Engine" to listOf(
            retroArch("mednafen_pce_fast")
        ),
        "Atari 2600" to listOf(
            retroArch("stella")
        ),
        // Yaba Sanshiro 2 (free e Pro): sem boot externo confiável (vide
        // issue do Daijisho — activity de jogo não exportada), então abrem
        // a home pro boot manual. Boot direto no Saturn é pelo core yabause
        // do RetroArch. Ordem: standalone primeiro (é o que o usuário de
        // Saturn costuma ter), RetroArch de fallback.
        "Saturn" to listOf(
            EmulatorTarget(
                "org.devmiyax.yabasanshioro2",
                "",
                "Yaba Sanshiro 2"
            ),
            EmulatorTarget(
                "org.devmiyax.yabasanshioro2.pro",
                "",
                "Yaba Sanshiro 2 Pro"
            ),
            retroArch("yabause")
        ),
        // Sem duplicar a chave: antes eram dois "Dreamcast" no mapOf e o
        // segundo (só Redream) apagava o primeiro (flycast via RetroArch).
        // Ordem: Redream (otimizado, preferido) > Flycast standalone
        // (clássico, leve) > RetroArch (flycast core, fallback).
        "Dreamcast" to listOf(
            EmulatorTarget("io.recompiled.redream", "io.recompiled.redream.MainActivity", "Redream"),
            EmulatorTarget(
                "com.flycast.emulator",
                "com.flycast.emulator.MainActivity",
                "Flycast"
            ),
            retroArch("flycast")
        ),
        // Dolphin oficial + forks MMJR2/MMJR (receita do ES-DE: boot via
        // extra "AutoStartFile", ver dolphinBootIntents). Ordem: oficial
        // primeiro pra não mudar o padrão de quem já usa; MMJR2 antes do
        // MMJR v1 (mais novo e mais compatível).
        "GameCube" to listOf(
            EmulatorTarget(
                DOLPHIN_PACKAGE,
                "org.dolphinemu.dolphinemu.ui.main.TvMainActivity",
                "Dolphin"
            ),
            EmulatorTarget(
                DOLPHIN_MMJR2_PACKAGE,
                "org.dolphinemu.dolphinemu.ui.main.MainActivity",
                "Dolphin MMJR2"
            ),
            EmulatorTarget(
                DOLPHIN_MMJR_PACKAGE,
                "org.dolphinemu.dolphinemu.ui.main.MainActivity",
                "Dolphin MMJR"
            )
        ),
        "Wii" to listOf(
            EmulatorTarget(
                DOLPHIN_PACKAGE,
                "org.dolphinemu.dolphinemu.ui.main.TvMainActivity",
                "Dolphin"
            ),
            EmulatorTarget(
                DOLPHIN_MMJR2_PACKAGE,
                "org.dolphinemu.dolphinemu.ui.main.MainActivity",
                "Dolphin MMJR2"
            ),
            EmulatorTarget(
                DOLPHIN_MMJR_PACKAGE,
                "org.dolphinemu.dolphinemu.ui.main.MainActivity",
                "Dolphin MMJR"
            )
        ),
        // Cemu Android (port não-oficial SSimco, experimental): boot via
        // ACTION_VIEW na EmulationActivity com a URI + mimeType (o filtro
        // exige scheme content + mime */* + path .wud/.wux/.wua/.wuhb/.iso/
        // .elf/.rpx). Debug instala lado a lado (applicationIdSuffix).
        "Wii U" to listOf(
            EmulatorTarget(
                "info.cemu.cemu",
                "info.cemu.cemu.emulation.EmulationActivity",
                "Cemu"
            ),
            EmulatorTarget(
                "info.cemu.cemu.debug",
                "info.cemu.cemu.emulation.EmulationActivity",
                "Cemu (Debug)"
            )
        ),
        // PC (jogos de Windows no Android): GameNative tem boot externo por
        // ID da loja (action app.gamenative.LAUNCH_GAME + deep link
        // gamenative://run, confirmados no fonte); Winlator (só a MainActivity
        // é exportada, as de jogo não) e GameHub (sem API pública) abrem a
        // home pro boot manual. Ordem: quem boota direto primeiro.
        "PC" to listOf(
            EmulatorTarget(
                "app.gamenative",
                "app.gamenative.MainActivity",
                "GameNative"
            ),
            EmulatorTarget(
                "app.gamenative.debug",
                "app.gamenative.MainActivity",
                "GameNative (Debug)"
            ),
            EmulatorTarget(
                "com.winlator",
                "com.winlator.MainActivity",
                "Winlator"
            ),
            EmulatorTarget(
                "com.xiaoji.egggame",
                "",
                "GameHub"
            )
        ),
        "PlayStation" to listOf(
            EmulatorTarget(
                DUCKSTATION_PACKAGE,
                "com.github.stenzek.duckstation.EmulationActivity",
                "DuckStation"
            ),
            retroArch("pcsx_rearmed")
        ),
        // NetherSX2 mantém o MESMO pacote/activity do AetherSX2 (é patch em
        // cima dele; os dois nem instalam juntos) — uma entrada cobre ambos,
        // igual o Daijisho faz. Boot via ACTION_VIEW + URI com grant.
        // ARMSX2 tem 3 pacotes: GitHub stable (com.armsx2), nightly
        // (com.armsx2.nightly, instala lado a lado) e Play
        // (come.nanodata.armsx2 — o "come." é real). O namespace das
        // activities é sempre com.armsx2 (.BootSplashActivity recebe o cold
        // start e repassa pro .Main; .MainActivity é alias pro .Main).
        // Boot via ACTION_VIEW com a URI como dado, SEM mimeType (o filtro
        // declara só scheme content/file) + grant de leitura. .cue não boota
        // (o core não tem cue parser) — resolve pra .bin irmão antes de enviar.
        "PlayStation 2" to listOf(
            EmulatorTarget(
                "xyz.aethersx2.android",
                "xyz.aethersx2.android.EmulationActivity",
                "AetherSX2 / NetherSX2"
            ),
            EmulatorTarget(
                "com.armsx2",
                "com.armsx2.MainActivity",
                "ARMSX2 (GitHub)"
            ),
            EmulatorTarget(
                "com.armsx2.nightly",
                "com.armsx2.MainActivity",
                "ARMSX2 Nightly"
            ),
            EmulatorTarget(
                "come.nanodata.armsx2",
                "com.armsx2.MainActivity",
                "ARMSX2 (Play)"
            )
        ),
        // J2ME Loader: boot via ACTION_VIEW com a URI do .jar direto na
        // MainActivity (filtro VIEW declarado no manifest pra
        // application/java-archive e path .jar/.jad/.kjx, schemes file e
        // content). Abre o diálogo de instalação/conversão; com o jogo já
        // instalado, o diálogo oferece o botão de executar.
        "J2ME" to listOf(
            EmulatorTarget(
                "ru.playsoftware.j2meloader",
                "ru.playsoftware.j2meloader.MainActivity",
                "J2ME Loader"
            )
        ),
        // PPSSPP documenta VIEW + PpssppActivity (doc oficial de integração).
        // No Gold a activity mantém o nome base (sem "gold").
        "PSP" to listOf(
            EmulatorTarget(
                "org.ppsspp.ppsspp",
                "org.ppsspp.ppsspp.PpssppActivity",
                "PPSSPP"
            ),
            EmulatorTarget(
                "org.ppsspp.ppssppgold",
                "org.ppsspp.ppsspp.PpssppActivity",
                "PPSSPP Gold"
            )
        ),
        // Vita3K original + Plus (mesmo esquema de boot por Title ID —
        // ver vita3kBootIntents). O boot é pelo jogo INSTALADO (extra
        // title_id), não pelo .vpk — ver VitaTitleId. Ordem: original
        // primeiro pra não mudar o padrão de quem já usa.
        "PS Vita" to listOf(
            EmulatorTarget(
                VITA3K_PACKAGE,
                VITA3K_EMULATOR_ACTIVITY,
                "Vita3K"
            ),
            EmulatorTarget(
                VITA3K_PLUS_PACKAGE,
                VITA3K_EMULATOR_ACTIVITY,
                "Vita3K Plus"
            )
        )
    )

    fun knownTargets(console: String): List<EmulatorTarget> = targetsByConsole[console].orEmpty()

    /**
     * Activities candidatas extras por pacote (tentadas em ordem, antes do
     * implícito). ARMSX2: .BootSplashActivity primeiro (é a LAUNCHER, recebe
     * o cold start e repassa data/extras pro .Main), depois o alias
     * .MainActivity e o .Main.
     */
    private val armsx2BootActivities = listOf(
        "com.armsx2.BootSplashActivity",
        "com.armsx2.MainActivity",
        "com.armsx2.Main"
    )

    private val extraViewActivities = mapOf(
        "com.armsx2" to armsx2BootActivities,
        "com.armsx2.nightly" to armsx2BootActivities,
        "come.nanodata.armsx2" to armsx2BootActivities,
        // Cemu só tem a EmulationActivity (a salva vai primeiro, sem extras).
        "info.cemu.cemu" to listOf(
            "info.cemu.cemu.emulation.EmulationActivity"
        ),
        "info.cemu.cemu.debug" to listOf(
            "info.cemu.cemu.emulation.EmulationActivity"
        ),
        // Flycast mudou o nome da activity entre versões: a Play Store
        // atual usa com.flycast.emulator.MainActivity, builds antigos usam
        // com.reicast.emulator.MainActivity / NativeGLActivity. A salva na
        // configuração vai primeiro, as demais completam.
        "com.flycast.emulator" to listOf(
            "com.flycast.emulator.MainActivity",
            "com.reicast.emulator.MainActivity",
            "com.reicast.emulator.NativeGLActivity"
        )
    )

    /** Activities explícitas a tentar, sem duplicar a já salva. */
    fun viewActivityCandidates(target: EmulatorTarget): List<String> {
        val candidates = ArrayList<String>(3)
        if (target.activityName.isNotBlank()) candidates += target.activityName
        extraViewActivities[target.packageName]
            ?.filterTo(candidates) { it !in candidates }
        return candidates
    }

    /** Qualquer pacote do ARMSX2 (GitHub stable, nightly ou Play). */
    fun isArmsx2Package(packageName: String): Boolean =
        packageName == "com.armsx2" ||
            packageName == "com.armsx2.nightly" ||
            packageName == "come.nanodata.armsx2" ||
            packageName.startsWith("com.armsx2.")

    /**
     * Intents de boot direto pro ARMSX2, uma por activity candidata + uma
     * implícita.
     *
     * Receita confirmada no fonte (MainActivityRuntime.handleExternalLaunchIntent,
     * BootSplashActivity): ACTION_VIEW com a URI como dado e SEM mimeType (o
     * filtro declara só scheme content/file — intent com tipo nunca casa no
     * implícito), + grant de leitura + clipData + EXTRA_STREAM de reserva
     * (o ARMSX2 também lê EXTRA_STREAM/clipData/extras path/game/rom/uri).
     * Task nova: sem isso, com o ARMSX2 já aberto o jogo novo pode cair na
     * instância velha. A URI já vem resolvida (.cue -> .bin irmão).
     */
    fun armsx2BootIntents(
        context: android.content.Context,
        game: Game,
        target: EmulatorTarget
    ): List<android.content.Intent> {
        val packageName = target.packageName
        val bootUri = resolveArmsx2BootUri(context, game.uri)
        val activities = ArrayList<String>(armsx2BootActivities.size + 1)
        // A salva na configuração primeiro (cobre renome futuro), as demais completam.
        if (target.activityName.isNotBlank()) activities += target.activityName
        armsx2BootActivities.filterTo(activities) { it !in activities }
        val intents = activities.map { activity ->
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setClassName(packageName, activity)
                data = bootUri
                putExtra(android.content.Intent.EXTRA_STREAM, bootUri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                clipData = android.content.ClipData.newRawUri("ROM", bootUri)
            }
        }
        return intents + android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setPackage(packageName)
            data = bootUri
            putExtra(android.content.Intent.EXTRA_STREAM, bootUri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = android.content.ClipData.newRawUri("ROM", bootUri)
        }
    }

    /**
     * URI de boot pro ARMSX2: .cue resolve pro .bin irmão na mesma pasta (o
     * core não tem cue parser e .cue nem está no whitelist dele — bootar .cue
     * falha sempre). Demais extensões passam direto. Falha = URI original.
     */
    fun resolveArmsx2BootUri(context: android.content.Context, uri: android.net.Uri): android.net.Uri {
        val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':').orEmpty()
        if (!name.endsWith(".cue", ignoreCase = true)) return uri
        val head = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(65536)
                val n = input.read(buf)
                if (n <= 0) null else String(buf, 0, n, Charsets.UTF_8)
            }
        }.getOrNull() ?: return uri
        val track = Regex("""(?im)^\s*FILE\s+(?:"([^"]+)"|(\S+))""").find(head)?.let { m ->
            val quoted = m.groupValues.getOrNull(1).orEmpty()
            val raw = if (quoted.isNotBlank()) quoted else m.groupValues.getOrNull(2).orEmpty()
            raw.trim().substringAfterLast('/').substringAfterLast('\\')
        }.orEmpty()
        if (track.isBlank()) return uri
        // 1) DocumentFile (cobre provedores com parent resolvível).
        runCatching {
            androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                ?.parentFile?.findFile(track)?.takeIf { it.isFile }?.uri
        }.getOrNull()?.let { return it }
        // 2) Irmão por manipulação de string (tree/document da externalstorage:
        // .../tree/<tree>/document/<path%2Fdoc>). Troca só o último segmento.
        runCatching {
            val raw = uri.toString()
            val encoded = android.net.Uri.encode(track)
            val sepEncoded = raw.lastIndexOf("%2F")
            val sepPlain = raw.lastIndexOf('/')
            val (cut, joiner) = if (sepEncoded > sepPlain) sepEncoded to "%2F" else sepPlain to "/"
            if (cut <= 0) return@runCatching null
            val sibling = android.net.Uri.parse(raw.substring(0, cut + joiner.length) + encoded)
            androidx.documentfile.provider.DocumentFile.fromSingleUri(context, sibling)
                ?.takeIf { it.isFile }?.uri ?: sibling
        }.getOrNull()?.let { return it }
        return uri
    }

    /**
     * Núcleo RetroArch da linha (console + pacote), se houver.
     *
     * Pra família RetroArch a comparação é por FAMÍLIA, não por pacote
     * exato: quem escolheu o app manualmente em Configurações > Emuladores
     * geralmente pega o `com.retroarch.aarch64` (64-bit, o certo pro N64),
     * que nunca seria igual ao `com.retroarch` da tabela — antes isso
     * zerava o LIBRETRO e o boot caía num VIEW sem core (tela preta).
     */
    fun coreFor(console: String, packageName: String): String? {
        if (isRetroArchPackage(packageName)) return primaryCoreFor(console)
        return knownTargets(console).firstOrNull {
            it.packageName == packageName && it.libretroCore != null
        }?.libretroCore
    }

    /** Todos os núcleos candidatos pro console, em ordem de preferência. */
    fun coreCandidatesFor(console: String): List<String> =
        knownTargets(console).mapNotNull { it.libretroCore }.distinct()

    /** Núcleo principal (o que o boot automático usa). */
    fun primaryCoreFor(console: String): String? = coreCandidatesFor(console).firstOrNull()

    /** Primeira variante RetroArch instalada (aarch64 64-bit primeiro). */
    fun installedRetroArchPackage(context: android.content.Context): String? =
        listOf("com.retroarch.aarch64", RETROARCH_PACKAGE, "com.retroarch.ra32")
            .firstOrNull { isInstalled(context, it) }
            // Fallback genérico: qualquer outro com.retroarch.* instalado.
            ?: runCatching {
                context.packageManager.getInstalledApplications(0)
                    .map { it.packageName }
                    .firstOrNull { isRetroArchPackage(it) }
            }.getOrNull()

    /**
     * Intent própria pra emuladores que NÃO abrem jogo via ACTION_VIEW.
     *
     * DuckStation: activity EmulationActivity + extra "bootPath" com a URI
     * do jogo (receita documentada pelos frontends Pegasus/Daijisho/Beacon;
     * o DuckStation aceita URI em bootPath e a permissão de leitura vai junto
     * via grant).
     * Eden (linhagem Yuzu, os 3 sabores): ação TECH_DISCOVERED + URI como
     * dado (receita do guia oficial de integração com ES-DE, confirmada no
     * manifest: EmulationActivity exportada com filtro TECH_DISCOVERED).
     * RetroArch: RetroActivityFuture + extras ROM (caminho real), LIBRETRO
     * (núcleo) e CONFIGFILE (receita do wiki do Daijisho). Sem caminho real
     * derivável, devolve null e cai no VIEW padrão.
     * Null = sem intent própria, usa o VIEW padrão.
     */
    fun customBootIntent(
        game: Game,
        target: EmulatorTarget,
        console: String
    ): android.content.Intent? {
        val packageName = target.packageName
        val isDuckStation = packageName == DUCKSTATION_PACKAGE ||
            packageName.startsWith("$DUCKSTATION_PACKAGE.")
        if (isDuckStation) {
            return android.content.Intent().apply {
                setClassName(packageName, "$packageName.EmulationActivity")
                putExtra("bootPath", game.uri.toString())
                putExtra("resumeState", false)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri("ROM", game.uri)
            }
        }

        val isEden = isEdenPackage(packageName)
        if (isEden) {
            return android.content.Intent("android.nfc.action.TECH_DISCOVERED").apply {
                setPackage(packageName)
                setDataAndType(game.uri, mimeForSwitch(game.extension))
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri("ROM", game.uri)
            }
        }

        // M64Plus FZ: path real em forma de file:// na SplashActivity. O path
        // puro (receita do DIG) abria o app mas parava na biblioteca; o
        // content-URI idem. Sem grants aqui (não é content://). Task limpa:
        // se o Mupen já estava aberto, o intent caía na instância velha que
        // ignora o dado — igual a receita do DIG manda.
        if (packageName == "org.mupen64plusae.v3.fzurita") {
            val path = realPathFromTreeUri(game.uri) ?: return null
            return android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setClassName(packageName, "paulscode.android.mupen64plusae.SplashActivity")
                setDataAndType(android.net.Uri.parse("file://$path"), "application/octet-stream")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }

        // RetroArch + núcleo (qualquer variante com.retroarch*): ROM por
        // caminho real — o RetroArch resolve o núcleo pelo nome do arquivo
        // (receita do wiki do Daijisho: ROM + LIBRETRO + CONFIGFILE).
        // Sem LIBRETRO válido o RetroArch abre num hang de tela preta sem
        // erro (issue libretro/RetroArch#19357) — por isso nunca mandamos
        // intent sem core: null aqui deixa o launcher cair no fallback com
        // aviso em vez de tela preta silenciosa.
        if (isRetroArchPackage(packageName)) {
            val core = target.libretroCore?.takeIf { it.isNotBlank() }
                ?: coreFor(console, packageName) ?: return null
            val path = realPathFromTreeUri(game.uri) ?: return null
            return android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                setClassName(packageName, RETROARCH_ACTIVITY)
                putExtra("ROM", path)
                putExtra("LIBRETRO", core)
                putExtra(
                    "CONFIGFILE",
                    "/storage/emulated/0/Android/data/$packageName/files/retroarch.cfg"
                )
                // Receita do Daijisho (clear-task + clear-top): garante que
                // trocar de jogo com o RetroArch já aberto não caia na
                // instância velha que ignora o novo ROM. NEW_TASK é exigido
                // pro CLEAR_TASK valer.
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        return null
    }

    /**
     * Qualquer build do Citra (MMJ, oficial, Canary, PabloMK7…) ou o disfarce
     * Antutu do MMJ (com.antutu.ABenchMark, pra fugir do ban da Play Store —
     * mesma activity org.citra.emu.ui.EmulationActivity por dentro). Vale pra
     * escolha manual em Configurações > Emuladores, não só pros da tabela.
     */
    fun isCitraPackage(packageName: String): Boolean =
        "citra" in packageName.lowercase() || packageName == "com.antutu.ABenchMark"

    /**
     * Activities de emulação candidatas nos builds do Citra (cada fork muda
     * o pacote/activity: org.citra.emu.ui.EmulationActivity no MMJ,
     * org.citra.citra_emu.activities.EmulationActivity no oficial/Canary…).
     * O launcher tenta em ordem até uma abrir.
     */
    private val citraEmulationActivities = listOf(
        "org.citra.emu.ui.EmulationActivity",
        "org.citra.citra_emu.activities.EmulationActivity",
        "org.citra.emu.activities.EmulationActivity"
    )

    /**
     * Intents de boot direto pro Citra, um por activity candidata.
     *
     * Receita dos frontends (Pegasus/Daijisho, confirmada no Android 15):
     * ACTION_VIEW na EmulationActivity com a URI como dado (content:// +
     * octet-stream, que é o intent-filter declarado no manifest) + extras de
     * caminho ("GamePath" do autoboot do MMJ e "SelectedGame" da tabela de
     * frontends do Retroid — caminho real quando derivável, senão a própria
     * URI; extra desconhecido é ignorado) + grants + task limpa (sem isso,
     * com o Citra já aberto o jogo novo cai na instância velha e nada
     * acontece).
     */
    fun citraBootIntents(game: Game, packageName: String): List<android.content.Intent> =
        citraEmulationActivities.map { activity ->
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setClassName(packageName, activity)
                setDataAndType(game.uri, "application/octet-stream")
                val path = realPathFromTreeUri(game.uri) ?: game.uri.toString()
                putExtra("GamePath", path)
                putExtra("SelectedGame", path)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                clipData = android.content.ClipData.newRawUri("ROM", game.uri)
            }
        }

    /**
     * Qualquer build do Dolphin (oficial, MMJR, MMJR2 e forks que mantêm
     * o id, como o MMJR2-VBI). Vale pra escolha manual em Configurações >
     * Emuladores, não só pros da tabela.
     */
    fun isDolphinPackage(packageName: String): Boolean =
        packageName == DOLPHIN_PACKAGE ||
            packageName.startsWith("$DOLPHIN_PACKAGE.") ||
            packageName == DOLPHIN_MMJR2_PACKAGE ||
            packageName == DOLPHIN_MMJR_PACKAGE

    /**
     * Activities candidatas do Dolphin oficial (a do leanback primeiro,
     * que é a receita testada do ES-DE; a normal de fallback).
     */
    private val dolphinOfficialActivities = listOf(
        "org.dolphinemu.dolphinemu.ui.main.TvMainActivity",
        "org.dolphinemu.dolphinemu.ui.main.MainActivity"
    )

    /** Activity do MMJR/MMJR2 (mesmo nome de classe do oficial). */
    private const val DOLPHIN_MMJ_ACTIVITY = "org.dolphinemu.dolphinemu.ui.main.MainActivity"

    /**
     * Intents de boot direto pra família Dolphin, um por activity
     * candidata × formato do jogo.
     *
     * Receita dos frontends (ES-DE, confirmada no Android 15): o Dolphin
     * NÃO abre jogo via ACTION_VIEW com a URI como dado — ele lê o extra
     * "AutoStartFile" e ignora o resto (VIEW genérico termina na lista de
     * jogos sem bootar nada). Oficial: ACTION_MAIN + categoria
     * LEANBACK_LAUNCHER na TvMainActivity; MMJR/MMJR2: ACTION_VIEW na
     * MainActivity, sem dado (igual ao ES-DE). O valor preferido é a URI
     * do jogo (content:// + grant, pra builds modernos com scoped
     * storage); o caminho real vai de segunda opção (pra builds antigos).
     * Task limpa: sem isso, com o Dolphin já aberto o jogo novo cai na
     * instância velha e nada acontece.
     */
    fun dolphinBootIntents(game: Game, target: EmulatorTarget): List<android.content.Intent> {
        val packageName = target.packageName
        val isOfficial = packageName == DOLPHIN_PACKAGE ||
            packageName.startsWith("$DOLPHIN_PACKAGE.")
        val baseActivities = if (isOfficial) dolphinOfficialActivities
        else listOf(DOLPHIN_MMJ_ACTIVITY)
        val activities = ArrayList<String>(baseActivities.size + 1)
        activities.addAll(baseActivities)
        if (target.activityName.isNotBlank() && target.activityName !in activities) {
            activities += target.activityName
        }
        // URI SAF primeiro (scoped storage), caminho real depois (legado).
        val autoStartValues = ArrayList<String>(2)
        autoStartValues += game.uri.toString()
        realPathFromTreeUri(game.uri)?.let { if (it !in autoStartValues) autoStartValues += it }

        return activities.flatMap { activity ->
            autoStartValues.map { value ->
                if (isOfficial) {
                    android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                        setClassName(packageName, activity)
                        addCategory(android.content.Intent.CATEGORY_LEANBACK_LAUNCHER)
                        putExtra("AutoStartFile", value)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        clipData = android.content.ClipData.newRawUri("ROM", game.uri)
                    }
                } else {
                    android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setClassName(packageName, activity)
                        putExtra("AutoStartFile", value)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        clipData = android.content.ClipData.newRawUri("ROM", game.uri)
                    }
                }
            }
        }
    }

    /**
     * Qualquer sabor do Eden (Standard, Optimized disfarçado de Genshin e
     * Legacy — mesmo código, mesma activity). Vale pra escolha manual em
     * Configurações > Emuladores, não só pros da tabela.
     */
    fun isEdenPackage(packageName: String): Boolean =
        packageName == EDEN_PACKAGE || packageName.startsWith("$EDEN_PACKAGE.") ||
            packageName == EDEN_OPTIMIZED_PACKAGE || packageName.startsWith("$EDEN_OPTIMIZED_PACKAGE.") ||
            packageName == EDEN_LEGACY_PACKAGE || packageName.startsWith("$EDEN_LEGACY_PACKAGE.")

    /**
     * Qualquer build do Vita3K (original, Plus e forks que mantêm o id).
     * Na prática o boot vale pelo console (PS Vita) e não só pelo pacote:
     * quem escolheu outro app manualmente recebe a mesma receita, e o que
     * não honrar cai no fallback da home.
     */
    fun isVita3KPackage(packageName: String): Boolean =
        packageName == VITA3K_PACKAGE || packageName.startsWith("$VITA3K_PACKAGE.") ||
            packageName == VITA3K_PLUS_PACKAGE || packageName.startsWith("$VITA3K_PLUS_PACKAGE.")

    /**
     * Intents de boot direto pro Vita3K, uma por activity candidata.
     *
     * Receita do ES-DE + issue Vita3K-Android#505, confirmada no fonte
     * (Emulator.java, activity exportada): o Vita3K NÃO abre .vpk por URI —
     * ele boota o jogo já INSTALADO pelo Title ID. Manda os formatos que o
     * app entende (AppStartParameters pros builds antigos, title_id e
     * game_title pros novos — todos resolvem pro mesmo "-r TITLEID").
     * Single-top no topo: trocar de jogo com o Vita3K já aberto cai no
     * onNewIntent (relaunch) em vez da instância velha.
     */
    fun vita3kBootIntents(
        game: Game,
        target: EmulatorTarget,
        titleId: String
    ): List<android.content.Intent> {
        val activities = ArrayList<String>(2)
        // A salva primeiro (cobre o Plus/forks com activity renomeada), a
        // oficial completa.
        if (target.activityName.isNotBlank()) activities += target.activityName
        if (VITA3K_EMULATOR_ACTIVITY !in activities) activities += VITA3K_EMULATOR_ACTIVITY
        return activities.map { activity ->
            android.content.Intent().apply {
                setClassName(target.packageName, activity)
                putExtra("title_id", titleId)
                if (game.name.isNotBlank()) putExtra("game_title", game.name)
                putExtra("AppStartParameters", arrayOf("-r", titleId))
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }

    /**
     * Caminho real (/storage/...) a partir de URI de árvore do provedor
     * externalstorage. Null fora dele (outros provedores, DocumentProvider
     * próprio). Público pra o launcher diagnosticar em vez de mandar um
     * VIEW sem core que termina em tela preta silenciosa.
     */
    fun realPathFromTreeUri(uri: android.net.Uri): String? {
        if (uri.authority != "com.android.externalstorage.documents") return null
        val docId = uri.pathSegments.lastOrNull { it != "tree" && it != "document" }
            ?: return null
        val volume = docId.substringBefore(':')
        val path = docId.substringAfter(':', "")
        if (volume.isEmpty() || path.isEmpty()) return null
        val root = if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
        return "$root/$path"
    }

    private fun mimeForSwitch(extension: String): String = when (extension.lowercase()) {
        "nsp" -> "application/octet-stream"
        "xci" -> "application/octet-stream"
        else -> "*/*"
    }

    /** Qualquer pacote do GameNative (release ou debug lado a lado). */
    fun isGameNativePackage(packageName: String): Boolean =
        packageName == "app.gamenative" ||
            packageName == "app.gamenative.debug" ||
            packageName.startsWith("app.gamenative.")

    /** Lojas que o boot externo do GameNative aceita (enum GameSource dele). */
    val gameNativeSources = listOf("STEAM", "EPIC", "GOG", "AMAZON")

    /**
     * Intents de boot direto pro GameNative, pela action própria + deep link.
     *
     * Receita confirmada no fonte (IntentLaunchManager.parseLaunchIntent,
     * chamado na MainActivity inclusive no onNewIntent): action
     * "app.gamenative.LAUNCH_GAME" com extras "app_id" (int > 0) e
     * "game_source" (opcional, padrão STEAM), ou VIEW em
     * gamenative://run?appid=X&gamesource=Y. O boot é pelo jogo INSTALADO
     * nele (ID da loja), não pelo .exe — igual o Vita3K com Title ID.
     */
    fun gameNativeBootIntents(
        target: EmulatorTarget,
        appId: Int,
        source: String
    ): List<android.content.Intent> {
        val activity = target.activityName
            .takeIf { it.isNotBlank() } ?: "app.gamenative.MainActivity"
        val direct = android.content.Intent("app.gamenative.LAUNCH_GAME").apply {
            setClassName(target.packageName, activity)
            putExtra("app_id", appId)
            putExtra("game_source", source)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val deepLink = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setPackage(target.packageName)
            data = android.net.Uri.parse(
                "gamenative://run?appid=$appId&gamesource=$source"
            )
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return listOf(direct, deepLink)
    }

    /** Winlator/GameHub: sem API de boot externo — só abrem a home. */
    fun isPcHomeOnlyPackage(packageName: String): Boolean =
        packageName == "com.winlator" || packageName.startsWith("com.winlator.") ||
            packageName == "com.xiaoji.egggame" || packageName.startsWith("com.xiaoji.egggame.")

    /** Yaba Sanshiro 2 (free e Pro): idem, sem boot externo confiável. */
    fun isYabaPackage(packageName: String): Boolean =
        packageName == "org.devmiyax.yabasanshioro2" ||
            packageName == "org.devmiyax.yabasanshioro2.pro" ||
            packageName.startsWith("org.devmiyax.yabasanshioro2")

    /** Qualquer pacote do Cemu Android (release ou debug lado a lado). */
    fun isCemuPackage(packageName: String): Boolean =
        packageName == "info.cemu.cemu" ||
            packageName == "info.cemu.cemu.debug" ||
            packageName.startsWith("info.cemu.cemu.")

    /**
     * Intents de boot direto pro Cemu, uma por activity candidata + uma
     * implícita.
     *
     * Diferente do ARMSX2 (filtro só com scheme), o filtro do Cemu exige
     * scheme content + um mimeType qualquer + path do formato — por isso
     * TODAS as tentativas levam setDataAndType com tipo curinga, nunca só
     * data (data sozinha não casa nem no implícito). + grant de leitura +
     * clipData + task nova (com o Cemu já aberto o jogo novo cairia na
     * instância velha, que é singleTop mas pode ignorar sem as flags).
     */
    fun cemuBootIntents(
        game: Game,
        target: EmulatorTarget
    ): List<android.content.Intent> {
        val packageName = target.packageName
        val activities = ArrayList<String>(2)
        if (target.activityName.isNotBlank()) activities += target.activityName
        extraViewActivities[packageName]?.filterTo(activities) { it !in activities }
        if (activities.isEmpty()) activities += "info.cemu.cemu.emulation.EmulationActivity"
        val intents = activities.map { activity ->
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setClassName(packageName, activity)
                setDataAndType(game.uri, "*/*")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                clipData = android.content.ClipData.newRawUri("ROM", game.uri)
            }
        }
        return intents + android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setPackage(packageName)
            setDataAndType(game.uri, "*/*")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = android.content.ClipData.newRawUri("ROM", game.uri)
        }
    }

    fun defaultTarget(context: android.content.Context, console: String): EmulatorTarget? {
        val installedRetroArch = installedRetroArchPackage(context)
        for (target in knownTargets(console)) {
            if (isRetroArchPackage(target.packageName)) {
                // A tabela guarda com.retroarch, mas o instalado pode ser o
                // aarch64 (64-bit). Devolve o alvo com o pacote REAL pra
                // activity/CONFIGFILE apontarem pro lugar certo.
                if (installedRetroArch != null) return target.copy(packageName = installedRetroArch)
            } else if (isInstalled(context, target.packageName)) {
                return target
            }
        }
        return null
    }

    fun isInstalled(context: android.content.Context, packageName: String): Boolean =
        runCatching { context.packageManager.getApplicationInfo(packageName, 0) }.isSuccess
}
