package com.btbugado.aerostation.ui

import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.provider.DocumentsContract
import android.view.KeyEvent
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.documentfile.provider.DocumentFile
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.videoFrameMicros
import com.btbugado.aerostation.data.AndroidGameStore
import com.btbugado.aerostation.data.AppAudio
import com.btbugado.aerostation.data.GameOverridesStore
import com.btbugado.aerostation.data.LauncherContentCache
import com.btbugado.aerostation.data.MediaFolder
import com.btbugado.aerostation.data.MediaFolderStore
import com.btbugado.aerostation.data.PlaySession
import com.btbugado.aerostation.data.PlayTimeStore
import com.btbugado.aerostation.data.RomFolderStore
import com.btbugado.aerostation.data.RomScanner
import com.btbugado.aerostation.data.ScreenshotStore
import com.btbugado.aerostation.ui.components.DialogGamepadBar
import com.btbugado.aerostation.ui.components.GamepadDialogScope
import com.btbugado.aerostation.ui.components.LocalDpadOverride
import com.btbugado.aerostation.ui.components.LocalGamepadCursor
import com.btbugado.aerostation.ui.components.LocalHidePadForOverlay
import com.btbugado.aerostation.ui.components.LocalVirtualPadVisible
import com.btbugado.aerostation.ui.components.LocalHideNavForOverlay
import com.btbugado.aerostation.ui.components.LocalTouchActionsEnabled
import com.btbugado.aerostation.ui.components.gamepadFocusable
import com.btbugado.aerostation.ui.theme.AeroGlassBorder
import com.btbugado.aerostation.ui.theme.AeroGlassWhite
import com.btbugado.aerostation.ui.theme.AeroGlassWhiteStrong
import com.btbugado.aerostation.ui.theme.AeroTextPrimary
import com.btbugado.aerostation.ui.theme.AeroTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Uma foto ou vídeo da galeria (capturas salvas pelos emuladores). */
private data class GalleryItem(
    val uri: Uri,
    val isVideo: Boolean,
    val dateAddedSec: Long,
    val displayName: String,
    val durationMs: Long = 0L
)

private enum class GalleryFilter { ALL, PHOTOS, VIDEOS }

/**
 * Aba "Álbum" estilo Switch: grade SÓ com as pastas dedicadas (fotos +
 * vídeos), filtro Tudo/Fotos/Vídeos, viewer em tela cheia e exclusão. Sem
 * leitura global do rolo de câmera, sem login nem rede: cada pasta entra
 * pelo gerenciador (📁) como "jogos" (tudo) ou "mista" (só arquivos com
 * nome de jogo/app da biblioteca).
 */
@Composable
fun GalleryScreen(
    contentPadding: PaddingValues = PaddingValues(),
    gamepadActive: Boolean = false
) {
    val context = LocalContext.current
    val touchOk = LocalTouchActionsEnabled.current

    // Só pastas dedicadas: sem leitura global do rolo de câmera.
    var items by remember { mutableStateOf<List<GalleryItem>?>(null) }
    var reloadTick by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf(GalleryFilter.ALL) }
    var selected by remember { mutableStateOf<GalleryItem?>(null) }
    var pendingDelete by remember { mutableStateOf<GalleryItem?>(null) }
    // Pastas dedicadas (Game Media, app de gravação…): "jogos" mostra tudo,
    // "mista" só o que tiver nome de jogo/app da biblioteca.
    var folders by remember { mutableStateOf(MediaFolderStore.load(context)) }
    var showFolders by remember { mutableStateOf(false) }
    var pendingFolderMode by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            MediaFolderStore.add(context, uri, pendingFolderMode)
            folders = MediaFolderStore.load(context)
            reloadTick++
            Toast.makeText(
                context,
                if (pendingFolderMode) "Pasta mista adicionada!" else "Pasta de jogos adicionada!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(reloadTick, folders) {
        items = withContext(Dispatchers.IO) {
            // Nomes só interessam se há pasta mista; sessões sempre (filtro
            // misto por horário + atribuição pros tiles da Home).
            val hasMixed = folders.any { it.matchNamesOnly }
            val libraryNames = if (hasMixed) loadLibraryNames(context) else emptySet()
            val sessions = if (folders.isNotEmpty()) PlayTimeStore.loadSessions(context) else emptyList()
            val loaded = loadFolderItems(context, folders, libraryNames, sessions)
            // Atribui prints (só fotos) ao jogo aberto na hora e salva pros
            // tiles — a lista já vem do mais novo pro mais antigo.
            if (sessions.isNotEmpty()) {
                val attribution = mutableMapOf<String, MutableList<String>>()
                for (item in loaded) {
                    if (item.isVideo) continue
                    val millis = item.dateAddedSec * 1000L
                    val key = sessions.firstOrNull {
                        millis in it.startEpoch..it.endEpoch + 30_000L
                    }?.gameKey ?: continue
                    attribution.getOrPut(key) { ArrayList() }.add(item.uri.toString())
                }
                ScreenshotStore.saveMap(context, attribution)
            }
            loaded
        }
    }

    fun askDelete(item: GalleryItem) {
        pendingDelete = item
    }

    fun confirmDelete(item: GalleryItem) {
        if (selected == item) selected = null
        // Arquivos SAF apagam direto pelo DocumentsContract (a permissão da
        // pasta já foi concedida); o diálogo de confirmação é o nosso.
        val ok = runCatching {
            DocumentsContract.deleteDocument(context.contentResolver, item.uri)
        }.getOrDefault(false)
        pendingDelete = null
        Toast.makeText(
            context,
            if (ok) "Apagado." else "Não consegui apagar.",
            Toast.LENGTH_SHORT
        ).show()
        if (ok) reloadTick++
    }

    // Viewer fullscreen esconde a BottomNav e o pad virtual da raiz, e zera
    // o cursor: sem isso o retângulo antigo (da grade atrás) continuava
    // desenhado por cima, e o pad cobria o vídeo no modo virtual.
    val setNavHidden = LocalHideNavForOverlay.current
    val setPadHidden = LocalHidePadForOverlay.current
    val cursor = LocalGamepadCursor.current
    LaunchedEffect(selected) {
        setNavHidden?.invoke(selected != null)
        setPadHidden?.invoke(selected != null)
        cursor?.clearAll()
    }
    DisposableEffect(Unit) {
        onDispose {
            setNavHidden?.invoke(false)
            setPadHidden?.invoke(false)
        }
    }
    // Posição da grade preservada ao abrir/fechar o viewer.
    val gridState = rememberLazyGridState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 20.dp, bottom = contentPadding.calculateBottomPadding())
        ) {
            val all = items
            val shown = remember(all, filter) {
                when (filter) {
                    GalleryFilter.ALL -> all.orEmpty()
                    GalleryFilter.PHOTOS -> all.orEmpty().filter { !it.isVideo }
                    GalleryFilter.VIDEOS -> all.orEmpty().filter { it.isVideo }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 76.dp, end = 32.dp, bottom = 4.dp)
            ) {
                Text(text = "Álbum", color = AeroTextPrimary, fontSize = 26.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (all == null) "" else "(${shown.size})",
                    color = AeroTextSecondary,
                    fontSize = 16.sp
                )
            }

            // Filtro estilo Switch (Y): Tudo / Fotos / Vídeos + pastas.
            if (all != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 76.dp, end = 32.dp, bottom = 12.dp)
                ) {
                    FilterPill(
                        label = "Tudo",
                        selected = filter == GalleryFilter.ALL,
                        onClick = { filter = GalleryFilter.ALL }
                    )
                    FilterPill(
                        label = "Fotos",
                        selected = filter == GalleryFilter.PHOTOS,
                        onClick = { filter = GalleryFilter.PHOTOS }
                    )
                    FilterPill(
                        label = "Vídeos",
                        selected = filter == GalleryFilter.VIDEOS,
                        onClick = { filter = GalleryFilter.VIDEOS }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    FilterPill(
                        label = if (folders.isEmpty()) "📁 Pastas" else "📁 ${folders.size}",
                        selected = false,
                        onClick = { showFolders = true }
                    )
                }
            }

            when {
                all == null -> {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                folders.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "O \u00e1lbum mostra s\u00f3 as suas pastas.\nAdicione a Game Media, a pasta do\nemulador ou do app de grava\u00e7\u00e3o.",
                            color = AeroTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = { showFolders = true },
                            modifier = Modifier.gamepadFocusable(
                                autoFocus = gamepadActive,
                                onConfirm = { showFolders = true }
                            )
                        ) { Text("Adicionar pasta") }
                    }
                }
                shown.isEmpty() -> {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Nada por aqui ainda.\nCapture algo no jogo que aparece aqui.",
                            color = AeroTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    // Com o viewer aberto a grade sai da composição: sem isso
                    // o D-pad continuava movendo o foco nos itens de trás (o
                    // contorno desenha por cima de tudo) e B/X caiam no vazio.
                    // O scroll é preservado pelo gridState ao voltar.
                    if (selected == null) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(140.dp),
                            state = gridState,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(start = 76.dp, end = 32.dp, bottom = 12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            itemsIndexed(shown, key = { _, it -> it.uri.toString() }) { index, item ->
                                GalleryCell(
                                    item = item,
                                    gamepadAutoFocus = gamepadActive && index == 0,
                                    onOpen = { selected = item },
                                    onDelete = { askDelete(item) },
                                    touchOk = touchOk
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Viewer em tela cheia.
        selected?.let { item ->
            GalleryViewer(
                item = item,
                onClose = { selected = null },
                onDelete = { askDelete(item) },
                modifier = Modifier.zIndex(50f)
            )
        }

        // Confirmação de exclusão (vale na grade e no viewer).
        pendingDelete?.let { item ->
            GamepadDialogScope {
                AlertDialog(
                    onDismissRequest = { pendingDelete = null },
                    title = { Text("Apagar?") },
                    text = { Text("Apagar ${item.displayName} da galeria?") },
                    confirmButton = {
                        TextButton(
                            onClick = { confirmDelete(item) },
                            modifier = Modifier.gamepadFocusable(
                                autoFocus = true,
                                onConfirm = { confirmDelete(item) }
                            )
                        ) { Text("Apagar") }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { pendingDelete = null },
                            modifier = Modifier.gamepadFocusable(
                                onConfirm = { pendingDelete = null },
                                onBack = { pendingDelete = null }
                            )
                        ) { Text("Cancelar") }
                    },
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }

        // Gerenciador de pastas dedicadas.
        if (showFolders) {
            MediaFoldersDialog(
                folders = folders,
                onAddGames = {
                    pendingFolderMode = false
                    folderPicker.launch(null)
                },
                onAddMixed = {
                    pendingFolderMode = true
                    folderPicker.launch(null)
                },
                onRemove = { folder ->
                    MediaFolderStore.remove(context, folder.uri)
                    folders = MediaFolderStore.load(context)
                    reloadTick++
                },
                onDismiss = { showFolders = false }
            )
        }
    }
}

/**
 * Gerenciador de pastas dedicadas: lista com modo (jogos/mista) e remoção,
 * mais os dois jeitos de adicionar. Pasta de jogos mostra tudo; pasta mista
 * filtra pelo nome de jogo/app da biblioteca (ex: "..._Brawl Stars.jpg").
 */
@Composable
private fun MediaFoldersDialog(
    folders: List<MediaFolder>,
    onAddGames: () -> Unit,
    onAddMixed: () -> Unit,
    onRemove: (MediaFolder) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    GamepadDialogScope {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Pastas da galeria") },
            text = {
                Column {
                    Text(
                        text = "Jogos mostra tudo da pasta. Mista mostra só arquivos com nome de jogo/app da sua biblioteca.",
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        if (folders.isEmpty()) {
                            item { Text("Nenhuma pasta ainda.", fontSize = 13.sp) }
                        } else {
                            items(folders, key = { it.uri }) { folder ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = MediaFolderStore.displayName(context, folder.uri),
                                            color = AeroTextPrimary,
                                            fontSize = 14.sp,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = if (folder.matchNamesOnly) "Mista (filtra por nome)" else "Jogos (tudo)",
                                            color = AeroTextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    TextButton(
                                        onClick = { onRemove(folder) },
                                        modifier = Modifier.gamepadFocusable(
                                            onConfirm = { onRemove(folder) }
                                        )
                                    ) { Text("✕") }
                                }
                            }
                        }
                    }
                    DialogGamepadBar()
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onAddGames,
                    modifier = Modifier.gamepadFocusable(
                        autoFocus = true,
                        onConfirm = onAddGames,
                        onBack = onDismiss
                    )
                ) { Text("+ Jogos") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = onAddMixed,
                        modifier = Modifier.gamepadFocusable(onConfirm = onAddMixed)
                    ) { Text("+ Mista") }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.gamepadFocusable(
                            onConfirm = onDismiss,
                            onBack = onDismiss
                        )
                    ) { Text("Fechar") }
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    /**
     * True = toque sempre vale (botões do viewer). False = respeita a trava
     * do modo gamepad (cards/filtros da grade, pra não disparar sem querer
     * com o pad na tela).
     */
    alwaysClickable: Boolean = false
) {
    val touchOk = LocalTouchActionsEnabled.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) AeroGlassWhiteStrong else AeroGlassWhite
            )
            .border(1.dp, AeroGlassBorder, RoundedCornerShape(50))
            .gamepadFocusable(onConfirm = onClick)
            .clickable { if (touchOk || alwaysClickable) onClick() }
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) AeroTextPrimary else AeroTextSecondary,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun GalleryCell(
    item: GalleryItem,
    gamepadAutoFocus: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    touchOk: Boolean
) {
    Box(
        modifier = Modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, AeroGlassBorder, RoundedCornerShape(10.dp))
            .gamepadFocusable(
                autoFocus = gamepadAutoFocus,
                onConfirm = onOpen,
                onSecondary = onDelete
            )
            .clickable { if (touchOk) onOpen() }
    ) {
        if (!item.isVideo) {
            AsyncImage(
                model = item.uri,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Thumb real via VideoFrameDecoder (frame de 1s: pula quadros
            // pretos de abertura). Se falhar, fica o fundo escuro + selos.
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(item.uri)
                    .videoFrameMicros(1_000_000)
                    .crossfade(true)
                    .build(),
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(text = "▶", color = Color.White, fontSize = 12.sp)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatDuration(item.durationMs),
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Viewer em tela cheia, em dois sabores:
 * - Pad VIRTUAL (dedo na tela): pad some, botões sempre visíveis, toque não
 *   fecha nada (sem perder o ponto do vídeo sem querer).
 * - Controle FÍSICO: cinema — botões e dicas somem, toque revela/esconde.
 * Nos dois, B volta e X apaga (com confirmação); ←/→ no vídeo viram seek.
 */
@Composable
private fun GalleryViewer(
    item: GalleryItem,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Pad visível = modo virtual (dedo). Apagado = controle físico.
    val padVisible = LocalVirtualPadVisible.current
    val cursor = LocalGamepadCursor.current
    // Físico começa no cinema (sem chrome); virtual sempre com botões.
    var chromeVisible by remember(item) { mutableStateOf(padVisible) }
    // Ref do player pro seek físico (o preview de tecla só desce até o item
    // focado — interceptar no Box do vídeo, filho do foco, nunca recebia).
    var videoView by remember(item) { mutableStateOf<VideoView?>(null) }
    // Estado do play içado: o A do controle alterna sem precisar de foco.
    var videoPlaying by remember(item) { mutableStateOf(true) }

    fun seekVideo(deltaMs: Int) {
        val view = videoView ?: return
        val total = runCatching { view.duration }.getOrDefault(0).takeIf { it > 0 } ?: return
        val pos = runCatching { view.currentPosition }.getOrDefault(0)
        runCatching { view.seekTo((pos + deltaMs).coerceIn(0, total)) }
    }

    fun toggleVideo() {
        val view = videoView
        if (view == null) {
            videoPlaying = !videoPlaying
            return
        }
        runCatching {
            if (view.isPlaying) {
                view.pause()
                videoPlaying = false
            } else {
                view.start()
                videoPlaying = true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .gamepadFocusable(
                // Virtual: sem autofoco (só toque nos botões, sem contorno).
                // Físico: foco no fundo pra B/X imediatos, sem contorno
                // fullscreen (announceCursor=false).
                autoFocus = !padVisible,
                onBack = onClose,
                onSecondary = onDelete,
                // A alterna play/pause no vídeo (foto: sem ação).
                onConfirm = if (item.isVideo) {
                    { toggleVideo() }
                } else {
                    null
                },
                announceCursor = false
            )
            // Físico: ←/→ dão seek no vídeo TEM que ser neste nível (o do
            // foco). Up/down passam pra navegação dos botões.
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                    return@onPreviewKeyEvent false
                }
                if (event.nativeKeyEvent.repeatCount > 0) {
                    return@onPreviewKeyEvent false
                }
                if (!item.isVideo) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        seekVideo(-10_000)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        seekVideo(10_000)
                        true
                    }
                    else -> false
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                // Só no físico o toque alterna o chrome (no virtual os
                // botões ficam e o toque neles não propaga pra cá).
                onClick = {
                    if (!padVisible) {
                        chromeVisible = !chromeVisible
                        if (!chromeVisible) cursor?.clearAll()
                    }
                }
            )
    ) {
        if (item.isVideo) {
            VideoPlayer(
                uri = item.uri,
                chromeVisible = chromeVisible,
                playing = videoPlaying,
                onToggle = { toggleVideo() },
                onViewCreated = { videoView = it },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = item.uri,
                contentDescription = item.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (chromeVisible) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 20.dp, end = 20.dp)
            ) {
                FilterPill(label = "‹ Voltar", selected = false, onClick = onClose, alwaysClickable = true)
                FilterPill(label = "🗑 Apagar", selected = false, onClick = onDelete, alwaysClickable = true)
            }
            Text(
                text = if (item.isVideo) "◀ ▶ ±10s • Ⓑ Voltar • Ⓧ Apagar" else "Ⓑ Voltar   Ⓧ Apagar",
                color = AeroTextSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
            )
        }
    }
}

@Composable
private fun VideoPlayer(
    uri: Uri,
    chromeVisible: Boolean,
    playing: Boolean,
    onToggle: () -> Unit,
    onViewCreated: (VideoView?) -> Unit,
    modifier: Modifier = Modifier
) {
    var viewRef by remember(uri) { mutableStateOf<VideoView?>(null) }
    var positionMs by remember(uri) { mutableStateOf(0) }
    var durationMs by remember(uri) { mutableStateOf(0) }
    val latestPlaying by rememberUpdatedState(playing)

    fun seekBy(deltaMs: Int) {
        val view = viewRef ?: return
        val total = view.duration.takeIf { it > 0 } ?: return
        view.seekTo((view.currentPosition + deltaMs).coerceIn(0, total))
    }

    // D-pad virtual: ←/→ sempre viram seek no vídeo (foco não mexe, vale em
    // qualquer modo). O físico é interceptado no onPreviewKeyEvent abaixo.
    // Up/down continuam navegando nos botões. Limpa o override ao sair.
    val dpadOverride = LocalDpadOverride.current
    LaunchedEffect(uri) {
        dpadOverride?.value = { dir ->
            when (dir) {
                FocusDirection.Left -> {
                    seekBy(-10_000)
                    true
                }
                FocusDirection.Right -> {
                    seekBy(10_000)
                    true
                }
                else -> false
            }
        }
    }

    // Relógio da posição (tempo decorrido / total).
    LaunchedEffect(uri) {
        while (true) {
            delay(500)
            positionMs = viewRef?.currentPosition ?: 0
            durationMs = viewRef?.duration ?: 0
        }
    }

    // Sem interceptação aqui de propósito: o preview de tecla só desce até
    // o item focado, e este Box é filho do container focado — nunca recebia
    // nada (era o bug do seek físico). O físico é tratado no container.
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    viewRef = this
                    onViewCreated(this)
                    setVideoURI(uri)
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        if (latestPlaying) start()
                    }
                }
            },
            update = { view ->
                if (playing) {
                    if (!view.isPlaying) view.start()
                } else {
                    view.pause()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (chromeVisible) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, AeroGlassBorder, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                TransportButton(label = "−10s", onPress = { seekBy(-10_000) })
                TransportButton(label = if (playing) "⏸" else "▶", onPress = onToggle)
                TransportButton(label = "+10s", onPress = { seekBy(10_000) })
                Text(
                    text = "${formatDuration(positionMs.toLong())} / ${formatDuration(durationMs.toLong())}",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                )
            }
        }
    }
    // Fechar o viewer TEM que soltar o player: o VideoView não se libera
    // sozinho ao sair da composição — o áudio fantasma segurava o foco e a
    // música de fundo nunca recebia o ganho de volta. stopPlayback libera o
    // MediaPlayer interno e onVideoClosed restabelece o foco do zero (cobre
    // o caso em que o evento de ganho se perde no caminho).
    DisposableEffect(uri) {
        onDispose {
            dpadOverride?.value = null
            runCatching { viewRef?.stopPlayback() }
            viewRef = null
            onViewCreated(null)
            AppAudio.onVideoClosed()
        }
    }
}

@Composable
private fun TransportButton(label: String, onPress: () -> Unit) {
    TextButton(
        onClick = onPress,
        modifier = Modifier.gamepadFocusable(onConfirm = onPress)
    ) { Text(label, fontSize = 14.sp, color = Color.White) }
}

/** "30s" / "1:05" — selo dos vídeos, como no Switch. */
private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).toInt().coerceAtLeast(0)
    return if (totalSec < 60) "${totalSec}s"
    else "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}"
}

/** Nomes da biblioteca (ROMs + apps) dobrados pra comparar com arquivos. */
private suspend fun loadLibraryNames(context: Context): Set<String> =
    withContext(Dispatchers.IO) {
        val names = HashSet<String>()
        val folderUri = RomFolderStore.load(context)
        val games = if (folderUri == null) {
            emptyList()
        } else {
            LauncherContentCache.getGames(folderUri) ?: run {
                val scanned = RomScanner.scan(context, folderUri)
                LauncherContentCache.putGames(folderUri, scanned)
                scanned
            }
        }
        val overrides = GameOverridesStore.load(context)
        for (game in games) {
            names += foldFileName(game.name)
            overrides[game.uri.toString()]?.customName?.let { names += foldFileName(it) }
        }
        for (app in AndroidGameStore.load(context)) {
            names += foldFileName(app.displayName)
        }
        names.filter { it.length >= 3 }.toSet()
    }

/** Minúsculo, sem acento, só letras/números separados por espaço. */
private fun foldFileName(name: String): String =
    name.lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

private val folderImageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
private val folderVideoExts = setOf("mp4", "mkv", "webm", "3gp", "mov", "avi")

/** Mídias das pastas dedicadas (SAF), do mais novo pro mais antigo. */
private fun loadFolderItems(
    context: Context,
    folders: List<MediaFolder>,
    libraryNames: Set<String>,
    sessions: List<PlaySession>
): List<GalleryItem> = runCatching {
    val out = ArrayList<GalleryItem>()
    for (folder in folders) {
        val treeUri = runCatching { Uri.parse(folder.uri) }.getOrNull() ?: continue
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: continue
        collectFolderMedia(context, root, folder.matchNamesOnly, libraryNames, sessions, out, depth = 0)
    }
    out.sortedByDescending { it.dateAddedSec }
}.getOrDefault(emptyList())

private fun collectFolderMedia(
    context: Context,
    dir: DocumentFile,
    matchNamesOnly: Boolean,
    libraryNames: Set<String>,
    sessions: List<PlaySession>,
    out: MutableList<GalleryItem>,
    depth: Int
) {
    // Teto de profundidade: não sai varrendo o aparelho inteiro sem querer.
    if (depth > 3) return
    val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
    for (child in children) {
        if (child.isDirectory) {
            collectFolderMedia(context, child, matchNamesOnly, libraryNames, sessions, out, depth + 1)
            continue
        }
        val name = child.name ?: continue
        val ext = name.substringAfterLast('.', "").lowercase()
        val isVideo = ext in folderVideoExts
        if (!isVideo && ext !in folderImageExts) continue
        val lastModified = runCatching { child.lastModified() }.getOrDefault(0L)
        // Pasta mista: entra se o nome citar um jogo/app da biblioteca
        // (ex: "Screenshot_20260901_134018_Brawl Stars.jpg") OU se foi
        // criado durante uma sessão de jogo (screenshot do sistema não tem
        // nome de jogo no arquivo — vale o horário).
        if (matchNamesOnly &&
            !matchesLibraryName(name, libraryNames) &&
            !takenDuringSession(lastModified, sessions)
        ) continue
        out += GalleryItem(
            uri = child.uri,
            isVideo = isVideo,
            dateAddedSec = (if (lastModified > 0L) lastModified else 0L) / 1000L,
            displayName = name,
            durationMs = if (isVideo) readVideoDuration(context, child.uri) else 0L
        )
    }
}

/**
 * O arquivo nasceu com algum jogo aberto? Folga de 30s após o fim (o save
 * do sistema pode atrasar um pouco em relação à volta ao app). Sem data
 * válida no arquivo, não atesta.
 */
private fun takenDuringSession(fileEpochMillis: Long, sessions: List<PlaySession>): Boolean {
    if (fileEpochMillis <= 0L || sessions.isEmpty()) return false
    return sessions.any { fileEpochMillis in it.startEpoch..it.endEpoch + 30_000L }
}

private fun matchesLibraryName(fileName: String, libraryNames: Set<String>): Boolean {
    if (libraryNames.isEmpty()) return false
    val folded = foldFileName(fileName.substringBeforeLast('.'))
    if (folded.isEmpty()) return false
    return libraryNames.any { it in folded }
}

private fun readVideoDuration(context: Context, uri: Uri): Long = runCatching {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } finally {
        runCatching { retriever.release() }
    }
}.getOrDefault(0L)
