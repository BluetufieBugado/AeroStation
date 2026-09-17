package com.btbugado.aerostation.ui

import android.content.Intent
import java.io.File
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.btbugado.aerostation.data.ArtSource
import com.btbugado.aerostation.data.AndroidAppLauncher
import com.btbugado.aerostation.data.AndroidGame
import com.btbugado.aerostation.data.AndroidGameStore
import com.btbugado.aerostation.data.CoverArtRepository
import com.btbugado.aerostation.data.DisplayGame
import com.btbugado.aerostation.data.Game
import com.btbugado.aerostation.data.GameLaunchSettingsStore
import com.btbugado.aerostation.data.GameOverride
import com.btbugado.aerostation.data.EmulatorLauncher
import com.btbugado.aerostation.data.GameOverridesStore
import com.btbugado.aerostation.data.LauncherContentCache
import com.btbugado.aerostation.data.PlayTimeStore
import com.btbugado.aerostation.data.ScreenshotStore
import com.btbugado.aerostation.data.RomExtensions
import com.btbugado.aerostation.data.RomFolderStore
import com.btbugado.aerostation.data.RomScanner
import com.btbugado.aerostation.data.SteamGridDbLauncher
import com.btbugado.aerostation.data.withOverride
import com.btbugado.aerostation.ui.components.CartridgeInsertOverlay
import com.btbugado.aerostation.ui.components.CartridgePreviewDialog
import com.btbugado.aerostation.ui.components.DialogGamepadBar
import com.btbugado.aerostation.ui.components.GamepadDialogScope
import com.btbugado.aerostation.ui.components.LocalHideNavForOverlay
import com.btbugado.aerostation.ui.components.gamepadFocusable
import com.btbugado.aerostation.ui.components.GameActionMenu
import com.btbugado.aerostation.ui.components.GameCard
import com.btbugado.aerostation.ui.components.GlassIconButton
import com.btbugado.aerostation.ui.components.ProfileAvatar
import com.btbugado.aerostation.ui.components.SystemStatus
import com.btbugado.aerostation.ui.components.RenameGameDialog
import com.btbugado.aerostation.ui.theme.AeroTextPrimary
import com.btbugado.aerostation.ui.theme.AeroTextSecondary
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.btbugado.aerostation.data.RaResult
import com.btbugado.aerostation.data.RetroAchievementsApi
import com.btbugado.aerostation.data.RetroAchievementsStore

/** Largura de cada ícone de jogo na fileira — a altura vem do aspect ratio do GameCard. */
private val GameCardWidth = 130.dp

/** Foto do RetroAchievements salva local (base da hierarquia do avatar). */
private const val RaAvatarFile = "profile_avatar_ra.jpg"

@Composable
fun HomeScreen(
    contentPadding: PaddingValues = PaddingValues(),
    gamepadActive: Boolean = true,
    /** Incrementado quando uma sessão fecha: atualiza o tempo no verso. */
    playTick: Int = 0
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var folderUri by remember { mutableStateOf(RomFolderStore.load(context)) }
    var games by remember { mutableStateOf<List<Game>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var overrides by remember { mutableStateOf(GameOverridesStore.load(context)) }
    var refreshTick by remember { mutableStateOf(0) }
    val androidGamesRevision = AndroidGameStore.revision

    // Foto de perfil exibida no canto superior esquerdo.
    var profileImagePath by remember {
        mutableStateOf(
            context.getSharedPreferences("profile", android.content.Context.MODE_PRIVATE)
                .getString("image_path", null)
        )
    }

    // Hierarquia do avatar: foto da galeria (override) > foto do
    // RetroAchievements (baixada) > silhueta padrão.
    var raImagePath by remember { mutableStateOf<String?>(null) }
    var raRefreshTick by remember { mutableStateOf(0) }
    val raCredentialsKey = remember {
        RetroAchievementsStore.loadUsername(context) + "\u0000" +
            RetroAchievementsStore.loadApiKey(context)
    }

    /** Baixa a foto do RA pro armazenamento privado (offline-friendly). */
    suspend fun downloadRaAvatar(): String? = withContext(Dispatchers.IO) {
        val username = RetroAchievementsStore.loadUsername(context)
        val apiKey = RetroAchievementsStore.loadApiKey(context)
        if (username.isBlank() || apiKey.isBlank()) return@withContext null
        val picUrl = (RetroAchievementsApi.getUserAccount(username, apiKey) as? RaResult.Ok)
            ?.value?.second ?: return@withContext null
        try {
            val dest = File(context.filesDir, RaAvatarFile)
            val connection = java.net.URL(picUrl).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = true
            if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) return@withContext null
            connection.inputStream.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    LaunchedEffect(raCredentialsKey, raRefreshTick) {
        if (!RetroAchievementsStore.hasCredentials(context)) {
            raImagePath = null
            return@LaunchedEffect
        }
        // Mostra o arquivo já baixado na hora (offline); depois tenta atualizar.
        File(context.filesDir, RaAvatarFile).takeIf { it.exists() }?.let {
            raImagePath = it.absolutePath
        }
        downloadRaAvatar()?.let { raImagePath = it }
    }

    /** Segurar o avatar: apaga a foto da galeria e volta pra do RA. */
    fun restoreRaAvatar() {
        val hadCustom = profileImagePath != null
        profileImagePath?.let { runCatching { File(it).delete() } }
        profileImagePath = null
        context.getSharedPreferences("profile", android.content.Context.MODE_PRIVATE)
            .edit().remove("image_path").apply()
        if (RetroAchievementsStore.hasCredentials(context)) {
            raRefreshTick++
            Toast.makeText(
                context,
                if (hadCustom) "Foto do RetroAchievements restaurada" else "Foto do RetroAchievements atualizada",
                Toast.LENGTH_SHORT
            ).show()
        } else if (hadCustom) {
            Toast.makeText(context, "Foto personalizada removida", Toast.LENGTH_SHORT).show()
        }
    }

    // Qual jogo está com o menu de long-press aberto / sendo renomeado / recebendo nova capa
    var menuGame by remember { mutableStateOf<Game?>(null) }
    var renameGame by remember { mutableStateOf<Game?>(null) }
    var pickImageForGame by remember { mutableStateOf<Game?>(null) }
    // Qual jogo está escolhendo a plataforma manual (override do .iso etc).
    var pickConsoleGame by remember { mutableStateOf<Game?>(null) }
    // Preview 3D (toque/A no card) e inserção (após Jogar, antes do boot).
    var previewGame by remember { mutableStateOf<DisplayGame?>(null) }
    var insertingGame by remember { mutableStateOf<DisplayGame?>(null) }

    // Overlays fullscreen escondem a BottomNav da raiz (visível e tocável).
    // O dispose zera ao sair da aba (ex: L1/R1 no meio do preview).
    val setNavHidden = LocalHideNavForOverlay.current
    LaunchedEffect(previewGame, insertingGame) {
        setNavHidden?.invoke(previewGame != null || insertingGame != null)
    }
    DisposableEffect(Unit) {
        onDispose { setNavHidden?.invoke(false) }
    }

    // Jogo que está aguardando a imagem escolhida no SteamGridDB.
    // O site é aberto no navegador; ao retornar ao launcher, abrimos
    // automaticamente o seletor de arquivos para pegar o download.
    var steamGridGame by remember { mutableStateOf<Game?>(null) }
    var steamGridBrowserPaused by remember { mutableStateOf(false) }

    fun updateOverride(game: Game, transform: (GameOverride) -> GameOverride) {
        val key = game.uri.toString()
        val updated = overrides.toMutableMap()
        updated[key] = transform(updated[key] ?: GameOverride())
        overrides = updated
        GameOverridesStore.save(context, updated)
    }

    fun clearOverride(game: Game) {
        val key = game.uri.toString()
        val updated = overrides.toMutableMap()
        updated.remove(key)
        overrides = updated
        GameOverridesStore.save(context, updated)
    }

    val pickProfileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            val profileFile = File(context.filesDir, "profile_avatar.jpg")
            val copied = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    profileFile.outputStream().use { output -> input.copyTo(output) }
                }
                profileFile
            }.getOrNull()

            if (copied != null && copied.exists()) {
                profileImagePath = copied.absolutePath
                context.getSharedPreferences(
                    "profile",
                    android.content.Context.MODE_PRIVATE
                ).edit().putString("image_path", copied.absolutePath).apply()
            } else {
                Toast.makeText(
                    context,
                    "Não consegui usar essa imagem",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val target = pickImageForGame
        pickImageForGame = null
        if (uri != null && target != null) {
            scope.launch {
                val localPath = CoverArtRepository.copyCustomCover(context, target.uri.toString(), uri)
                if (localPath != null) {
                    updateOverride(target) { it.copy(artUri = localPath, artSource = ArtSource.CUSTOM) }
                } else {
                    Toast.makeText(context, "Não consegui usar essa imagem", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val pickSteamGridImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val target = steamGridGame
        steamGridGame = null

        if (uri != null && target != null) {
            scope.launch {
                val localPath = CoverArtRepository.copyCustomCover(
                    context,
                    target.uri.toString(),
                    uri
                )
                if (localPath != null) {
                    updateOverride(target) {
                        it.copy(
                            artUri = localPath,
                            artSource = ArtSource.CUSTOM
                        )
                    }
                    Toast.makeText(
                        context,
                        "Capa do SteamGridDB aplicada!",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "Não consegui usar essa imagem",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // Só fica ativo enquanto estamos aguardando o retorno do navegador.
    DisposableEffect(lifecycleOwner, steamGridGame) {
        if (steamGridGame == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                        steamGridBrowserPaused = true
                    }
                    androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                        if (steamGridBrowserPaused) {
                            steamGridBrowserPaused = false
                            pickSteamGridImageLauncher.launch(arrayOf("image/*"))
                        }
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        RomFolderStore.save(context, uri)
        LauncherContentCache.invalidateGames()
        refreshTick = 0
        folderUri = uri
    }

    LaunchedEffect(folderUri, refreshTick) {
        val uri = folderUri
        if (uri == null) {
            games = emptyList()
            isScanning = false
            return@LaunchedEffect
        }

        // Trocar de aba não deve disparar um novo scan. Só o botão de
        // refresh (refreshTick) ou uma pasta diferente força a leitura.
        val cached = LauncherContentCache.getGames(uri)
        if (cached != null && refreshTick == 0) {
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

    // Ordem estável da esquerda pra direita: mesma ordem alfabética que já
    // vem do RomScanner, sem reordenar por uso/recente como o Switch faz
    // (isso pode virar uma opção mais pra frente).
    val androidGames = remember(androidGamesRevision) { AndroidGameStore.load(context) }
    val androidGameModels = remember(androidGames, overrides) {
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
    val displayGames = remember(games, overrides, androidGameModels) {
        games.map { game -> game.withOverride(overrides[game.uri.toString()]) } + androidGameModels
    }

    // Tempo de jogo pro verso dos cards (recarrega quando sessão fecha).
    // Só carrega se o flip estiver ligado — senão nem consulta o disco.
    val flipEnabled = remember { GameLaunchSettingsStore.isFlipEnabled(context) }
    val flipFrontHoldMs = remember { GameLaunchSettingsStore.getFlipSeconds(context) * 1000L }
    val playStats = remember(games, androidGameModels, playTick, flipEnabled) {
        if (flipEnabled) PlayTimeStore.loadAll(context) else emptyMap()
    }
    // Capturas por jogo pro verso rotativo (só com flip ligado).
    val tileShots = remember(games, playTick, flipEnabled) {
        if (flipEnabled) ScreenshotStore.loadMap(context) else emptyMap()
    }

    /** Abertura única de jogo/app (preview 3D ou direto, conforme Ajustes). */
    fun openDisplayGame(displayGame: DisplayGame) {
        if (GameLaunchSettingsStore.isPreviewEnabled(context)) {
            // Preview 3D (vale pra ROM e pra app); o boot sai do Jogar.
            previewGame = displayGame
        } else {
            val androidApp = androidGames.firstOrNull { it.key == displayGame.game.uri.toString() }
            if (androidApp != null) AndroidAppLauncher.launch(context, androidApp)
            else EmulatorLauncher.launch(context, displayGame.game)
        }
    }

    // Ligação explícita de foco topo <-> fileira de jogos.
    // Sem isso a busca automática 2D do Compose, ao apertar Baixo no
    // avatar/refresh, pulava o LazyRow e ia direto pra BottomNavBar.
    val avatarRequester = remember { FocusRequester() }
    val refreshRequester = remember { FocusRequester() }
    val firstGameRequester = remember { FocusRequester() }
    val hasGames = displayGames.isNotEmpty() && !isScanning
    // Estado da fileira: ao focar o primeiro jogo, voltamos ao item 0 pra
    // fila reassumir o recuo inicial (76dp). Sem isso o bringIntoView parava
    // no mínimo visível e o card colava na borda esquerda.
    val rowState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Cabeçalho inspirado no Nintendo Switch:
        // avatar do usuário à esquerda; relógio, Wi-Fi e bateria à direita.
        ProfileAvatar(
            imagePath = profileImagePath ?: raImagePath,
            onClick = {
                pickProfileLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onLongClick = { restoreRaAvatar() },
            focusRequester = avatarRequester,
            nextRight = refreshRequester,
            nextDown = if (hasGames) firstGameRequester else null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 14.dp, start = 20.dp)
                .zIndex(2f)
        )

        SystemStatus(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 22.dp, end = 20.dp)
                .zIndex(2f)
        )

        // "+" pra escolher a pasta pela primeira vez. Depois de escolhida:
        // toque curto = refresh (reescaneia a MESMA pasta, sem abrir seletor);
        // toque longo = abre o seletor de novo, pra trocar de pasta.
        GlassIconButton(
            onClick = {
                if (folderUri == null) {
                    pickFolderLauncher.launch(null)
                } else {
                    LauncherContentCache.invalidateGames()
                    refreshTick++
                }
            },
            onLongClick = {
                // Segurar sempre abre o seletor de pasta, mesmo já tendo uma
                // escolhida — é como se troca pra uma pasta diferente.
                pickFolderLauncher.launch(null)
            },
            // Sem pasta escolhida ainda não existe nenhum GameCard pra focar
            // primeiro, então esse botão precisa ser o alvo inicial do
            // cursor do gamepad — senão o controle fica sem nada pra focar.
            gamepadAutoFocus = gamepadActive && folderUri == null,
            focusRequester = refreshRequester,
            nextLeft = avatarRequester,
            nextDown = if (hasGames) firstGameRequester else null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 15.dp, start = 76.dp)
                .zIndex(1f)
        ) {
            if (folderUri == null) {
                Text(text = "+", fontSize = 24.sp, color = AeroTextPrimary)
            } else if (isScanning) {
                val infiniteTransition = rememberInfiniteTransition(label = "refresh-spin")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 900, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "refresh-rotation"
                )
                Text(
                    text = "\u27F3",
                    fontSize = 20.sp,
                    color = AeroTextPrimary,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation }
                )
            } else {
                Text(text = "\u27F3", fontSize = 20.sp, color = AeroTextPrimary)
            }
        }

        when {
            isScanning -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            folderUri == null -> {
                EmptyState(
                    message = "Nenhuma pasta de ROMs selecionada ainda.\nToque no \"+\" no canto superior pra começar."
                )
            }

            displayGames.isEmpty() -> {
                EmptyState(
                    message = if (folderUri == null) {
                        "Nenhum jogo ou aplicativo adicionado à tela inicial ainda."
                    } else {
                        "Nenhuma ROM encontrada nessa pasta.\nConfira se os arquivos têm uma extensão reconhecida."
                    }
                )
            }

            else -> {
                LazyRow(
                    state = rowState,
                    contentPadding = PaddingValues(
                        // A fila começa no eixo X do botão de refresh (76dp),
                        // como no Switch — sem isso os cards colavam na borda.
                        start = 76.dp,
                        end = 32.dp,
                        top = contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding()
                    ),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(displayGames, key = { _, it -> it.game.uri.toString() }) { index, displayGame ->
                        val stats = playStats[displayGame.game.uri.toString()]
                        GameCard(
                            title = displayGame.displayName,
                            artUrl = displayGame.artUrl,
                            modifier = Modifier.width(GameCardWidth),
                            gamepadAutoFocus = gamepadActive && index == 0,
                            // O primeiro card é o alvo do Baixo vindo do topo.
                            // Todos os cards voltam pro topo com Cima, pra nunca
                            // ficar preso na fileira sem rota de volta.
                            // Focar o primeiro recentraliza a fila no recuo inicial.
                            focusRequester = if (index == 0) firstGameRequester else null,
                            nextUp = refreshRequester,
                            onFocusGained = if (index == 0) {
                                { scope.launch { rowState.animateScrollToItem(0) } }
                            } else null,
                            onClick = { openDisplayGame(displayGame) },
                            onLongClick = { menuGame = displayGame.game },
                            // Verso estilo live tile: console + tempo total.
                            // O atraso dessincroniza a fileira (ciclo de 12).
                            flipEnabled = flipEnabled,
                            flipStartDelayMs = (index % 12) * 350L,
                            flipFrontHoldMs = flipFrontHoldMs,
                            backLine1 = displayGame.game.console,
                            backLine2 = stats?.takeIf { it.totalMillis > 0L }?.let {
                                "⏱ ${PlayTimeStore.formatShort(it.totalMillis)} • ${it.sessions}x"
                            },
                            backArtUrls = tileShots[displayGame.game.uri.toString()].orEmpty()
                        )
                    }
                }
            }
        }

        menuGame?.let { game ->
            val currentName = overrides[game.uri.toString()]?.customName ?: game.name
            GameActionMenu(
                gameName = currentName,
                onDismiss = { menuGame = null },
                onRename = {
                    renameGame = game
                    menuGame = null
                },
                onPickFromGallery = {
                    pickImageForGame = game
                    menuGame = null
                    pickImageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onSearchSteamGridDb = {
                    menuGame = null
                    steamGridGame = game

                    val opened = SteamGridDbLauncher.openSearch(
                        context,
                        currentName
                    )

                    if (!opened) {
                        steamGridGame = null
                        Toast.makeText(
                            context,
                            "Não consegui abrir o navegador",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onAutoFetchCover = {
                    menuGame = null
                    scope.launch {
                        // Com plataforma forçada, a capa sai do repo certo.
                        val effective = overrides[game.uri.toString()]?.console
                            ?.let { game.copy(console = it) } ?: game
                        val url = CoverArtRepository.findAutoCoverUrl(effective)
                        if (url != null) {
                            updateOverride(game) { it.copy(artUri = url, artSource = ArtSource.AUTO) }
                        } else {
                            Toast.makeText(
                                context,
                                "Não encontrei capa automática pra esse jogo",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onPickConsole = {
                    pickConsoleGame = game
                    menuGame = null
                },
                onResetToDefault = {
                    menuGame = null
                    clearOverride(game)
                },
                onRemoveAndroidApp = if (game.uri.scheme == "retroaero" && game.uri.host == "android-app") {
                    {
                        menuGame = null
                        AndroidGameStore.remove(context, game.uri.path?.removePrefix("/") ?: "")
                        clearOverride(game)
                    }
                } else null
            )
        }

        renameGame?.let { game ->
            val currentName = overrides[game.uri.toString()]?.customName ?: game.name
            RenameGameDialog(
                initialName = currentName,
                onDismiss = { renameGame = null },
                onConfirm = { newName ->
                    updateOverride(game) { it.copy(customName = newName) }
                    renameGame = null
                }
            )
        }

        pickConsoleGame?.let { game ->
            val key = game.uri.toString()
            val current = overrides[key]?.console
            ConsolePickerDialog(
                currentConsole = current,
                autoConsole = game.console,
                onDismiss = { pickConsoleGame = null },
                onSelected = { selected ->
                    updateOverride(game) { it.copy(console = selected) }
                    pickConsoleGame = null
                    Toast.makeText(
                        context,
                        if (selected == null) {
                            "Plataforma automática restaurada"
                        } else {
                            "Plataforma definida: $selected"
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
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
 * Escolhe a plataforma de um jogo (override). Formatos ambíguos (.iso,
 * .bin, .chd) vêm do scan como PlayStation; aqui o usuário corrige pra
 * PS2, PSP etc. Null = automática da extensão.
 */
@Composable
private fun ConsolePickerDialog(
    currentConsole: String?,
    autoConsole: String?,
    onDismiss: () -> Unit,
    onSelected: (String?) -> Unit
) {
    val options = remember {
        listOf(null) + RomExtensions.consoles.sorted()
    }

    GamepadDialogScope {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Plataforma do jogo") },
            text = {
                Column {
                    LazyColumn(modifier = Modifier.height(320.dp)) {
                        itemsIndexed(options, key = { index, it -> it ?: "auto-$index" }) { index, console ->
                            val label = console
                                ?: "Automática${if (autoConsole != null) " ($autoConsole)" else ""}"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .gamepadFocusable(
                                        autoFocus = index == 0,
                                        onConfirm = { onSelected(console) },
                                        onBack = onDismiss
                                    )
                                    .clickable { onSelected(console) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        label,
                                        color = if (console == currentConsole) {
                                            AeroTextPrimary
                                        } else {
                                            AeroTextSecondary
                                        }
                                    )
                                }
                                if (console == currentConsole) {
                                    Text(text = "✓", color = AeroTextPrimary)
                                }
                            }
                        }
                    }
                    DialogGamepadBar()
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.gamepadFocusable(onConfirm = onDismiss, onBack = onDismiss)
                ) { Text("Cancelar") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                color = AeroTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
