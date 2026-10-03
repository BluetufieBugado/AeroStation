package com.btbugado.aerostation.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import android.widget.Toast
import com.btbugado.aerostation.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.btbugado.aerostation.ui.theme.AeroGlassBorder
import com.btbugado.aerostation.ui.theme.AeroGlassWhite
import com.btbugado.aerostation.ui.theme.AeroGlassWhiteStrong
import com.btbugado.aerostation.ui.theme.AeroTextSecondary
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.btbugado.aerostation.data.AppAudio
import com.btbugado.aerostation.data.AppTheme
import com.btbugado.aerostation.data.ThemeEngine
import com.btbugado.aerostation.data.ControlMode
import com.btbugado.aerostation.data.ControlModeStore
import com.btbugado.aerostation.data.NavBarStore
import com.btbugado.aerostation.data.CrashReporter
import com.btbugado.aerostation.data.PlayTimeStore
import com.btbugado.aerostation.ui.components.DialogGamepadBar
import com.btbugado.aerostation.ui.components.GamepadDialogScope
import com.btbugado.aerostation.ui.components.gamepadFocusable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.btbugado.aerostation.data.VirtualPadLayout
import com.btbugado.aerostation.data.VirtualPadLayoutStore
import com.btbugado.aerostation.data.VirtualPadVisibilityStore
import com.btbugado.aerostation.ui.components.AeroBackground
import com.btbugado.aerostation.ui.components.BottomNavBar
import com.btbugado.aerostation.ui.components.GamepadCursorOverlay
import com.btbugado.aerostation.ui.components.LocalControlMode
import com.btbugado.aerostation.ui.components.LocalGamepadCursor
import com.btbugado.aerostation.ui.components.LocalDpadOverride
import com.btbugado.aerostation.ui.components.LocalGamepadCursorVisible
import com.btbugado.aerostation.ui.components.LocalHidePadForOverlay
import com.btbugado.aerostation.ui.components.LocalGamepadEnabled
import com.btbugado.aerostation.ui.components.LocalHideNavForOverlay
import com.btbugado.aerostation.ui.components.LocalOnGamepadUsed
import com.btbugado.aerostation.ui.components.LocalTouchActionsEnabled
import com.btbugado.aerostation.ui.components.LocalVirtualGamepadEvents
import com.btbugado.aerostation.ui.components.LocalVirtualPadVisible
import com.btbugado.aerostation.ui.components.VirtualDpadFocusHandler
import com.btbugado.aerostation.ui.components.VirtualGamepadOverlay
import com.btbugado.aerostation.ui.components.VirtualPadLayoutEditor
import com.btbugado.aerostation.ui.components.rememberGamepadCursorState
import com.btbugado.aerostation.ui.components.rememberVirtualGamepadEvents

/** Espaço reservado embaixo do conteúdo de cada aba pra não ficar coberto pela barra. */
private val BottomBarClearance = 96.dp

/** Teclas que indicam um controle físico navegando (evidência p/ cursor sempre visível). */
private fun isGamepadKey(keyCode: Int): Boolean = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_BUTTON_A,
    KeyEvent.KEYCODE_BUTTON_B,
    KeyEvent.KEYCODE_BUTTON_C,
    KeyEvent.KEYCODE_BUTTON_X,
    KeyEvent.KEYCODE_BUTTON_Y,
    KeyEvent.KEYCODE_BUTTON_Z,
    KeyEvent.KEYCODE_BUTTON_L1,
    KeyEvent.KEYCODE_BUTTON_L2,
    KeyEvent.KEYCODE_BUTTON_R1,
    KeyEvent.KEYCODE_BUTTON_R2,
    KeyEvent.KEYCODE_BUTTON_THUMBL,
    KeyEvent.KEYCODE_BUTTON_THUMBR,
    KeyEvent.KEYCODE_BUTTON_START,
    KeyEvent.KEYCODE_BUTTON_SELECT,
    KeyEvent.KEYCODE_BUTTON_MODE -> true
    // ENTER/NUMPAD_ENTER de propósito FORA: eles vêm do teclado
    // físico/virtual (ex: ao renomear um jogo) e marcavam um
    // "controle físico" inexistente, fazendo o contorno branco
    // aparecer no modo touch sem controle conectado.
    else -> false
}

/** Checa se há algum controle físico (gamepad/joystick/D-pad) conectado. */
private fun hasGamepadDevice(): Boolean =
    InputDevice.getDeviceIds().any { id ->
        val device = InputDevice.getDevice(id) ?: return@any false
        val sources = device.sources
        (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (sources and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
    }

/**
 * Raiz da UI: fundo animado desenhado uma única vez (fica fixo entre trocas
 * de aba, sem reiniciar a animação), conteúdo da aba atual por cima, e a
 * barra inferior estilo Switch (Início / Apps / Ajustes) flutuando no fundo
 * da tela.
 *
 * Modo de entrada (Configurações > Geral):
 * - TOUCH: sem cursor, sem foco de controle e sem contorno branco.
 *   É o modo dedo: nenhum input liga o cursor aqui.
 * - GAMEPAD: controle virtual de vidro na tela + cursor visível. Usar o
 *   controle FÍSICO esconde o pad virtual (o cursor continua nele); tocar
 *   na tela traz o pad virtual de volta ("quero navegar pelo pad") e
 *   esconde o cursor até o próximo input de controle (virtual ou físico).
 * - Controle FÍSICO: funciona dentro do modo GAMEPAD. No modo TOUCH o
 *   cursor nunca aparece, mesmo com controle conectado.
 */
@Composable
fun RetroAeroApp() {
    val context = LocalContext.current
    val gamepadCursor = rememberGamepadCursorState()
    val virtualEvents = rememberVirtualGamepadEvents()
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    var controlMode by remember { mutableStateOf(ControlModeStore.load(context)) }
    var padLayout by remember { mutableStateOf(VirtualPadLayoutStore.load(context)) }
    var editingPad by remember { mutableStateOf(false) }
    var hasPhysicalGamepad by remember { mutableStateOf(hasGamepadDevice()) }
    var touchNavigating by remember { mutableStateOf(false) }
    // Pad virtual visível? Restaura o último estado salvo (físico em uso =
    // escondido, digital em uso = visível). Só muda por input real.
    var virtualPadVisible by remember { mutableStateOf(VirtualPadVisibilityStore.load(context)) }
    // BottomNav escondida? Overlays fullscreen da Home (preview/inserção)
    // pedem pra esconder enquanto duram — sem isso a barra ficava visível
    // e tocável por cima do véu.
    var navHiddenForOverlay by remember { mutableStateOf(false) }
    // BottomNav escondida por scroll (listas verticais: Jogados/Álbum)?
    // Rolar pra baixo esconde, rolar pra cima mostra — ganha espaço vertical.
    // Só vale no modo TOUCH: no modo GAMEPAD a barra precisa continuar
    // focável, senão o cursor perde o alvo no meio da navegação.
    var navHiddenForScroll by remember { mutableStateOf(false) }
    // BottomNav escondida MANUALMENTE (segurar o fundo no modo touch).
    // Sessão apenas: troca de aba e modo controle sempre mostram de volta,
    // pra nunca strandar o usuário sem navegação.
    var navHiddenManual by remember { mutableStateOf(false) }
    // Auto-hide no scroll ligado? (Ajustes > Geral; padrão ligado.)
    var navAutoHide by remember { mutableStateOf(NavBarStore.isAutoHideEnabled(context)) }
    // Pad virtual escondido? O viewer de mídia da galeria pede enquanto aberto.
    var padHiddenForOverlay by remember { mutableStateOf(false) }
    // Editor de tema em tela cheia (Ajustes > Temas > Novo/Editar): tema +
    // se é novo. Enquanto aberto, cobre tudo (sem painel, sem nav, sem pad).
    var themeEditorRequest by remember { mutableStateOf<Pair<AppTheme, Boolean>?>(null) }
    // Incrementado ao fechar o editor: a lista de temas recarrega.
    var themeEditorClosedTick by remember { mutableStateOf(0) }
    // Override do D-pad virtual (ex: viewer de vídeo usa ←/→ pra seek):
    // quem ativa escreve aqui, o handler da janela consome.
    val dpadOverride = remember { mutableStateOf<((FocusDirection) -> Boolean)?>(null) }
    // Log de crash da sessão anterior (gravado pelo CrashReporter): mostra
    // o dialog de envio uma vez por abertura, se houver.
    var crashFile by remember { mutableStateOf<File?>(null) }
    // Muda quando uma sessão de jogo fecha na volta: a fileira "Mais
    // jogados" da Home se recalcula.
    var playTick by remember { mutableStateOf(0) }
    val focusManager = LocalFocusManager.current

    /** Troca a visibilidade persistindo — só grava na transição (não a cada toque). */
    fun setVirtualPadVisible(visible: Boolean) {
        if (virtualPadVisible == visible) return
        virtualPadVisible = visible
        VirtualPadVisibilityStore.save(context, visible)
    }

    // Reage a conectar/desconectar controle físico em tempo real.
    DisposableEffect(context) {
        val inputManager = context.getSystemService(InputManager::class.java)
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                hasPhysicalGamepad = hasGamepadDevice()
            }

            override fun onInputDeviceRemoved(deviceId: Int) {
                hasPhysicalGamepad = hasGamepadDevice()
            }

            override fun onInputDeviceChanged(deviceId: Int) {
                hasPhysicalGamepad = hasGamepadDevice()
            }
        }
        inputManager?.registerInputDeviceListener(listener, null)
        hasPhysicalGamepad = hasGamepadDevice()
        onDispose { inputManager?.unregisterInputDeviceListener(listener) }
    }

    // Habilitado = pode receber foco de controle. Visível = desenha o cursor.
    // Regra estrita pedida pelo usuário: só o modo GAMEPAD liga foco e
    // contorno branco. No modo TOUCH tudo fica desligado, mesmo com
    // controle físico conectado ou tecla de teclado apertada antes.
    // (hasPhysicalGamepad segue detectado abaixo para uso futuro, mas não
    // liga mais o cursor sozinho.)
    @Suppress("UNUSED_VARIABLE")
    val physicalDetected = hasPhysicalGamepad
    val gamepadUiEnabled = controlMode == ControlMode.GAMEPAD
    val cursorVisible = gamepadUiEnabled && !touchNavigating
    // Com o controle virtual ativo, toques em botões/cartões não disparam
    // ações (só o gamepad ativa) — evita abrir jogo/app sem querer ao usar
    // o pad na tela. Rolagem, texto, dialogs e o próprio pad não são afetados.
    val touchActionsEnabled = controlMode != ControlMode.GAMEPAD

    // Áudio: prepara efeitos + música uma vez; pausa a música ao sair do
    // app (ex: emulador por cima) e volta ao retornar.
    LaunchedEffect(Unit) {
        // Tema ativo ANTES do áudio: o init já carrega bgm/sfx do tema.
        ThemeEngine.init(context)
        withContext(Dispatchers.IO) { AppAudio.init(context) }
        // Crash da sessão anterior? Mostra o dialog de envio (sem logcat).
        crashFile = CrashReporter.pendingCrashes(context).firstOrNull()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> AppAudio.onAppPaused()
                Lifecycle.Event.ON_RESUME -> {
                    AppAudio.onAppResumed()
                    // Volta do emulador: fecha a sessão de tempo de jogo, se
                    // houver. Mudou algo = avisa a aba Jogados (playTick).
                    if (PlayTimeStore.endSession(context) != null) playTick++
                    // Volta do emulador/outro app: nós de foco podem ter sido
                    // descartados (ou o processo recriado com links
                    // nextUp/nextDown apontando pro vazio). Foco podre +
                    // D-pad = IllegalStateException no moveFocus — o crash do
                    // log, que pega o D-pad físico também (esse passa pelo
                    // moveFocus interno do framework, fora do nosso alcance).
                    // Recomeça limpo: o próximo toque no D-pad refaz uma
                    // busca inicial. Campos de texto em dialogs usam outra
                    // janela/focus, então o teclado não é afetado.
                    runCatching {
                        gamepadCursor.clearAll()
                        focusManager.clearFocus(force = true)
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Quando o gamepad desliga (voltou pro touch, desconectou o físico),
    // solta qualquer foco preso e apaga o retângulo do cursor. Sem isso o
    // contorno branco ficava parado num elemento no modo touch.
    LaunchedEffect(gamepadUiEnabled) {
        // No modo controle a barra é alvo de foco: nunca fica escondida
        // manualmente (o gesto só vale no touch).
        if (gamepadUiEnabled) navHiddenManual = false
        if (!gamepadUiEnabled) {
            gamepadCursor.clearAll()
            focusManager.clearFocus(force = true)
        }
    }

    fun markGamepadUsed() {
        touchNavigating = false
    }

    /**
     * Input do controle FÍSICO: reexibe o cursor e esconde o pad virtual
     * (quem está com o físico na mão não precisa do pad na tela).
     */
    fun markPhysicalUsed() {
        markGamepadUsed()
        setVirtualPadVisible(false)
    }

    fun switchScreen(screen: AppScreen) {
        if (screen == currentScreen) return
        // Limpa o foco antigo antes de trocar a composição. Assim nenhum
        // elemento da página anterior permanece registrado no FocusManager
        // durante a criação da nova página.
        gamepadCursor.clearAll()
        focusManager.clearFocus(force = true)
        // Teto de segurança: nenhum overlay sobrevive à troca de aba, então
        // a nav e o pad nunca ficam escondidos pra sempre (ex: L1/R1 no meio
        // do preview ou do viewer). O editor de tema fecha junto.
        navHiddenForOverlay = false
        padHiddenForOverlay = false
        navHiddenForScroll = false
        navHiddenManual = false
        themeEditorRequest = null
        currentScreen = screen
    }

    fun tabPrev() {
        switchScreen(
            when (currentScreen) {
                AppScreen.HOME -> AppScreen.SETTINGS
                AppScreen.PLAYED -> AppScreen.HOME
                AppScreen.APPS -> AppScreen.PLAYED
                AppScreen.GALLERY -> AppScreen.APPS
                AppScreen.ACHIEVEMENTS -> AppScreen.GALLERY
                AppScreen.SETTINGS -> AppScreen.ACHIEVEMENTS
            }
        )
    }

    fun tabNext() {
        switchScreen(
            when (currentScreen) {
                AppScreen.HOME -> AppScreen.PLAYED
                AppScreen.PLAYED -> AppScreen.APPS
                AppScreen.APPS -> AppScreen.GALLERY
                AppScreen.GALLERY -> AppScreen.ACHIEVEMENTS
                AppScreen.ACHIEVEMENTS -> AppScreen.SETTINGS
                AppScreen.SETTINGS -> AppScreen.HOME
            }
        )
    }

    fun setControlMode(mode: ControlMode) {
        ControlModeStore.save(context, mode)
        controlMode = mode
        if (mode == ControlMode.GAMEPAD) {
            // Mostra o cursor/pad na hora.
            touchNavigating = false
            setVirtualPadVisible(true)
        } else {
            // Voltou pro touch: sempre esconde tudo e solta o foco,
            // mesmo com controle físico conectado.
            gamepadCursor.clearAll()
            focusManager.clearFocus(force = true)
        }
    }

    CompositionLocalProvider(
        LocalGamepadCursor provides gamepadCursor,
        LocalVirtualGamepadEvents provides virtualEvents,
        LocalDpadOverride provides dpadOverride,
        LocalControlMode provides controlMode,
        LocalOnGamepadUsed provides ::markGamepadUsed,
        LocalGamepadEnabled provides gamepadUiEnabled,
        LocalGamepadCursorVisible provides cursorVisible,
        LocalTouchActionsEnabled provides touchActionsEnabled,
        LocalVirtualPadVisible provides virtualPadVisible,
        LocalHideNavForOverlay provides { hidden: Boolean -> navHiddenForOverlay = hidden },
        LocalHidePadForOverlay provides { hidden: Boolean -> padHiddenForOverlay = hidden },
        LocalOpenThemeEditor provides { theme: AppTheme, isNew: Boolean ->
            themeEditorRequest = theme to isNew
        },
        LocalThemeEditorClosedTick provides themeEditorClosedTick
    ) {
        // Auto-hide da BottomNav no scroll (só TOUCH e com a chave ligada:
        // no GAMEPAD ela é alvo de foco e sumir no meio da navegação
        // strandaria o cursor). Rolar pra baixo (available.y < 0) esconde,
        // pra cima mostra. Histerese de 10px: evita liga/desliga com o
        // jitter do dedo parado.
        val scrollHideConnection = remember(gamepadUiEnabled, navAutoHide) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    if (gamepadUiEnabled || !navAutoHide) return Offset.Zero
                    if (available.y < -10f) {
                        if (!navHiddenForScroll) navHiddenForScroll = true
                    } else if (available.y > 10f) {
                        if (navHiddenForScroll) navHiddenForScroll = false
                    }
                    return Offset.Zero
                }
            }
        }
        val navVisible = !navHiddenForOverlay && !navHiddenManual &&
            !(navHiddenForScroll && navAutoHide && !gamepadUiEnabled)
        // Troca SECA do respiro (sem animateDpAsState): animar o padding
        // recompunha a lista inteira a cada frame — era o lag do swipe.
        // O deslize da barra continua animado (só a barra, baratinho).
        // Com a barra escondida manualmente a alça flutua embaixo, então o
        // respiro é um pouco maior que o vazio total.
        val bottomClearance = if (navVisible) BottomBarClearance else 34.dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollHideConnection)
                // Toque em conteúdo = "quero o pad virtual de volta": mostra
                // o pad e esconde o cursor até o próximo input de controle.
                // requireUnconsumed=true: toques nos botões do próprio pad
                // virtual são consumidos por eles e ignorados aqui — senão
                // apertar qualquer botão do pad escondia o cursor na hora.
                // No modo TOUCH o cursor/pad já são desligados, então é no-op.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = true)
                        touchNavigating = true
                        setVirtualPadVisible(true)
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                        return@onPreviewKeyEvent false
                    }
                    // Igual nos itens: botão segurado repete o DOWN. Sem o
                    // guard, segurar L1/R1 carrossela as abas sem parar.
                    // False = propaga, então o D-pad segurado continua
                    // navegando normalmente pelo foco do Compose.
                    if (event.nativeKeyEvent.repeatCount > 0) {
                        return@onPreviewKeyEvent false
                    }
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BUTTON_L1 -> {
                            markPhysicalUsed()
                            tabPrev()
                            true
                        }
                        KeyEvent.KEYCODE_BUTTON_R1 -> {
                            markPhysicalUsed()
                            tabNext()
                            true
                        }
                        else -> {
                            if (isGamepadKey(event.nativeKeyEvent.keyCode)) {
                                // Físico em uso: reexibe o cursor (só tem
                                // efeito no modo GAMEPAD) e esconde o pad
                                // virtual; o item focado trata a tecla.
                                // De propósito NÃO liga hasPhysicalGamepad
                                // aqui: a trava antiga fazia o contorno
                                // branco aparecer no modo touch para sempre
                                // após uma tecla qualquer.
                                markPhysicalUsed()
                            }
                            false
                        }
                    }
                }
        ) {
            AeroBackground(modifier = Modifier.fillMaxSize())

            val screenContentPadding = PaddingValues(bottom = bottomClearance)

            // Handler do D-pad virtual para a janela principal (dialogs têm
            // o próprio dentro de GamepadDialogScope).
            VirtualDpadFocusHandler()

            // Somente a página atual é composta. Antes mantínhamos Home, Apps e
            // Ajustes montadas simultaneamente com alpha=0; isso preservava a
            // composição, mas também deixava os elementos invisíveis na árvore
            // de foco do Android/Compose. Para o gamepad isso causava "foco
            // fantasma" em outras abas.
            //
            // Agora existe uma única árvore interativa por vez. Os dados pesados
            // continuam protegidos por LauncherContentCache/Stores e a troca
            // continua sendo instantânea, pois não há animação nem espera.
            //
            // Com o editor de tema aberto, NEM a página vai: ela sai da
            // composição (igual a grade da galeria atrás do viewer) — sem
            // botões "fantasma" por baixo do overlay e sem custo de compor
            // duas telas pesadas juntas.
            if (themeEditorRequest == null) {
            when (currentScreen) {
                AppScreen.HOME -> {
                    HomeScreen(
                        contentPadding = screenContentPadding,
                        gamepadActive = gamepadUiEnabled,
                        playTick = playTick
                    )
                }

                AppScreen.PLAYED -> {
                    PlayedScreen(
                        contentPadding = screenContentPadding,
                        gamepadActive = gamepadUiEnabled,
                        playTick = playTick
                    )
                }

                AppScreen.APPS -> {
                    AppsScreen(
                        contentPadding = screenContentPadding,
                        gamepadActive = gamepadUiEnabled
                    )
                }

                AppScreen.GALLERY -> {
                    GalleryScreen(
                        contentPadding = screenContentPadding,
                        gamepadActive = gamepadUiEnabled
                    )
                }

                AppScreen.ACHIEVEMENTS -> {
                    AchievementsScreen(
                        contentPadding = screenContentPadding,
                        gamepadActive = gamepadUiEnabled
                    )
                }

                AppScreen.SETTINGS -> {
                    SettingsScreen(
                        contentPadding = screenContentPadding,
                        gamepadActive = gamepadUiEnabled,
                        controlMode = controlMode,
                        onControlModeChange = ::setControlMode,
                        onEditPadLayout = { editingPad = true },
                        navAutoHide = navAutoHide,
                        onNavAutoHideChange = {
                            navAutoHide = it
                            NavBarStore.setAutoHideEnabled(context, it)
                        }
                    )
                }
            }
            } else {
                // Editor aberto: página fora da composição (ver comentário).
                Spacer(modifier = Modifier.fillMaxSize())
            }

            GamepadCursorOverlay(modifier = Modifier.zIndex(100f))

            // Editor de tema em tela cheia (sem painel, sem nav, sem pad):
            // abaixo do cursor (z100) pra ele continuar visível no controle,
            // acima do conteúdo. Voltar/Salvar devolvem pra lista de temas.
            themeEditorRequest?.let { (theme, isNew) ->
                ThemeEditorOverlay(
                    initial = theme,
                    isNew = isNew,
                    gamepadActive = gamepadUiEnabled,
                    onClose = { saved ->
                        if (saved) ThemeEngine.refresh(context)
                        themeEditorRequest = null
                        themeEditorClosedTick++
                    },
                    modifier = Modifier.zIndex(50f)
                )
            }

            // Pad virtual some enquanto o físico está em uso, ao tocar na
            // tela ele volta (virtualPadVisible) e no viewer de mídia
            // (padHiddenForOverlay) — lá sobram os botões da mídia.
            if (controlMode == ControlMode.GAMEPAD && virtualPadVisible && !editingPad && !padHiddenForOverlay) {
                VirtualGamepadOverlay(
                    onTabPrev = { markGamepadUsed(); tabPrev() },
                    onTabNext = { markGamepadUsed(); tabNext() },
                    layout = padLayout,
                    modifier = Modifier.zIndex(200f)
                )
            }

            if (editingPad) {
                VirtualPadLayoutEditor(
                    initial = padLayout,
                    onSave = { layout ->
                        VirtualPadLayoutStore.save(context, layout)
                        padLayout = layout
                        editingPad = false
                    },
                    onCancel = { editingPad = false },
                    onReset = {
                        VirtualPadLayoutStore.clear(context)
                        padLayout = VirtualPadLayout()
                    },
                    modifier = Modifier.zIndex(300f)
                )
            }

            // Fora de cena durante overlays fullscreen (preview/inserção):
            // nem visível, nem tocável, nem focável pelo controle.
            // Fora isso, some no scroll pra baixo (modo touch) e volta no
            // scroll pra cima — seco, SEM animação: qualquer animação aqui
            // roda junto do scroll e engasga em aparelho fraco. O ganho de
            // espaço (~70dp) continua valendo. A alça acima da barra alterna
            // manualmente (só touch); no controle a barra fica sempre.
            if (!navHiddenForOverlay && !gamepadUiEnabled) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                        .zIndex(10f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NavHandle(
                        expanded = navVisible,
                        onToggle = { navHiddenManual = !navHiddenManual },
                        touchOk = touchActionsEnabled
                    )
                    if (navVisible) {
                        Spacer(modifier = Modifier.height(6.dp))
                        BottomNavBar(
                            current = currentScreen,
                            onSelect = { switchScreen(it) }
                        )
                    }
                }
            } else if (navVisible) {
                BottomNavBar(
                    current = currentScreen,
                    onSelect = { switchScreen(it) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                        .zIndex(10f)
                )
            }

            // Log do crash anterior: dialog pra copiar/compartilhar sem PC.
            crashFile?.let { file ->
                CrashReportDialog(
                    file = file,
                    onShare = { shareCrash(context, file) },
                    onCopy = { copyCrash(context, file) },
                    onDelete = {
                        CrashReporter.deleteCrash(file)
                        crashFile = CrashReporter.pendingCrashes(context).firstOrNull()
                    },
                    onDismiss = { crashFile = null },
                    modifier = Modifier.zIndex(400f)
                )
            }
        }
    }
}

/**
 * Alça sutil acima da BottomNav: pílula de vidro com setinha que alterna a
 * barra (mostra/esconde). De propósito discreta — some junto nos overlays
 * fullscreen e nem existe no modo controle (lá a barra é alvo de foco).
 */
@Composable
private fun NavHandle(
    expanded: Boolean,
    onToggle: () -> Unit,
    touchOk: Boolean
) {
    val desc = stringResource(R.string.nav_toggle_desc)
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 16.dp)
            .clip(RoundedCornerShape(50))
            .background(Brush.linearGradient(listOf(AeroGlassWhiteStrong, AeroGlassWhite)))
            .border(1.dp, AeroGlassBorder, RoundedCornerShape(50))
            .semantics { contentDescription = desc }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = { if (touchOk) onToggle() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (expanded) "▼" else "▲",
            color = AeroTextSecondary,
            fontSize = 9.sp
        )
    }
}

/**
 * Dialog do log de crash: mostra o stack trace (rolável, selecionável) com
 * Compartilhar (manda o texto completo pra qualquer app) e Copiar.
 * Dentro de [GamepadDialogScope] pra funcionar no controle também.
 */
@Composable
private fun CrashReportDialog(
    file: File,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val content = remember(file) { CrashReporter.readCrash(context, file) }
    GamepadDialogScope {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.screens_crash_dialog_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.screens_crash_dialog_message, file.name),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.06f))
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = content.take(4000),
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                    DialogGamepadBar()
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onShare,
                    modifier = Modifier.gamepadFocusable(autoFocus = true, onConfirm = onShare)
                ) { Text(stringResource(R.string.common_share)) }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = onCopy,
                        modifier = Modifier.gamepadFocusable(onConfirm = onCopy)
                    ) { Text(stringResource(R.string.common_copy)) }
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.gamepadFocusable(onConfirm = onDelete, onBack = onDismiss)
                    ) { Text(stringResource(R.string.common_delete)) }
                }
            },
            shape = RoundedCornerShape(20.dp),
            modifier = modifier
        )
    }
}

private fun shareCrash(context: Context, file: File) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "AeroStation ${file.name}")
        putExtra(Intent.EXTRA_TEXT, CrashReporter.readCrash(context, file))
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.screens_crash_chooser_title)))
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.common_share_failed), Toast.LENGTH_SHORT).show()
    }
}

private fun copyCrash(context: Context, file: File) {
    val clip = ClipData.newPlainText("AeroStation crash", CrashReporter.readCrash(context, file))
    runCatching {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
    }
    Toast.makeText(context, context.getString(R.string.screens_crash_copied_toast), Toast.LENGTH_SHORT).show()
}
