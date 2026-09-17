package com.btbugado.aerostation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Menu que aparece ao segurar o dedo num ícone de jogo (long-press),
 * ou ao apertar X no controle com um cartucho focado.
 *
 * Fica dentro de [GamepadDialogScope]: dialogs são outra janela, então o
 * overlay central da tela principal fica invisível atrás deles. No escopo
 * do dialog cada item focado desenha seu próprio anel de cursor local.
 */
@Composable
fun GameActionMenu(
    gameName: String,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onPickFromGallery: () -> Unit,
    onSearchSteamGridDb: () -> Unit,
    onAutoFetchCover: () -> Unit,
    onPickConsole: () -> Unit,
    onResetToDefault: () -> Unit,
    onRemoveAndroidApp: (() -> Unit)? = null
) {
    GamepadDialogScope {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(gameName) },
            text = {
                // Rolável: em telas baixas as últimas opções ficavam cortadas
                // e inalcançáveis. Com verticalScroll o dedo arrasta e o foco
                // do gamepad traz o item pra vista via bringIntoView.
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    MenuAction("Renomear", onRename, autoFocus = true, onBack = onDismiss)
                    MenuAction("Escolher capa da galeria", onPickFromGallery, onBack = onDismiss)
                    MenuAction("Pesquisar no SteamGridDB", onSearchSteamGridDb, onBack = onDismiss)
                    MenuAction("Buscar capa automaticamente", onAutoFetchCover, onBack = onDismiss)
                    MenuAction("Definir plataforma", onPickConsole, onBack = onDismiss)
                    MenuAction("Restaurar padrão", onResetToDefault, onBack = onDismiss)
                    onRemoveAndroidApp?.let { MenuAction("Remover da tela inicial", it, onBack = onDismiss) }
                }
            },
            confirmButton = {
                // Barra de navegação do controle virtual: dialogs são outra
                // janela e o pad da tela principal fica atrás deles, então
                // cada dialog carrega sua mini-barra interna.
                Column {
                    DialogGamepadBar()
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.gamepadFocusable(onConfirm = onDismiss, onBack = onDismiss)
                    ) { Text("Fechar") }
                }
            }
        )
    }
}

@Composable
private fun MenuAction(
    label: String,
    onClick: () -> Unit,
    autoFocus: Boolean = false,
    onBack: (() -> Unit)? = null
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .gamepadFocusable(autoFocus = autoFocus, onConfirm = onClick, onBack = onBack)
    ) {
        Text(label, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Diálogo simples pra renomear um jogo.
 *
 * Também usa [GamepadDialogScope] para o cursor do controle aparecer.
 * O botão Salvar recebe o foco inicial (o campo de texto é para teclado/
 * toque; pelo controle o usuário navega entre Salvar/Cancelar e usa B/A).
 */
@Composable
fun RenameGameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialName) }

    GamepadDialogScope {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Renomear jogo") },
            text = {
                Column {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    DialogGamepadBar()
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onConfirm(text.trim().ifEmpty { initialName }) },
                    modifier = Modifier.gamepadFocusable(
                        autoFocus = true,
                        onConfirm = { onConfirm(text.trim().ifEmpty { initialName }) },
                        onBack = onDismiss
                    )
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.gamepadFocusable(onConfirm = onDismiss, onBack = onDismiss)
                ) { Text("Cancelar") }
            }
        )
    }
}
