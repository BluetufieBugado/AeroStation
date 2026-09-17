package com.btbugado.aerostation.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.animateIntSizeAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import com.btbugado.aerostation.data.AppAudio
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import android.view.KeyEvent
import kotlinx.coroutines.launch

class GamepadCursorState {
    var rect by mutableStateOf<Rect?>(null)
        private set
    private var focusedToken: Any? = null

    fun update(token: Any, coordinates: LayoutCoordinates) {
        if (!coordinates.isAttached) return
        // localToRoot pode jogar IllegalStateException se o nó desanexar
        // na corrida (ex: volta do emulador descartando a composição).
        // Sem o guard, o app crasha — e só no modo controle.
        val bounds = runCatching {
            val position = coordinates.localToRoot(Offset.Zero)
            Rect(position, Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
        }.getOrNull() ?: return
        focusedToken = token
        rect = bounds
    }

    fun clear(token: Any) {
        if (focusedToken === token) {
            focusedToken = null
            rect = null
        }
    }

    fun clearAll() {
        focusedToken = null
        rect = null
    }
}

val LocalGamepadCursor = compositionLocalOf<GamepadCursorState?> { null }

val LocalGamepadEnabled = compositionLocalOf { true }

/**
 * Quando true, o [gamepadFocusable] desenha o cursor localmente no próprio
 * item focado em vez de publicar para o [GamepadCursorOverlay] da tela.
 *
 * Isso existe por causa dos menus flutuantes (AlertDialog/Dialog): eles são
 * renderizados numa janela separada, acima da janela principal. O overlay
 * central do [RetroAeroApp][com.btbugado.aerostation.ui.RetroAeroApp] fica na
 * janela de baixo, então fica invisível por trás do dialog — e as
 * coordenadas `localToRoot` do dialog nem fariam sentido na janela principal.
 * Dentro de dialogs usamos o anel branco local, que é independente de janela.
 */
val LocalGamepadInDialog = compositionLocalOf { false }

/**
 * Controla se o cursor do controle deve ser desenhado.
 *
 * - Modo touch (sem controle físico): false — cursor some.
 * - Modo controle ou controle físico conectado: true, exceto logo depois
 *   de um toque na tela (sinal de que o usuário passou a navegar com o
 *   dedo), quando esconde até o próximo input de controle.
 * - Com controle físico conectado o toque NÃO esconde (cursor sempre
 *   visível), então essa regra só vale para o caso do controle virtual.
 */
val LocalGamepadCursorVisible = compositionLocalOf { true }

/**
 * Quando false (modo controle virtual ativo), toques em botões/cartões NÃO
 * disparam ações — evita inicializações acidentais de jogos/apps ao apertar
 * os botões do controle na tela. O controle (virtual ou físico) continua
 * ativando tudo normalmente via foco.
 *
 * Não afeta: rolagem, campos de texto, dialogs e o próprio pad/editor.
 */
val LocalTouchActionsEnabled = compositionLocalOf { true }

@Composable
fun rememberGamepadCursorState(): GamepadCursorState = remember { GamepadCursorState() }

/**
 * Escopo para conteúdo de menus flutuantes (AlertDialog/Dialog).
 *
 * Isola o cursor: cria um [GamepadCursorState] próprio para o dialog (para
 * que o foco do dialog não polua/move o overlay da tela principal com
 * coordenadas da janela errada) e ativa o modo [LocalGamepadInDialog], onde
 * cada item focado desenha seu próprio anel de cursor localmente.
 *
 * Uso: envolva o `AlertDialog` inteiro com este escopo.
 */
@Composable
fun GamepadDialogScope(content: @Composable () -> Unit) {
    val dialogCursor = rememberGamepadCursorState()
    // Som de abrir ao montar e de fechar ao sair — vale pra todo dialog
    // (menu do jogo, renomear, seletores), em qualquer modo de entrada.
    LaunchedEffect(Unit) { AppAudio.playOpen() }
    DisposableEffect(Unit) { onDispose { AppAudio.playClose() } }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalGamepadCursor provides dialogCursor,
        LocalGamepadInDialog provides true
    ) {
        // Cada dialog tem seu próprio FocusManager: o handler da tela
        // principal não alcança os itens daqui, então o D-pad virtual
        // precisa de um handler nesta janela também.
        VirtualDpadFocusHandler()
        content()
    }
}

/**
 * Alvo de foco físico. O Android/Compose continua cuidando da navegação do
 * D-pad; este modificador apenas publica o retângulo do foco para que exista
 * UM cursor visual centralizado por cima da interface inteira.
 *
 * Dentro de [GamepadDialogScope] (menus flutuantes/Dialogs), em vez de
 * publicar para o overlay da tela principal (que fica numa janela separada,
 * atrás do dialog), desenha o anel branco diretamente no item focado — assim
 * o cursor fica visível em qualquer janela.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.gamepadFocusable(
    autoFocus: Boolean = false,
    onConfirm: (() -> Unit)? = null,
    onSecondary: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    nextUp: FocusRequester? = null,
    nextDown: FocusRequester? = null,
    nextLeft: FocusRequester? = null,
    nextRight: FocusRequester? = null,
    focusRequester: FocusRequester? = null,
    onFocusGained: (() -> Unit)? = null,
    /**
     * False = recebe foco e teclas normalmente, mas não publica o retângulo
     * no cursor (ex: container fullscreen do viewer — o contorno do tamanho
     * da tela não faz sentido).
     */
    announceCursor: Boolean = true
): Modifier {
    val gamepadEnabled = LocalGamepadEnabled.current
    val inDialog = LocalGamepadInDialog.current
    val cursorVisible = LocalGamepadCursorVisible.current
    val virtualEvents = LocalVirtualGamepadEvents.current
    val internalRequester = remember { FocusRequester() }
    // Permite que a tela passe um FocusRequester externo para criar
    // ligações explícitas de foco (ex: botão do topo -> primeiro jogo).
    // Sem isso a busca automática 2D do Compose pode pular fileiras
    // (foi o bug da Home: do avatar/refresh o Down ia direto pra
    // BottomNavBar, pulando o LazyRow de jogos).
    val requester = focusRequester ?: internalRequester
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val cursor = LocalGamepadCursor.current
    val cursorAnnouncer = if (announceCursor) cursor else null
    val token = remember { Any() }
    var focused by remember { mutableStateOf(false) }
    var lastCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    LaunchedEffect(autoFocus, gamepadEnabled) {
        if (gamepadEnabled && autoFocus) {
            // requestFocus joga IllegalStateException se o FocusRequester
            // ainda não está anexado a um nó (ex: volta do emulador com o
            // processo recriado, item do LazyRow fora da composição). Sem o
            // guard o app crasha — e só no modo controle, o único com foco.
            runCatching { requester.requestFocus() }
        }
        if (!gamepadEnabled && focused) cursor?.clear(token)
    }

    // Botões A/B/X do controle VIRTUAL: não geram KeyEvents, então cada
    // item focado observa o barramento e dispara seu próprio callback.
    val latestFocused by rememberUpdatedState(focused)
    val latestConfirm by rememberUpdatedState(onConfirm)
    val latestBack by rememberUpdatedState(onBack)
    val latestSecondary by rememberUpdatedState(onSecondary)
    val latestFocusGained by rememberUpdatedState(onFocusGained)
    val confirmTick = virtualEvents?.confirmNonce ?: 0
    val backTick = virtualEvents?.backNonce ?: 0
    val secondaryTick = virtualEvents?.secondaryNonce ?: 0
    LaunchedEffect(confirmTick) {
        if (confirmTick != 0 && latestFocused) {
            latestConfirm?.let { AppAudio.playConfirm(); it() }
        }
    }
    LaunchedEffect(backTick) {
        if (backTick != 0 && latestFocused) latestBack?.invoke()
    }
    LaunchedEffect(secondaryTick) {
        if (secondaryTick != 0 && latestFocused) latestSecondary?.invoke()
    }

    return this
        .focusRequester(requester)
        .bringIntoViewRequester(bringIntoView)
        .focusProperties {
            canFocus = gamepadEnabled
            // Ligações explícitas têm prioridade sobre a busca automática.
            // Só sobrescreve quando um requester foi passado.
            nextUp?.let { up = it }
            nextDown?.let { down = it }
            nextLeft?.let { left = it }
            nextRight?.let { right = it }
        }
        // IMPORTANTE: onGloballyPositioned, onFocusChanged e onPreviewKeyEvent
        // precisam vir ANTES de .focusable() na cadeia de modifiers. No Compose,
        // esses modifiers só "escutam"/"interceptam" um alvo de foco que esteja
        // depois deles na cadeia (ou seja, eles precisam ser "pais" do
        // .focusable()). Colocá-los depois faz com que nunca recebam eventos
        // daquele focusable específico — foi por isso que a navegação por
        // D-pad continuava funcionando (o Compose already cuida disso
        // internamente), mas o cursor nunca aparecia: `focused` nunca virava
        // true e `cursor.rect` nunca era preenchido.
        .onGloballyPositioned { coordinates ->
            lastCoordinates = coordinates
            if (focused) cursorAnnouncer?.update(token, coordinates)
        }
        .onFocusChanged { state ->
            focused = gamepadEnabled && state.isFocused
            if (state.isFocused && gamepadEnabled) {
                // O layout normalmente já existe quando o foco muda. Usamos
                // as últimas coordenadas conhecidas para atualizar o cursor
                // imediatamente, sem depender de um novo layout.
                // Guardado por gamepadEnabled: no modo touch (sem controle)
                // nenhum foco pode publicar retângulo — sem isso o contorno
                // branco vazava mesmo com o cursor "invisível".
                lastCoordinates?.let { coordinates ->
                    cursorAnnouncer?.update(token, coordinates)
                }
                // Chegou num botão novo pelo controle: blip de navegação.
                AppAudio.playMove()
                // Hook opcional (ex: primeiro card recentraliza a fileira).
                latestFocusGained?.invoke()
                // Traz o item pra área visível quando ele está dentro de um
                // container rolável (ex: opções de Configurações). Sem isso o
                // cursor consegue focar itens fora da tela e o usuário não vê
                // onde está — nem rolando com o dedo ele alcançava, porque a
                // aba "Geral" nem era rolável. Em container sem rolagem é no-op.
                scope.launch { runCatching { bringIntoView.bringIntoView() } }
            } else {
                cursorAnnouncer?.clear(token)
            }
        }
        .drawWithContent {
            drawContent()
            // Nos dialogs o overlay central não é visível (outra janela),
            // então o cursor é o anel local. Na tela principal o overlay
            // animado já faz esse papel, então não desenhamos em duplicidade.
            // Em ambos os casos respeita a visibilidade (modo touch / toque).
            if (inDialog && focused && cursorVisible) {
                val inset = 1.5.dp.toPx()
                val stroke = 3.dp.toPx()
                val radius = 18.dp.toPx()
                drawRoundRect(
                    color = Color.White,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(
                        width = (size.width - inset * 2f).coerceAtLeast(1f),
                        height = (size.height - inset * 2f).coerceAtLeast(1f)
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                    style = Stroke(width = stroke)
                )
            }
        }
        .onPreviewKeyEvent { event ->
            if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
            // Botão SEGURADO gera ACTION_DOWN repetidos (repeatCount > 0):
            // sem isso, segurar o A confirma/abre o jogo várias vezes e
            // segurar o B dispara o voltar em cascata. Devolve false pra
            // navegação do D-pad continuar repetindo normalmente via Compose.
            if (event.nativeKeyEvent.repeatCount > 0) return@onPreviewKeyEvent false
            when (event.nativeKeyEvent.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    onConfirm?.let { AppAudio.playConfirm(); it() }
                    onConfirm != null
                }
                KeyEvent.KEYCODE_BUTTON_X,
                KeyEvent.KEYCODE_BUTTON_Y -> {
                    onSecondary?.invoke()
                    onSecondary != null
                }
                KeyEvent.KEYCODE_BUTTON_B,
                KeyEvent.KEYCODE_BACK -> {
                    onBack?.invoke()
                    onBack != null
                }
                else -> false
            }
        }
        // Desligado no modo touch sem controle: o item nem entra na
        // árvore de foco, então nenhum toque/teclado deixa um foco
        // preso que desenharia o contorno branco.
        .focusable(enabled = gamepadEnabled)
}

@Composable
fun GamepadCursorOverlay(modifier: Modifier = Modifier) {
    if (!LocalGamepadCursorVisible.current) return
    val cursor = LocalGamepadCursor.current ?: return
    val rect = cursor.rect ?: return

    val targetOffset = IntOffset(rect.left.toInt(), rect.top.toInt())
    val targetSize = IntSize(rect.width.toInt().coerceAtLeast(1), rect.height.toInt().coerceAtLeast(1))
    val animatedOffset by animateIntOffsetAsState(
        targetValue = targetOffset,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "gamepad-cursor-position"
    )
    val animatedSize by animateIntSizeAsState(
        targetValue = targetSize,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "gamepad-cursor-size"
    )
    val pulse by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(220),
        label = "gamepad-cursor-pulse"
    )

    val density = LocalDensity.current
    val offsetX = with(density) { animatedOffset.x.toDp() }
    val offsetY = with(density) { animatedOffset.y.toDp() }

    Canvas(
        modifier = modifier
            .offset(offsetX, offsetY)
            .size(with(density) { animatedSize.width.toDp() }, with(density) { animatedSize.height.toDp() })
    ) {
        // O overlay é desenhado em pixels; a posição acima é convertida de
        // forma aproximada para dp pelo density implícito do Canvas. Como o
        // Box pai está em coordenadas de dp, o cursor ocupa o mesmo espaço.
        val w = animatedSize.width.toFloat().coerceAtLeast(1f)
        val h = animatedSize.height.toFloat().coerceAtLeast(1f)
        val radius = 18.dp.toPx()
        val stroke = 3.dp.toPx()

        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.24f),
                    Color(0xFFBEEFFF).copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.20f)
                )
            ),
            topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
            size = Size(w - 2.dp.toPx(), h - 2.dp.toPx()),
            cornerRadius = CornerRadius(radius, radius)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.92f * pulse),
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(w - stroke, h - stroke),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = stroke)
        )
        drawRoundRect(
            color = Color(0xFFBDEBFF).copy(alpha = 0.72f),
            topLeft = Offset(stroke + 2.dp.toPx(), stroke + 2.dp.toPx()),
            size = Size(w - (stroke + 2.dp.toPx()) * 2f, h - (stroke + 2.dp.toPx()) * 2f),
            cornerRadius = CornerRadius((radius - 3.dp.toPx()).coerceAtLeast(1f), (radius - 3.dp.toPx()).coerceAtLeast(1f)),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}
