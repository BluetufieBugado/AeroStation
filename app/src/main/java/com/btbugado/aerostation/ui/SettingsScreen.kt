package com.btbugado.aerostation.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.btbugado.aerostation.data.AndroidGameStore
import com.btbugado.aerostation.data.AppAudio
import com.btbugado.aerostation.data.AudioSettingsStore
import com.btbugado.aerostation.data.ControlMode
import com.btbugado.aerostation.data.Credits
import com.btbugado.aerostation.data.EmulatorConfigStore
import com.btbugado.aerostation.data.EmulatorRegistry
import com.btbugado.aerostation.data.GameLaunchSettingsStore
import com.btbugado.aerostation.data.RetroAchievementsStore
import com.btbugado.aerostation.data.RomExtensions
import com.btbugado.aerostation.ui.theme.AeroTextPrimary
import com.btbugado.aerostation.ui.theme.AeroTextSecondary
import com.btbugado.aerostation.ui.theme.AeroGlassWhite
import com.btbugado.aerostation.ui.theme.AeroGlassWhiteStrong
import com.btbugado.aerostation.ui.theme.AeroGlassBorder
import com.btbugado.aerostation.ui.components.GlassIconButton
import com.btbugado.aerostation.ui.components.SettingsItem
import com.btbugado.aerostation.ui.components.DialogGamepadBar
import com.btbugado.aerostation.ui.components.GamepadDialogScope
import com.btbugado.aerostation.ui.components.LocalTouchActionsEnabled
import com.btbugado.aerostation.ui.components.gamepadFocusable

private data class EmulatorAppInfo(
    val packageName: String,
    val activityName: String,
    val label: String
)

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues = PaddingValues(),
    gamepadActive: Boolean = false,
    controlMode: ControlMode = ControlMode.TOUCH,
    onControlModeChange: (ControlMode) -> Unit = {},
    onEditPadLayout: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedConsole by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var showAndroidAppPicker by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("Geral") }
    var previewEnabled by remember { mutableStateOf(GameLaunchSettingsStore.isPreviewEnabled(context)) }
    var flipEnabled by remember { mutableStateOf(GameLaunchSettingsStore.isFlipEnabled(context)) }
    var flipSeconds by remember { mutableStateOf(GameLaunchSettingsStore.getFlipSeconds(context)) }
    // Busca da aba Emuladores (a lupinha). Mantém o texto ao trocar de aba.
    var emulatorSearch by remember { mutableStateOf("") }
    var emulatorSearchExpanded by remember { mutableStateOf(false) }

    // Um console por linha, vindos da lista explícita (sem categorias
    // agrupadas — cada console tem seu próprio emulador).
    val consoles = remember {
        val preferred = listOf("Game Boy Advance", "Nintendo DS")
        val others = RomExtensions.consoles.filterNot { it in preferred }.sorted()
        preferred + others
    }
    val filteredConsoles = remember(consoles, emulatorSearch) {
        val query = emulatorSearch.trim()
        if (query.isEmpty()) consoles
        else consoles.filter { it.contains(query, ignoreCase = true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Coluna lateral das categorias, mantendo o visual de menu do Switch.
            // Rolável: com 4+ categorias o último item ficava cortado e
            // inalcançável em telas menores/paisagem (foi assim que o Áudio
            // "sumiu"). O foco do controle traz o item pra vista sozinho.
            Column(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 30.dp, top = 24.dp, end = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Configurações",
                    color = AeroTextPrimary,
                    fontSize = 26.sp,
                    modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
                )

                SettingsCategoryItem(
                    title = "Geral",
                    selected = selectedCategory == "Geral",
                    gamepadAutoFocus = gamepadActive,
                    onClick = { selectedCategory = "Geral" }
                )
                SettingsCategoryItem(
                    title = "Emuladores",
                    selected = selectedCategory == "Emuladores",
                    onClick = { selectedCategory = "Emuladores" }
                )
                SettingsCategoryItem(
                    title = "Conquistas",
                    selected = selectedCategory == "Conquistas",
                    onClick = { selectedCategory = "Conquistas" }
                )
                SettingsCategoryItem(
                    title = "Áudio",
                    selected = selectedCategory == "Áudio",
                    onClick = { selectedCategory = "Áudio" }
                )
                SettingsCategoryItem(
                    title = "Créditos",
                    selected = selectedCategory == "Créditos",
                    onClick = { selectedCategory = "Créditos" }
                )
            }

            // Conteúdo da categoria selecionada.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, top = 24.dp, end = 36.dp, bottom = 24.dp)
            ) {
                when (selectedCategory) {
                    "Geral" -> {
                        // Conteúdo rolável: em telas menores as opções de baixo
                        // (ex: "Modo de entrada") ficavam cortadas e inalcançáveis.
                        // O foco do controle ainda traz o item pra vista via
                        // bringIntoView no gamepadFocusable.
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "Geral",
                                color = AeroTextPrimary,
                                fontSize = 26.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            Text(
                                text = "Configurações gerais do RetroAero.",
                                color = AeroTextSecondary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 18.dp)
                            )

                            SettingsItem(
                                title = "Adicionar aplicativo como jogo",
                                // A categoria "Geral" já é o ponto de entrada
                                // desta tela. Ter dois auto-focos concorrentes
                                // deixava o cursor escolher um alvo ao acaso ao
                                // chegar aqui via L1/R1.
                                gamepadAutoFocus = false,
                                subtitle = "Coloca um app Android na sua biblioteca da tela inicial.",
                                onClick = { showAndroidAppPicker = true }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Modo de entrada",
                                color = AeroTextPrimary,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                            )
                        Text(
                            text = "No modo controle aparece um gamepad de vidro na tela e o cursor fica visível. Nesse modo os toques nos botões e cartuchos são ignorados (só o gamepad ativa) para não abrir nada sem querer. O controle físico também funciona nesse modo. No modo toque o cursor nunca aparece.",
                            color = AeroTextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 6.dp, bottom = 10.dp)
                        )

                            SettingsItem(
                                title = "Toque",
                                gamepadAutoFocus = false,
                                subtitle = if (controlMode == ControlMode.TOUCH) {
                                    "Ativo — navegar com o dedo, sem cursor."
                                } else {
                                    "Navegar com o dedo, sem cursor."
                                },
                                onClick = { onControlModeChange(ControlMode.TOUCH) },
                                trailing = {
                                    if (controlMode == ControlMode.TOUCH) {
                                        Text(
                                            text = "✓",
                                            color = AeroTextPrimary,
                                            fontSize = 18.sp,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            SettingsItem(
                                title = "Controle",
                                gamepadAutoFocus = false,
                                subtitle = if (controlMode == ControlMode.GAMEPAD) {
                                    "Ativo — gamepad na tela + cursor visível."
                                } else {
                                    "Gamepad na tela + cursor visível."
                                },
                                onClick = { onControlModeChange(ControlMode.GAMEPAD) },
                                trailing = {
                                    if (controlMode == ControlMode.GAMEPAD) {
                                        Text(
                                            text = "✓",
                                            color = AeroTextPrimary,
                                            fontSize = 18.sp,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            SettingsItem(
                                title = "Editar controles na tela",
                                gamepadAutoFocus = false,
                                subtitle = "Muda a posição e o tamanho do gamepad virtual.",
                                onClick = onEditPadLayout
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Ao abrir um jogo",
                                color = AeroTextPrimary,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                            )
                            Text(
                                text = "Com animação mostra o preview 3D e a inserção antes de abrir. Direto pula tudo e inicia na hora.",
                                color = AeroTextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(start = 6.dp, bottom = 10.dp)
                            )

                            SettingsItem(
                                title = "Com animação",
                                gamepadAutoFocus = false,
                                subtitle = if (previewEnabled) {
                                    "Ativo — preview 3D + inserção."
                                } else {
                                    "Preview 3D + inserção."
                                },
                                onClick = {
                                    previewEnabled = true
                                    GameLaunchSettingsStore.setPreviewEnabled(context, true)
                                },
                                trailing = {
                                    if (previewEnabled) {
                                        Text(
                                            text = "✓",
                                            color = AeroTextPrimary,
                                            fontSize = 18.sp,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            SettingsItem(
                                title = "Direto",
                                gamepadAutoFocus = false,
                                subtitle = if (!previewEnabled) {
                                    "Ativo — inicia na hora, sem preview."
                                } else {
                                    "Inicia na hora, sem preview."
                                },
                                onClick = {
                                    previewEnabled = false
                                    GameLaunchSettingsStore.setPreviewEnabled(context, false)
                                },
                                trailing = {
                                    if (!previewEnabled) {
                                        Text(
                                            text = "✓",
                                            color = AeroTextPrimary,
                                            fontSize = 18.sp,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Na tela inicial",
                                color = AeroTextPrimary,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                            )
                            Text(
                                text = "Com flip os cartões viram sozinhos mostrando o tempo de jogo no verso. Parados economiza bateria.",
                                color = AeroTextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(start = 6.dp, bottom = 10.dp)
                            )

                            SettingsItem(
                                title = "Com flip",
                                gamepadAutoFocus = false,
                                subtitle = if (flipEnabled) {
                                    "Ativo — vira a cada alguns segundos."
                                } else {
                                    "Vira a cada alguns segundos."
                                },
                                onClick = {
                                    flipEnabled = true
                                    GameLaunchSettingsStore.setFlipEnabled(context, true)
                                },
                                trailing = {
                                    if (flipEnabled) {
                                        Text(
                                            text = "✓",
                                            color = AeroTextPrimary,
                                            fontSize = 18.sp,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            SettingsItem(
                                title = "Parados",
                                gamepadAutoFocus = false,
                                subtitle = if (!flipEnabled) {
                                    "Ativo — capa sempre à mostra."
                                } else {
                                    "Capa sempre à mostra."
                                },
                                onClick = {
                                    flipEnabled = false
                                    GameLaunchSettingsStore.setFlipEnabled(context, false)
                                },
                                trailing = {
                                    if (!flipEnabled) {
                                        Text(
                                            text = "✓",
                                            color = AeroTextPrimary,
                                            fontSize = 18.sp,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            FlipIntervalRow(
                                seconds = flipSeconds,
                                onLess = {
                                    flipSeconds = (flipSeconds - 1).coerceAtLeast(2).also {
                                        GameLaunchSettingsStore.setFlipSeconds(context, it)
                                    }
                                },
                                onMore = {
                                    flipSeconds = (flipSeconds + 1).coerceAtMost(10).also {
                                        GameLaunchSettingsStore.setFlipSeconds(context, it)
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Espaço reservado para futuras configurações gerais.
                            Text(
                                text = "Mais opções poderão aparecer aqui no futuro.",
                                color = AeroTextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 12.dp)
                            )
                        }
                    }

                    "Conquistas" -> {
                        AchievementsSettings(
                            gamepadActive = gamepadActive
                        )
                    }

                    "Áudio" -> {
                        AudioSettings(
                            gamepadActive = gamepadActive
                        )
                    }

                    "Créditos" -> {
                        CreditsSettings(
                            gamepadActive = gamepadActive,
                            selectedCategory = selectedCategory
                        )
                    }

                    "Emuladores" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Emuladores",
                                    color = AeroTextPrimary,
                                    fontSize = 26.sp,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                Text(
                                    text = "Escolha qual aplicativo abre cada console.",
                                    color = AeroTextSecondary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(bottom = 14.dp)
                                )
                            }
                            // Lupinha no canto superior direito: expande o
                            // campo de busca; ao fechar, limpa o filtro.
                            GlassIconButton(
                                onClick = {
                                    emulatorSearchExpanded = !emulatorSearchExpanded
                                    if (!emulatorSearchExpanded) emulatorSearch = ""
                                },
                                gamepadAutoFocus = false,
                                modifier = Modifier.padding(start = 12.dp, bottom = 14.dp)
                            ) {
                                Text(
                                    text = if (emulatorSearchExpanded) "✕" else "🔍",
                                    fontSize = 20.sp,
                                    color = AeroTextPrimary
                                )
                            }
                        }

                        // refreshKey mantém a atualização do valor configurado sem
                        // alterar a navegação ou recriar a tela inteira.
                        @Suppress("UNUSED_VARIABLE")
                        val currentRefresh = refreshKey

                        // Campo de texto pra teclado/toque; o foco inicial do
                        // gamepad continua no primeiro resultado — mas só com
                        // a busca vazia, pra digitar não roubar o foco e
                        // fechar o teclado a cada letra.
                        if (emulatorSearchExpanded) {
                            OutlinedTextField(
                                value = emulatorSearch,
                                onValueChange = { emulatorSearch = it },
                                singleLine = true,
                                placeholder = { Text("Pesquisar plataforma...") },
                                leadingIcon = { Text(text = "🔍") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            )
                        }

                        if (filteredConsoles.isEmpty()) {
                            Text(
                                text = "Nenhuma plataforma encontrada para \"${emulatorSearch.trim()}\".",
                                color = AeroTextSecondary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(start = 6.dp, top = 8.dp)
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 24.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(filteredConsoles, key = { _, it -> it }) { index, console ->
                                    val configuredPackage = EmulatorConfigStore.getPackageCompat(context, console)
                                    val configuredLabel = configuredPackage?.let { packageLabel(context, it) }
                                    val defaultTarget = EmulatorRegistry.defaultTarget(context, console)
                                    val label = configuredLabel ?: defaultTarget?.displayName ?: "Nenhum configurado"

                                    SettingsItem(
                                        title = console,
                                        gamepadAutoFocus = gamepadActive && selectedCategory == "Emuladores" && index == 0 && emulatorSearch.isBlank(),
                                        subtitle = label,
                                        onClick = { selectedConsole = console }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAndroidAppPicker) {
        AndroidGamePickerDialog(
            context = context,
            onDismiss = { showAndroidAppPicker = false },
            onSelected = { packageName, activityName, label ->
                val added = AndroidGameStore.add(context, packageName, activityName, label)
                Toast.makeText(
                    context,
                    if (added) "${label} adicionado à tela inicial!" else "Esse aplicativo já está na tela inicial.",
                    Toast.LENGTH_SHORT
                ).show()
                showAndroidAppPicker = false
            }
        )
    }

    selectedConsole?.let { console ->
        EmulatorPickerDialog(
            context = context,
            console = console,
            onDismiss = { selectedConsole = null },
            onSelected = { packageName, activityName ->
                EmulatorConfigStore.set(context, console, packageName, activityName)
                selectedConsole = null
                refreshKey++
            },
            onClear = {
                EmulatorConfigStore.clear(context, console)
                selectedConsole = null
                refreshKey++
            }
        )
    }
}

/**
 * Categoria Conquistas: usuário + chave de API do RetroAchievements.
 * Campos de texto pra teclado/toque; Salvar é navegável pelo controle.
 * Salvar com os campos vazios desconecta a conta.
 */
@Composable
private fun AchievementsSettings(gamepadActive: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var username by remember { mutableStateOf(RetroAchievementsStore.loadUsername(context)) }
    var apiKey by remember { mutableStateOf(RetroAchievementsStore.loadApiKey(context)) }
    var showKey by remember { mutableStateOf(false) }
    var savedTick by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Conquistas",
            color = AeroTextPrimary,
            fontSize = 26.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            text = "Mostra perfil e progresso na aba Conquistas.",
            color = AeroTextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "A chave fica só no aparelho. Gere em retroachievements.org > Settings.",
            color = AeroTextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 6.dp, bottom = 14.dp)
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it; savedTick = 0 },
            singleLine = true,
            label = { Text("Usuário") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it; savedTick = 0 },
            singleLine = true,
            label = { Text("Chave de API") },
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showKey = !showKey }) {
                    Text(if (showKey) "Ocultar" else "Ver", fontSize = 12.sp)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
        )

        SettingsItem(
            title = "Salvar",
            gamepadAutoFocus = gamepadActive,
            subtitle = "Conecta a conta e libera a aba Conquistas.",
            onClick = {
                RetroAchievementsStore.save(context, username, apiKey)
                savedTick++
            }
        )

        if (savedTick > 0) {
            Text(
                text = "✓ Salvo! Abra a aba Conquistas.",
                color = AeroTextPrimary,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 6.dp, top = 10.dp)
            )
        }
    }
}

/**
 * Categoria Áudio: volume da música em looping e dos efeitos. Sliders são
 * touch/teclado primeiro (como a busca e os campos de texto); o botão
 * Testar é navegável pelo controle e toca o jingle de launch.
 */
@Composable
private fun AudioSettings(gamepadActive: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var music by remember { mutableStateOf(AudioSettingsStore.musicVolume(context)) }
    var sfx by remember { mutableStateOf(AudioSettingsStore.sfxVolume(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Áudio",
            color = AeroTextPrimary,
            fontSize = 26.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            text = "Música ambiente em looping e sons de navegação.",
            color = AeroTextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 18.dp)
        )

        VolumeRow(
            title = "Música",
            value = music,
            onChange = {
                music = it
                AudioSettingsStore.setMusicVolume(context, it)
                AppAudio.setMusicVolume(it)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        VolumeRow(
            title = "Efeitos",
            value = sfx,
            subtitle = "Cursor, confirmar, janelas e início de jogo.",
            onChange = {
                sfx = it
                AudioSettingsStore.setSfxVolume(context, it)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        SettingsItem(
            title = "Testar efeitos",
            gamepadAutoFocus = gamepadActive,
            subtitle = "Toca o som de início de jogo.",
            onClick = { AppAudio.playLaunch() }
        )
    }
}

/**
 * Passo do intervalo do flip (2s..10s): − valor + navegáveis pelo controle.
 */
@Composable
private fun FlipIntervalRow(
    seconds: Int,
    onLess: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(listOf(AeroGlassWhiteStrong, AeroGlassWhite))
            )
            .border(1.dp, AeroGlassBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Intervalo do flip", color = AeroTextPrimary)
            Text(
                text = "Tempo parado na frente antes de virar.",
                color = AeroTextSecondary,
                fontSize = 13.sp
            )
        }
        TextButton(
            onClick = onLess,
            modifier = Modifier.gamepadFocusable(onConfirm = onLess)
        ) { Text("−", fontSize = 18.sp) }
        Text(
            text = "${seconds}s",
            color = AeroTextPrimary,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        TextButton(
            onClick = onMore,
            modifier = Modifier.gamepadFocusable(onConfirm = onMore)
        ) { Text("+", fontSize = 18.sp) }
    }
}

@Composable
private fun VolumeRow(
    title: String,
    value: Float,
    onChange: (Float) -> Unit,
    subtitle: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(listOf(AeroGlassWhiteStrong, AeroGlassWhite))
            )
            .border(1.dp, AeroGlassBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = AeroTextPrimary)
                if (subtitle != null) {
                    Text(text = subtitle, color = AeroTextSecondary, fontSize = 13.sp)
                }
            }
            Text(
                text = "${(value * 100).toInt()}%",
                color = AeroTextPrimary,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Categoria Créditos: quem torna o app possível (música, sons, dados).
 * Links abrem no navegador (toque ou A no controle).
 */
@Composable
private fun CreditsSettings(gamepadActive: Boolean, selectedCategory: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val touchOk = LocalTouchActionsEnabled.current
    val sections = remember { Credits.entries.groupBy { it.section } }
    val firstSection = sections.keys.firstOrNull()

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(context, "Não consegui abrir o navegador", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Créditos",
            color = AeroTextPrimary,
            fontSize = 26.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            text = "Quem torna este app possível.",
            color = AeroTextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 18.dp)
        )

        sections.forEach { (section, entries) ->
            Text(
                text = section,
                color = AeroTextPrimary,
                fontSize = 16.sp,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp, top = 6.dp)
            )
            entries.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(listOf(AeroGlassWhiteStrong, AeroGlassWhite))
                        )
                        .border(1.dp, AeroGlassBorder, RoundedCornerShape(16.dp))
                        .gamepadFocusable(
                            autoFocus = gamepadActive && selectedCategory == "Créditos" &&
                                section == firstSection && index == 0,
                            onConfirm = { entry.url?.let { openUrl(it) } }
                        )
                        .clickable { if (touchOk) entry.url?.let { openUrl(it) } }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = entry.name, color = AeroTextPrimary)
                        Text(text = entry.detail, color = AeroTextSecondary, fontSize = 13.sp)
                        if (entry.url != null) {
                            Text(
                                text = entry.url,
                                color = AeroTextPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun SettingsCategoryItem(
    title: String,
    selected: Boolean,
    gamepadAutoFocus: Boolean = false,
    onClick: () -> Unit
) {
    val background = if (selected) AeroGlassWhiteStrong else AeroGlassWhite
    val border = if (selected) AeroGlassBorder else androidx.compose.ui.graphics.Color.Transparent
    val touchOk = LocalTouchActionsEnabled.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .gamepadFocusable(autoFocus = gamepadAutoFocus, onConfirm = onClick)
            .clickable(onClick = { if (touchOk) onClick() })
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = AeroTextPrimary,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun AndroidGamePickerDialog(
    context: Context,
    onDismiss: () -> Unit,
    onSelected: (String, String, String) -> Unit
) {
    val apps = remember { queryInstalledApps(context) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter {
            it.label.contains(q, ignoreCase = true) ||
                it.packageName.contains(q, ignoreCase = true)
        }
    }

    GamepadDialogScope {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Adicionar aplicativo como jogo") },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text("Pesquisar app...", fontSize = 13.sp) },
                        leadingIcon = { Text(text = "🔍", fontSize = 14.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                    LazyColumn(modifier = Modifier.height(280.dp)) {
                        if (apps.isEmpty()) {
                            item { Text("Não encontrei aplicativos disponíveis.") }
                        } else if (filtered.isEmpty()) {
                            item { Text("Nada encontrado para \"${query.trim()}\".") }
                        } else {
                            itemsIndexed(filtered, key = { _, it -> it.packageName }) { index, app ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .gamepadFocusable(
                                            autoFocus = index == 0 && query.isBlank(),
                                            onConfirm = { onSelected(app.packageName, app.activityName, app.label) },
                                            onBack = onDismiss
                                        )
                                        .clickable { onSelected(app.packageName, app.activityName, app.label) }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.label, color = AeroTextPrimary)
                                        Text(app.packageName, color = AeroTextSecondary, fontSize = 11.sp)
                                    }
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
private fun EmulatorPickerDialog(
    context: Context,
    console: String,
    onDismiss: () -> Unit,
    onSelected: (String, String?) -> Unit,
    onClear: () -> Unit
) {
    val apps = remember(console) { queryInstalledApps(context) }
    val known = remember(console) { EmulatorRegistry.knownTargets(console).associateBy { it.packageName } }
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter {
            it.label.contains(q, ignoreCase = true) ||
                it.packageName.contains(q, ignoreCase = true)
        }
    }

    GamepadDialogScope {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Emulador para $console") },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text("Pesquisar app...", fontSize = 13.sp) },
                        leadingIcon = { Text(text = "🔍", fontSize = 14.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                    LazyColumn(modifier = Modifier.height(260.dp)) {
                        if (apps.isEmpty()) {
                            item {
                                Text("Não encontrei aplicativos disponíveis para escolher.")
                            }
                        } else if (filtered.isEmpty()) {
                            item { Text("Nada encontrado para \"${query.trim()}\".") }
                        } else {
                            itemsIndexed(filtered, key = { _, it -> it.packageName }) { index, app ->
                                val knownTarget = known[app.packageName]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .gamepadFocusable(
                                            autoFocus = index == 0 && query.isBlank(),
                                            onConfirm = { onSelected(app.packageName, knownTarget?.activityName) },
                                            onBack = onDismiss
                                        )
                                        .clickable {
                                            onSelected(app.packageName, knownTarget?.activityName)
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.label, color = AeroTextPrimary)
                                        Text(app.packageName, color = AeroTextSecondary, fontSize = 11.sp)
                                    }
                                    if (knownTarget != null) {
                                        Text("Suportado", color = AeroTextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                    DialogGamepadBar()
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.gamepadFocusable(onConfirm = onClear, onBack = onDismiss)
                ) { Text("Desconfigurar") }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.gamepadFocusable(onConfirm = onDismiss, onBack = onDismiss)
                ) { Text("Cancelar") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

private fun queryInstalledApps(context: Context): List<EmulatorAppInfo> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return context.packageManager
        .queryIntentActivities(intent, 0)
        .mapNotNull { info: ResolveInfo ->
            val activityInfo = info.activityInfo ?: return@mapNotNull null
            val packageName = activityInfo.packageName
            if (packageName == context.packageName) return@mapNotNull null
            EmulatorAppInfo(
                packageName = packageName,
                activityName = activityInfo.name,
                label = info.loadLabel(context.packageManager).toString()
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

private fun packageLabel(context: Context, packageName: String): String =
    runCatching {
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(packageName, 0)
        ).toString()
    }.getOrDefault(packageName)
