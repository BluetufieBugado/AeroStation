package com.btbugado.aerostation.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.btbugado.aerostation.data.ControlMode
import com.btbugado.aerostation.data.VirtualPadLayout
import com.btbugado.aerostation.ui.theme.AeroGlassBorder
import com.btbugado.aerostation.ui.theme.AeroGlassWhite
import com.btbugado.aerostation.ui.theme.AeroGlassWhiteStrong
import com.btbugado.aerostation.ui.theme.AeroTextPrimary
import com.btbugado.aerostation.ui.theme.AeroTextSecondary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Barramento de eventos do controle virtual (gamepad na tela).
 *
 * O D-pad físico move o foco via KeyEvents do Android; o pad virtual não
 * gera KeyEvents, então publica aqui: o [VirtualDpadFocusHandler] (um por
 * janela — tela principal e cada dialog) move o foco da sua janela, e cada
 * item com [gamepadFocusable] observa os nonces de A/B/X e dispara o
 * callback correspondente quando está focado.
 */
class VirtualGamepadEvents {
    var dpadNonce by mutableStateOf(0)
        private set
    var dpadDirection by mutableStateOf<FocusDirection?>(null)
        private set

    var confirmNonce by mutableStateOf(0)
        private set
    var backNonce by mutableStateOf(0)
        private set
    var secondaryNonce by mutableStateOf(0)
        private set

    fun pressDpad(direction: FocusDirection) {
        dpadDirection = direction
        dpadNonce++
    }

    fun pressConfirm() {
        confirmNonce++
    }

    fun pressBack() {
        backNonce++
    }

    fun pressSecondary() {
        secondaryNonce++
    }
}

val LocalVirtualGamepadEvents = compositionLocalOf<VirtualGamepadEvents?> { null }

/**
 * Override do D-pad virtual: quando presente e devolve true pra uma direção,
 * o [VirtualDpadFocusHandler] consome sem mover o foco (ex: seek ±10s no
 * viewer de vídeo da galeria). Estado compartilhado, provido na raiz.
 */
val LocalDpadOverride =
    compositionLocalOf<MutableState<((FocusDirection) -> Boolean)?>?> { null }

/**
 * Se o controle virtual deve ser desenhado (só faz sentido no modo GAMEPAD).
 * Fica false enquanto o controle FÍSICO está em uso e volta a true ao
 * tocar na tela (sinal de que o usuário quer o pad virtual de volta).
 */
val LocalVirtualPadVisible = compositionLocalOf { true }

/**
 * Esconde o pad virtual temporariamente (ex: viewer de mídia em tela cheia
 * no modo virtual — sobram os botões da mídia; volta ao sair).
 */
val LocalHidePadForOverlay = compositionLocalOf<((Boolean) -> Unit)?> { null }

/** Modo de entrada atual (touch x controle), escolhido em Configurações > Geral. */
val LocalControlMode = compositionLocalOf { ControlMode.TOUCH }

/**
 * Callback para avisar que houve input de controle (físico ou virtual).
 * O app usa para sair do estado "navegando com o dedo" e mostrar o cursor.
 */
val LocalOnGamepadUsed = compositionLocalOf<() -> Unit> { {} }

@Composable
fun rememberVirtualGamepadEvents(): VirtualGamepadEvents = remember { VirtualGamepadEvents() }

/**
 * Escuta os D-pad do pad virtual e move o foco da janela atual.
 *
 * Precisa existir UM por janela: a tela principal tem o dela e cada
 * [GamepadDialogScope] tem o seu (dialogs são outra janela com outro
 * FocusManager — o da tela principal não alcança os itens do dialog).
 */
@Composable
fun VirtualDpadFocusHandler() {
    val events = LocalVirtualGamepadEvents.current ?: return
    val focusManager = LocalFocusManager.current
    val cursor = LocalGamepadCursor.current
    val latestOverride by rememberUpdatedState(LocalDpadOverride.current?.value)
    val nonce = events.dpadNonce
    LaunchedEffect(nonce) {
        if (nonce == 0) return@LaunchedEffect
        val direction = events.dpadDirection ?: return@LaunchedEffect
        // Override ativo (ex: viewer de vídeo): consome sem mover o foco.
        if (latestOverride?.invoke(direction) == true) return@LaunchedEffect
        // moveFocus joga IllegalStateException se a busca de foco atravessar
        // um FocusRequester desanexado (ex: links explícitos nextUp/nextDown
        // de uma composição descartada na volta do emulador). Era o crash do
        // log — e só o controle move o foco, por isso só ele crashava.
        val moved = runCatching { focusManager.moveFocus(direction) }.getOrDefault(false)
        if (!moved) {
            // Foco podre: solta tudo pra próxima tentativa fazer uma busca
            // inicial limpa em vez de atravessar o lixo de novo.
            runCatching {
                cursor?.clearAll()
                focusManager.clearFocus(force = true)
            }
        }
    }
}

/**
 * Botão redondo de vidro do controle virtual. Dispara no pressionar
 * (igual controle físico), não no soltar. Direções do D-pad repetem
 * enquanto seguradas.
 */
@Composable
private fun PadButton(
    label: String,
    onDown: () -> Unit,
    size: Dp = 54.dp,
    repeat: Boolean = false,
    labelColor: Color = Color.White,
    fontSize: Int = 18,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "pad-press")
    val currentOnDown by rememberUpdatedState(onDown)

    // D-pad segurado repete o movimento (igual controle físico).
    if (repeat) {
        LaunchedEffect(pressed) {
            if (!pressed) return@LaunchedEffect
            delay(400)
            while (pressed) {
                currentOnDown()
                delay(110)
            }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(AeroGlassWhiteStrong, AeroGlassWhite)))
            .border(1.dp, AeroGlassBorder, CircleShape)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Consome o toque: o detector de "navegando com o dedo"
                    // da raiz usa requireUnconsumed=true, então toques no
                    // próprio pad não escondem o cursor — só mostram
                    // (via onDown -> onGamepadUsed).
                    down.consume()
                    pressed = true
                    currentOnDown()
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
    ) {
        Text(
            text = label,
            color = labelColor,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ShoulderButton(label: String, onDown: () -> Unit, modifier: Modifier = Modifier) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "shoulder-press")
    val currentOnDown by rememberUpdatedState(onDown)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = 62.dp, height = 38.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(19.dp))
            .background(Brush.linearGradient(listOf(AeroGlassWhiteStrong, AeroGlassWhite)))
            .border(1.dp, AeroGlassBorder, RoundedCornerShape(19.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Mesmo motivo do PadButton: L1/R1 não podem ser
                    // confundidos com "toque de dedo" que esconde o cursor.
                    down.consume()
                    pressed = true
                    currentOnDown()
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
    ) {
        Text(text = label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Limita (base + delta) para dentro da tela.
 */
private fun placeInBounds(
    baseX: Dp,
    baseY: Dp,
    dx: Dp,
    dy: Dp,
    w: Dp,
    h: Dp,
    maxW: Dp,
    maxH: Dp
): DpOffset {
    val x = (baseX + dx).coerceIn(0.dp, (maxW - w).coerceAtLeast(0.dp))
    val y = (baseY + dy).coerceIn(0.dp, (maxH - h).coerceAtLeast(0.dp))
    return DpOffset(x, y)
}

/** Posiciona um grupo do controle numa coordenada absoluta da tela. */
@Composable
private fun Positioned(pos: DpOffset, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier.offset {
            with(density) {
                IntOffset(pos.x.toPx().roundToInt(), pos.y.toPx().roundToInt())
            }
        }
    ) {
        content()
    }
}

/** Cruz do D-pad (3x o tamanho do botão). Ações via callbacks. */
@Composable
private fun DpadCluster(
    buttonSize: Dp,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(buttonSize * 3)) {
        PadButton(
            label = "▲",
            size = buttonSize,
            repeat = true,
            onDown = onUp,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        PadButton(
            label = "◀",
            size = buttonSize,
            repeat = true,
            onDown = onLeft,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        PadButton(
            label = "▶",
            size = buttonSize,
            repeat = true,
            onDown = onRight,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
        PadButton(
            label = "▼",
            size = buttonSize,
            repeat = true,
            onDown = onDown,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** Losango A/B/X/Y (3x o tamanho do botão). */
@Composable
private fun ActionsCluster(
    buttonSize: Dp,
    onA: () -> Unit,
    onB: () -> Unit,
    onX: () -> Unit,
    onY: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(buttonSize * 3)) {
        PadButton(
            label = "Y",
            size = buttonSize,
            labelColor = Color(0xFFFFD54F),
            onDown = onY,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        PadButton(
            label = "X",
            size = buttonSize,
            labelColor = Color(0xFF6BB8FF),
            onDown = onX,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        PadButton(
            label = "B",
            size = buttonSize,
            labelColor = Color(0xFFFF7A7A),
            onDown = onB,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
        PadButton(
            label = "A",
            size = buttonSize,
            labelColor = Color(0xFF9BE27A),
            onDown = onA,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * Controle virtual de vidro sobre a tela principal: D-pad à esquerda,
 * botões A/B/X/Y à direita (layout Xbox) e L1/R1 nos cantos superiores
 * (trocam de aba, igual no controle físico).
 *
 * Posições/tamanho vêm de [layout] (editável em Configurações > Geral >
 * "Editar controles na tela").
 *
 * O container não intercepta toques — só os botões consomem. É touch-only
 * de propósito: não entra na árvore de foco do D-pad.
 *
 * Quem decide SE desenha é o [RetroAeroApp]: no modo GAMEPAD o pad some
 * enquanto o controle físico está em uso e volta ao tocar na tela.
 */
@Composable
fun VirtualGamepadOverlay(
    onTabPrev: () -> Unit,
    onTabNext: () -> Unit,
    layout: VirtualPadLayout = VirtualPadLayout(),
    modifier: Modifier = Modifier
) {
    val events = LocalVirtualGamepadEvents.current ?: return
    val onGamepadUsed = LocalOnGamepadUsed.current

    fun dpad(dir: FocusDirection): () -> Unit = {
        onGamepadUsed()
        events.pressDpad(dir)
    }
    fun action(press: () -> Unit): () -> Unit = {
        onGamepadUsed()
        press()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val s = layout.safeScale
        val btn = 54.dp * s
        val cluster = btn * 3
        val shoulderW = 62.dp * s
        val shoulderH = 38.dp * s

        val l1Pos = placeInBounds(
            baseX = 16.dp, baseY = 88.dp,
            dx = layout.l1Dx.dp, dy = layout.l1Dy.dp,
            w = shoulderW, h = shoulderH, maxW = maxWidth, maxH = maxHeight
        )
        val r1Pos = placeInBounds(
            baseX = maxWidth - 16.dp - shoulderW, baseY = 88.dp,
            dx = layout.r1Dx.dp, dy = layout.r1Dy.dp,
            w = shoulderW, h = shoulderH, maxW = maxWidth, maxH = maxHeight
        )
        val dpadPos = placeInBounds(
            baseX = 18.dp, baseY = maxHeight - 118.dp - cluster,
            dx = layout.dpadDx.dp, dy = layout.dpadDy.dp,
            w = cluster, h = cluster, maxW = maxWidth, maxH = maxHeight
        )
        val actionsPos = placeInBounds(
            baseX = maxWidth - 18.dp - cluster, baseY = maxHeight - 118.dp - cluster,
            dx = layout.actionsDx.dp, dy = layout.actionsDy.dp,
            w = cluster, h = cluster, maxW = maxWidth, maxH = maxHeight
        )

        Positioned(pos = l1Pos) {
            ShoulderButton(
                label = "L1",
                onDown = { onGamepadUsed(); onTabPrev() }
            )
        }
        Positioned(pos = r1Pos) {
            ShoulderButton(
                label = "R1",
                onDown = { onGamepadUsed(); onTabNext() }
            )
        }
        Positioned(pos = dpadPos) {
            DpadCluster(
                buttonSize = btn,
                onUp = dpad(FocusDirection.Up),
                onDown = dpad(FocusDirection.Down),
                onLeft = dpad(FocusDirection.Left),
                onRight = dpad(FocusDirection.Right)
            )
        }
        Positioned(pos = actionsPos) {
            ActionsCluster(
                buttonSize = btn,
                onA = action(events::pressConfirm),
                onB = action(events::pressBack),
                onX = action(events::pressSecondary),
                onY = action(events::pressSecondary)
            )
        }
    }
}

/**
 * Barra compacta de navegação para dentro dos menus flutuantes.
 *
 * Dialogs são outra janela: o [VirtualGamepadOverlay] da tela principal
 * fica atrás deles e não recebe toque. Esta barra vai DENTRO do dialog
 * (na área de botões) e dirige os mesmos [VirtualGamepadEvents].
 * Só aparece no modo controle.
 */
@Composable
fun DialogGamepadBar(modifier: Modifier = Modifier) {
    if (LocalControlMode.current != ControlMode.GAMEPAD) return
    if (!LocalVirtualPadVisible.current) return
    val events = LocalVirtualGamepadEvents.current ?: return
    val onGamepadUsed = LocalOnGamepadUsed.current

    fun dpad(dir: FocusDirection): () -> Unit = {
        onGamepadUsed()
        events.pressDpad(dir)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
            PadButton(label = "◀", size = 40.dp, fontSize = 14, repeat = true, onDown = dpad(FocusDirection.Left))
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(6.dp))
            PadButton(label = "▲", size = 40.dp, fontSize = 14, repeat = true, onDown = dpad(FocusDirection.Up))
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(6.dp))
            PadButton(label = "▼", size = 40.dp, fontSize = 14, repeat = true, onDown = dpad(FocusDirection.Down))
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(6.dp))
            PadButton(label = "▶", size = 40.dp, fontSize = 14, repeat = true, onDown = dpad(FocusDirection.Right))
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(12.dp))
            PadButton(
                label = "A",
                size = 40.dp,
                fontSize = 14,
                labelColor = Color(0xFF9BE27A),
                onDown = { onGamepadUsed(); events.pressConfirm() }
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(6.dp))
            PadButton(
                label = "B",
                size = 40.dp,
                fontSize = 14,
                labelColor = Color(0xFFFF7A7A),
                onDown = { onGamepadUsed(); events.pressBack() }
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/** Grupo arrastável no editor de layout (só move, não dispara ações). */
@Composable
private fun Draggable(
    pos: DpOffset,
    onMove: (dxDp: Float, dyDp: Float) -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset {
                with(density) {
                    IntOffset(pos.x.toPx().roundToInt(), pos.y.toPx().roundToInt())
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    with(density) {
                        onMove(dragAmount.x.toDp().value, dragAmount.y.toDp().value)
                    }
                }
            }
    ) {
        content()
    }
}

/**
 * Editor visual do controle virtual: tela cheia por cima do app, com os 4
 * grupos (D-pad, botões, L1, R1) arrastáveis com o dedo + controle de
 * tamanho. Salvar persiste em [com.btbugado.aerostation.data.VirtualPadLayoutStore].
 *
 * É uma ferramenta touch de propósito (arrastar exige o dedo); os botões de
 * ação também respondem ao controle físico via [gamepadFocusable].
 */
@Composable
fun VirtualPadLayoutEditor(
    initial: VirtualPadLayout,
    onSave: (VirtualPadLayout) -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dpadDx by remember { mutableStateOf(initial.dpadDx) }
    var dpadDy by remember { mutableStateOf(initial.dpadDy) }
    var actionsDx by remember { mutableStateOf(initial.actionsDx) }
    var actionsDy by remember { mutableStateOf(initial.actionsDy) }
    var l1Dx by remember { mutableStateOf(initial.l1Dx) }
    var l1Dy by remember { mutableStateOf(initial.l1Dy) }
    var r1Dx by remember { mutableStateOf(initial.r1Dx) }
    var r1Dy by remember { mutableStateOf(initial.r1Dy) }
    var scale by remember { mutableStateOf(initial.safeScale) }

    fun current() = VirtualPadLayout(
        dpadDx = dpadDx, dpadDy = dpadDy,
        actionsDx = actionsDx, actionsDy = actionsDy,
        l1Dx = l1Dx, l1Dy = l1Dy,
        r1Dx = r1Dx, r1Dy = r1Dy,
        scale = scale
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
    ) {
        val s = scale.coerceIn(VirtualPadLayout.MIN_SCALE, VirtualPadLayout.MAX_SCALE)
        val btn = 54.dp * s
        val cluster = btn * 3
        val shoulderW = 62.dp * s
        val shoulderH = 38.dp * s

        val noop: () -> Unit = {}

        Draggable(
            pos = placeInBounds(
                baseX = 16.dp, baseY = 88.dp,
                dx = l1Dx.dp, dy = l1Dy.dp,
                w = shoulderW, h = shoulderH, maxW = maxWidth, maxH = maxHeight
            ),
            onMove = { dx, dy -> l1Dx += dx; l1Dy += dy }
        ) {
            ShoulderButton(label = "L1", onDown = noop)
        }
        Draggable(
            pos = placeInBounds(
                baseX = maxWidth - 16.dp - shoulderW, baseY = 88.dp,
                dx = r1Dx.dp, dy = r1Dy.dp,
                w = shoulderW, h = shoulderH, maxW = maxWidth, maxH = maxHeight
            ),
            onMove = { dx, dy -> r1Dx += dx; r1Dy += dy }
        ) {
            ShoulderButton(label = "R1", onDown = noop)
        }
        Draggable(
            pos = placeInBounds(
                baseX = 18.dp, baseY = maxHeight - 118.dp - cluster,
                dx = dpadDx.dp, dy = dpadDy.dp,
                w = cluster, h = cluster, maxW = maxWidth, maxH = maxHeight
            ),
            onMove = { dx, dy -> dpadDx += dx; dpadDy += dy }
        ) {
            DpadCluster(buttonSize = btn, onUp = noop, onDown = noop, onLeft = noop, onRight = noop)
        }
        Draggable(
            pos = placeInBounds(
                baseX = maxWidth - 18.dp - cluster, baseY = maxHeight - 118.dp - cluster,
                dx = actionsDx.dp, dy = actionsDy.dp,
                w = cluster, h = cluster, maxW = maxWidth, maxH = maxHeight
            ),
            onMove = { dx, dy -> actionsDx += dx; actionsDy += dy }
        ) {
            ActionsCluster(buttonSize = btn, onA = noop, onB = noop, onX = noop, onY = noop)
        }

        // Barra superior: título + ações.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp, start = 20.dp, end = 20.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(AeroGlassWhiteStrong, AeroGlassWhite)))
                .border(1.dp, AeroGlassBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Editar controles",
                color = AeroTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            Text(
                text = "Arraste cada grupo com o dedo.",
                color = AeroTextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onSave(current()) },
                    modifier = Modifier.gamepadFocusable(
                        autoFocus = true,
                        onConfirm = { onSave(current()) }
                    )
                ) { Text("Salvar") }
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.gamepadFocusable(onConfirm = onCancel)
                ) { Text("Cancelar") }
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.gamepadFocusable(onConfirm = onReset)
                ) { Text("Padrão") }
            }
        }

        // Barra inferior: tamanho.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp, start = 20.dp, end = 20.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(AeroGlassWhiteStrong, AeroGlassWhite)))
                .border(1.dp, AeroGlassBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Tamanho dos botões",
                color = AeroTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        scale = (scale - 0.05f).coerceIn(
                            VirtualPadLayout.MIN_SCALE,
                            VirtualPadLayout.MAX_SCALE
                        )
                    },
                    modifier = Modifier.gamepadFocusable(onConfirm = {
                        scale = (scale - 0.05f).coerceIn(
                            VirtualPadLayout.MIN_SCALE,
                            VirtualPadLayout.MAX_SCALE
                        )
                    })
                ) { Text("−", fontSize = 18.sp) }
                Slider(
                    value = scale,
                    onValueChange = { scale = it },
                    valueRange = VirtualPadLayout.MIN_SCALE..VirtualPadLayout.MAX_SCALE,
                    modifier = Modifier.width(180.dp)
                )
                TextButton(
                    onClick = {
                        scale = (scale + 0.05f).coerceIn(
                            VirtualPadLayout.MIN_SCALE,
                            VirtualPadLayout.MAX_SCALE
                        )
                    },
                    modifier = Modifier.gamepadFocusable(onConfirm = {
                        scale = (scale + 0.05f).coerceIn(
                            VirtualPadLayout.MIN_SCALE,
                            VirtualPadLayout.MAX_SCALE
                        )
                    })
                ) { Text("+", fontSize = 18.sp) }
            }
        }
    }
}
