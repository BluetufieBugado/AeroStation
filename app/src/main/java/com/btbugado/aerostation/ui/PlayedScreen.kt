package com.btbugado.aerostation.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.btbugado.aerostation.data.AndroidAppLauncher
import com.btbugado.aerostation.data.AndroidGameStore
import com.btbugado.aerostation.data.DisplayGame
import com.btbugado.aerostation.data.EmulatorLauncher
import com.btbugado.aerostation.data.Game
import com.btbugado.aerostation.data.GameAchievements
import com.btbugado.aerostation.data.GameLaunchSettingsStore
import com.btbugado.aerostation.data.GameOverridesStore
import com.btbugado.aerostation.data.LauncherContentCache
import com.btbugado.aerostation.data.PlayTimeStore
import com.btbugado.aerostation.data.RetroAchievementsStore
import com.btbugado.aerostation.data.RomFolderStore
import com.btbugado.aerostation.data.RomScanner
import com.btbugado.aerostation.data.withOverride
import com.btbugado.aerostation.ui.components.CartridgeInsertOverlay
import com.btbugado.aerostation.ui.components.CartridgePreviewDialog
import com.btbugado.aerostation.ui.components.LocalHideNavForOverlay
import com.btbugado.aerostation.ui.components.LocalTouchActionsEnabled
import com.btbugado.aerostation.ui.components.gamepadFocusable
import com.btbugado.aerostation.ui.theme.AeroGlassBorder
import com.btbugado.aerostation.ui.theme.AeroGlassWhite
import com.btbugado.aerostation.ui.theme.AeroGlassWhiteStrong
import com.btbugado.aerostation.ui.theme.AeroTextPrimary
import com.btbugado.aerostation.ui.theme.AeroTextSecondary

/**
 * Aba "Jogados": ranking por tempo total de jogo (sessões fechadas pelo
 * [PlayTimeStore]), com capa, console, tempo, nº de sessões e última vez.
 *
 * Abrir funciona igual à Home (preview 3D ou direto, conforme Ajustes).
 * Customização (renomear/capa/plataforma) continua na Home, no segurar.
 */
@Composable
fun PlayedScreen(
    contentPadding: PaddingValues = PaddingValues(),
    gamepadActive: Boolean = false,
    /** Incrementado quando uma sessão fecha: recarrega tudo. */
    playTick: Int = 0
) {
    val context = LocalContext.current
    val touchOk = LocalTouchActionsEnabled.current

    var previewGame by remember { mutableStateOf<DisplayGame?>(null) }
    var insertingGame by remember { mutableStateOf<DisplayGame?>(null) }

    var games by remember { mutableStateOf<List<Game>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    val folderUri = remember { RomFolderStore.load(context) }

    // Catálogo: do cache quando dá (a Home é dona do scan); senão escaneia.
    LaunchedEffect(folderUri, playTick) {
        val uri = folderUri
        if (uri == null) {
            games = emptyList()
            isScanning = false
            return@LaunchedEffect
        }
        val cached = LauncherContentCache.getGames(uri)
        if (cached != null) {
            games = cached
            isScanning = false
            return@LaunchedEffect
        }
        isScanning = true
        val scanned = RomScanner.scan(context, uri)
        LauncherContentCache.putGames(uri, scanned)
        games = scanned
        isScanning = false
    }

    // Nomes/capas custom + apps Android + tempos (tudo recarrega no tick).
    val overrides = remember(playTick) { GameOverridesStore.load(context) }
    val androidGames = remember(playTick) { AndroidGameStore.load(context) }
    val androidModels = remember(androidGames, overrides) {
        androidGames.map { app ->
            val synthetic = Game(
                name = app.displayName,
                uri = Uri.parse(app.key),
                extension = "android",
                console = "Android",
                sizeBytes = 0L
            )
            synthetic.withOverride(overrides[synthetic.uri.toString()]).copy(
                artUrl = overrides[synthetic.uri.toString()]?.artUri ?: app.iconUri
            )
        }
    }
    val stats = remember(playTick) { PlayTimeStore.loadAll(context) }
    val ranked = remember(games, androidModels, overrides, stats) {
        val all = games.map { it.withOverride(overrides[it.uri.toString()]) } + androidModels
        all.mapNotNull { displayGame ->
            stats[displayGame.game.uri.toString()]
                ?.takeIf { it.totalMillis > 0L }
                ?.let { displayGame to it }
        }.sortedByDescending { it.second.totalMillis }
    }

    fun openGame(displayGame: DisplayGame) {
        if (GameLaunchSettingsStore.isPreviewEnabled(context)) {
            previewGame = displayGame
        } else {
            val androidApp = androidGames.firstOrNull { it.key == displayGame.game.uri.toString() }
            if (androidApp != null) AndroidAppLauncher.launch(context, androidApp)
            else EmulatorLauncher.launch(context, displayGame.game)
        }
    }

    // Overlays fullscreen escondem a BottomNav da raiz.
    val setNavHidden = LocalHideNavForOverlay.current
    LaunchedEffect(previewGame, insertingGame) {
        setNavHidden?.invoke(previewGame != null || insertingGame != null)
    }
    DisposableEffect(Unit) {
        onDispose { setNavHidden?.invoke(false) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 20.dp, bottom = contentPadding.calculateBottomPadding())
        ) {
            Text(
                text = "Mais jogados",
                color = AeroTextPrimary,
                fontSize = 26.sp,
                modifier = Modifier.padding(start = 76.dp, bottom = 4.dp)
            )
            Text(
                text = "Ordenados por tempo total de jogo.",
                color = AeroTextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 76.dp, bottom = 14.dp)
            )

            when {
                isScanning -> {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                ranked.isEmpty() -> {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Nada por aqui ainda.\nJogue algo que ele aparece neste ranking.",
                            color = AeroTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(start = 76.dp, end = 32.dp, bottom = 12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        itemsIndexed(ranked, key = { _, it -> it.first.game.uri.toString() }) { index, item ->
                            val (displayGame, play) = item
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(AeroGlassWhiteStrong, AeroGlassWhite)
                                        )
                                    )
                                    .border(1.dp, AeroGlassBorder, RoundedCornerShape(16.dp))
                                    .gamepadFocusable(
                                        autoFocus = gamepadActive && index == 0,
                                        onConfirm = { openGame(displayGame) }
                                    )
                                    .clickable { if (touchOk) openGame(displayGame) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = AeroTextSecondary,
                                    fontSize = 18.sp,
                                    modifier = Modifier.width(30.dp),
                                    textAlign = TextAlign.Center
                                )
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0x552E86AB), Color(0x556FD6C4))
                                            ),
                                            RoundedCornerShape(12.dp)
                                        )
                                ) {
                                    if (displayGame.artUrl != null) {
                                        AsyncImage(
                                            model = displayGame.artUrl,
                                            contentDescription = displayGame.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = displayGame.displayName,
                                        color = AeroTextPrimary,
                                        fontSize = 16.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = displayGame.game.console ?: "Aplicativo",
                                        color = AeroTextSecondary,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${PlayTimeStore.formatShort(play.totalMillis)} • " +
                                            "${play.sessions}x • ${PlayTimeStore.formatLastPlayed(play.lastPlayedEpoch)}",
                                        color = AeroTextSecondary,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                    RowAchievement(
                                        gameKey = displayGame.game.uri.toString(),
                                        displayName = displayGame.displayName,
                                        consoleName = displayGame.game.console,
                                        extension = displayGame.game.extension
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = PlayTimeStore.formatShort(play.totalMillis),
                                    color = AeroTextPrimary,
                                    fontSize = 20.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        previewGame?.let { displayGame ->
            CartridgePreviewDialog(
                displayName = displayGame.displayName,
                consoleName = displayGame.game.console,
                artUrl = displayGame.artUrl,
                onDismiss = { previewGame = null },
                onConfirm = {
                    previewGame = null
                    insertingGame = displayGame
                },
                modifier = Modifier.zIndex(50f)
            )
        }

        insertingGame?.let { displayGame ->
            CartridgeInsertOverlay(
                artUrl = displayGame.artUrl,
                title = displayGame.displayName,
                onDone = {
                    insertingGame = null
                    val androidApp = androidGames.firstOrNull { it.key == displayGame.game.uri.toString() }
                    if (androidApp != null) AndroidAppLauncher.launch(context, androidApp)
                    else EmulatorLauncher.launch(context, displayGame.game)
                },
                modifier = Modifier.zIndex(50f)
            )
        }
    }
}

/**
 * Última conquista RA embaixo de cada jogo (best-effort, silenciosa): mostra
 * só quando há unlock — sem login, sem match ou sem conquista, a linha some
 * e a fileira continua igual. Cache faz a lista custar pouco.
 */
@Composable
private fun RowAchievement(
    gameKey: String,
    displayName: String,
    consoleName: String?,
    extension: String
) {
    val context = LocalContext.current
    var text by remember(gameKey) { mutableStateOf<String?>(null) }

    LaunchedEffect(gameKey) {
        text = null
        if (gameKey.isBlank() || consoleName.isNullOrBlank()) return@LaunchedEffect
        if (!RetroAchievementsStore.hasCredentials(context)) return@LaunchedEffect
        val uri = runCatching { Uri.parse(gameKey) }.getOrNull() ?: return@LaunchedEffect
        val best = GameAchievements.lastUnlock(context, displayName, consoleName, uri, extension)?.best
            ?: return@LaunchedEffect
        text = "🏆 ${best.achievementTitle} • ${best.dateText}" +
            if (best.hardcore) " • hardcore" else ""
    }

    if (text != null) {
        Text(
            text = text!!,
            color = AeroTextSecondary,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}
