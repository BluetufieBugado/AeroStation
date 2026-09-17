package com.btbugado.aerostation.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object EmulatorLauncher {
    fun launch(context: Context, game: Game) {
        // Plataforma forçada no jogo (segurar > Definir plataforma) vence a
        // da extensão — é o que resolve .iso/.bin/.chd ambíguos (PS1/PS2/PSP).
        val console = runCatching {
            GameOverridesStore.load(context)[game.uri.toString()]?.console
        }.getOrNull() ?: game.console
        if (console == null) {
            Toast.makeText(context, "Não consegui identificar o console desse jogo.", Toast.LENGTH_SHORT).show()
            return
        }

        val configuredPackage = EmulatorConfigStore.getPackageCompat(context, console)
        val configuredActivity = EmulatorConfigStore.getActivityCompat(context, console)
        val target = if (configuredPackage != null) {
            EmulatorTarget(
                configuredPackage,
                configuredActivity ?: "",
                appLabel(context, configuredPackage)
            )
        } else {
            EmulatorRegistry.defaultTarget(context, console)
        }

        if (target == null) {
            Toast.makeText(
                context,
                "Nenhum emulador configurado para $console.\nAbra Configurações > Emuladores.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Jingle de "iniciando jogo" (só quando vai abrir de verdade).
        AppAudio.playLaunch()
        // 0) Intent própria do emulador (ex: DuckStation via extra bootPath
        // em vez de ACTION_VIEW). Vale pelo pacote, mesmo que a activity
        // salva na configuração seja outra/antiga.
        EmulatorRegistry.customBootIntent(game, target, console)?.let { custom ->
            if (runCatching { context.startActivity(custom) }.isSuccess) {
                PlayTimeStore.beginSession(context, game.uri.toString())
                return
            }
            // Falhou: segue pras tentativas VIEW/fallback abaixo.
        }
        // Citra (MMJ/oficial/Canary): boot direto na EmulationActivity (VIEW
        // + GamePath), tentando as activities de cada fork. Antes caía no
        // VIEW genérico mirando a MainActivity (lista de jogos) e terminava
        // na home com aviso.
        if (EmulatorRegistry.isCitraPackage(target.packageName)) {
            if (tryCitraBoot(context, game, target)) {
                PlayTimeStore.beginSession(context, game.uri.toString())
                return
            }
            // Falhou: segue pro VIEW genérico/home abaixo.
        }
        // Dolphin (oficial/MMJR/MMJR2): boot direto via extra AutoStartFile.
        // O VIEW genérico abaixo NÃO boota nada no Dolphin (ele ignora a
        // URI como dado e abre a lista de jogos) — por isso tenta antes.
        if (EmulatorRegistry.isDolphinPackage(target.packageName)) {
            if (tryDolphinBoot(context, game, target)) {
                PlayTimeStore.beginSession(context, game.uri.toString())
                return
            }
            // Falhou: segue pro VIEW genérico/home abaixo.
        }
        // PS Vita (Vita3K original/Plus/forks): boot pelo Title ID do jogo
        // INSTALADO — o .vpk em si não abre por intent (o Vita3K ignora a
        // URI como dado). Vale pra qualquer app configurado pro console;
        // RetroArch não tem core de Vita, então segue pro guard dele abaixo.
        if (console == "PS Vita" && !EmulatorRegistry.isRetroArchPackage(target.packageName)) {
            val titleId = VitaTitleId.resolve(context, game)
            if (titleId != null && tryVita3KBoot(context, game, target, titleId)) {
                PlayTimeStore.beginSession(context, game.uri.toString())
                return
            }
            if (titleId == null) {
                Toast.makeText(
                    context,
                    "Não encontrei o Title ID desse jogo (ex: PCSB00245).\nInstale o .vpk dentro do Vita3K e abra por lá.",
                    Toast.LENGTH_LONG
                ).show()
            }
            if (openEmulatorHome(context, target)) {
                PlayTimeStore.beginSession(context, game.uri.toString())
            }
            return
        }
        // RetroArch SEM intent própria (sem core mapeado ou sem caminho real)
        // NÃO pode cair no VIEW genérico: o RetroArch abre sem núcleo e
        // trava numa tela preta sem erro nem controles (era o bug do N64).
        // Abre a home com aviso explicando o que falta.
        if (EmulatorRegistry.isRetroArchPackage(target.packageName)) {
            val core = target.libretroCore?.takeIf { it.isNotBlank() }
                ?: EmulatorRegistry.coreFor(console, target.packageName)
            val path = EmulatorRegistry.realPathFromTreeUri(game.uri)
            val why = when {
                core == null -> "sem núcleo mapeado para $console no RetroArch"
                path == null -> "não consegui ler o caminho do jogo (use pasta na memória interna)"
                else -> "falha ao enviar o jogo (confira o núcleo em Online Updater)"
            }
            Toast.makeText(
                context,
                "RetroArch: $why.\nNúcleo esperado: ${core ?: "?"}\nBaixe-o no RetroArch > Online Updater e dê acesso total aos arquivos.",
                Toast.LENGTH_LONG
            ).show()
            if (openEmulatorHome(context, target)) {
                PlayTimeStore.beginSession(context, game.uri.toString())
            }
            return
        }
        // VIEW em cascata (o tryView já varia MIME específico, genérico e
        // sem tipo): cobre filtros exigentes e os que só declaram scheme.
        if (tryView(context, game, target, mimeFor(game.extension))) {
            PlayTimeStore.beginSession(context, game.uri.toString())
            return
        }
        // Último recurso: abre o emulador pra escolher o jogo lá dentro,
        // em vez de terminar num erro sem saída.
        if (openEmulatorHome(context, target)) {
            PlayTimeStore.beginSession(context, game.uri.toString())
        }
    }

    /**
     * Boot direto no Citra: tenta cada EmulationActivity candidata em ordem;
     * a primeira que abrir vence. Falhas (activity inexistente no fork,
     * não-exportada) só passam pra próxima tentativa.
     */
    private fun tryCitraBoot(context: Context, game: Game, target: EmulatorTarget): Boolean {
        for (intent in EmulatorRegistry.citraBootIntents(game, target.packageName)) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Tenta a próxima variante de activity.
            } catch (_: SecurityException) {
                // Activity existe mas não é exportada nesse build.
            }
        }
        return false
    }

    /**
     * Boot direto no Dolphin: tenta cada intent candidata em ordem; a
     * primeira que abrir vence. Falhas (activity inexistente no fork,
     * não-exportada) só passam pra próxima tentativa.
     */
    private fun tryDolphinBoot(context: Context, game: Game, target: EmulatorTarget): Boolean {
        for (intent in EmulatorRegistry.dolphinBootIntents(game, target)) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Tenta a próxima variante de activity/formato.
            } catch (_: SecurityException) {
                // Activity existe mas não é exportada nesse build.
            }
        }
        return false
    }

    /**
     * Boot direto no Vita3K pelo Title ID: tenta cada activity candidata em
     * ordem; a primeira que abrir vence. Falhas (activity inexistente no
     * fork, não-exportada) só passam pra próxima tentativa.
     */
    private fun tryVita3KBoot(context: Context, game: Game, target: EmulatorTarget, titleId: String): Boolean {
        for (intent in EmulatorRegistry.vita3kBootIntents(game, target, titleId)) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Tenta a próxima variante de activity.
            } catch (_: SecurityException) {
                // Activity existe mas não é exportada nesse build.
            }
        }
        return false
    }

    /**
     * Tenta entregar a ROM via ACTION_VIEW. False = tenta a próxima opção.
     *
     * Ordem: activities explícitas candidatas (salva + extras do pacote,
     * com MIME específico e genérico), implícito com MIME, e por fim
     * implícito SEM tipo (só a URI). Esse último casa com filtros que
     * declaram só scheme (ex: ARMSX2 aceita content/file sem mimeType —
     * intent com tipo nunca casaria com eles). Todas as falhas só passam
     * pra próxima tentativa.
     */
    private fun tryView(context: Context, game: Game, target: EmulatorTarget, mime: String): Boolean {
        val mimes = if (mime == "*/*") listOf(mime) else listOf(mime, "*/*")
        val attempts = ArrayList<Intent>(8)
        for (activity in EmulatorRegistry.viewActivityCandidates(target)) {
            for (m in mimes) {
                attempts += Intent(Intent.ACTION_VIEW).apply {
                    setClassName(target.packageName, activity)
                    setDataAndType(game.uri, m)
                }
            }
        }
        for (m in mimes) {
            attempts += Intent(Intent.ACTION_VIEW).apply {
                setPackage(target.packageName)
                setDataAndType(game.uri, m)
            }
        }
        attempts += Intent(Intent.ACTION_VIEW).apply {
            setPackage(target.packageName)
            data = game.uri
        }

        for (intent in attempts) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = android.content.ClipData.newRawUri("ROM", game.uri)
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Tenta a próxima forma.
            } catch (_: SecurityException) {
                // Ex: activity não exportada — as demais ainda podem servir.
            }
        }
        return false
    }

    /**
     * Abre a tela inicial do emulador (fallback quando o direto falha).
     * True = o emulador abriu (vale sessão de tempo de jogo).
     */
    private fun openEmulatorHome(context: Context, target: EmulatorTarget): Boolean {
        val home = runCatching {
            context.packageManager.getLaunchIntentForPackage(target.packageName)
        }.getOrNull()

        if (home != null) {
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val opened = runCatching { context.startActivity(home) }.isSuccess
            if (opened) {
                Toast.makeText(
                    context,
                    "Não consegui enviar o jogo direto; escolha ele dentro do emulador.",
                    Toast.LENGTH_LONG
                ).show()
                return true
            }
        }
        Toast.makeText(
            context,
            "O emulador configurado não consegue abrir esta ROM.",
            Toast.LENGTH_LONG
        ).show()
        return false
    }

    private fun mimeFor(extension: String): String = when (extension.lowercase()) {
        "cue" -> "application/x-cue"
        "iso" -> "application/x-iso9660-image"
        "chd" -> "application/x-chd"
        // J2ME Loader declara VIEW pra application/java-archive (e path
        // .jar/.jad/.kjx) no manifest — manda o tipo certo em vez do genérico.
        "jar" -> "application/java-archive"
        "jad" -> "text/vnd.sun.j2me.app-descriptor"
        else -> "application/octet-stream"
    }

    private fun appLabel(context: Context, packageName: String): String =
        runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        }.getOrDefault(packageName)
}
