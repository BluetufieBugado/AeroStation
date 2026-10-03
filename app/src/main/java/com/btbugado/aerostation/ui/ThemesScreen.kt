package com.btbugado.aerostation.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.btbugado.aerostation.R
import androidx.core.content.FileProvider
import com.btbugado.aerostation.data.AppAudio
import com.btbugado.aerostation.data.AppTheme
import com.btbugado.aerostation.data.THEME_COLOR_KEYS
import com.btbugado.aerostation.data.ThemeEngine
import com.btbugado.aerostation.data.ThemeStore
import com.btbugado.aerostation.ui.components.DialogGamepadBar
import com.btbugado.aerostation.ui.components.GamepadDialogScope
import com.btbugado.aerostation.ui.components.LocalTouchActionsEnabled
import com.btbugado.aerostation.ui.components.gamepadFocusable
import com.btbugado.aerostation.ui.theme.AeroGlassBorder
import com.btbugado.aerostation.ui.theme.AeroGlassWhite
import com.btbugado.aerostation.ui.theme.AeroGlassWhiteStrong
import com.btbugado.aerostation.ui.theme.AeroTextPrimary
import com.btbugado.aerostation.ui.theme.AeroTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Abre o editor de tema em tela cheia (providenciado na raiz do app).
 * Parâmetros: tema a editar + se é novo (rascunho, limpa ao cancelar).
 */
val LocalOpenThemeEditor = compositionLocalOf<((AppTheme, Boolean) -> Unit)?> { null }

/**
 * Incrementado cada vez que o editor fullscreen fecha: a lista recarrega.
 */
val LocalThemeEditorClosedTick = compositionLocalOf { 0 }

/**
 * Conteúdo da categoria "Temas" nos Ajustes: lista de temas instalados
 * (ativar, editar, duplicar, apagar, exportar) + criar do zero e importar.
 * O editor abre em tela cheia pela [LocalOpenThemeEditor].
 */
@Composable
fun ThemesSettings(gamepadActive: Boolean, selectedCategory: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshTick by remember { mutableStateOf(0) }
    var pendingDelete by remember { mutableStateOf<AppTheme?>(null) }
    // Importação em andamento (zip + normalização de áudio demoram):
    // sem isso o usuário toca duas vezes e importa duplicado.
    var importing by remember { mutableStateOf(false) }
    val openEditor = LocalOpenThemeEditor.current
    // Editor fechou? Recarrega a lista (tema novo/editado/apagado).
    val closedTick = LocalThemeEditorClosedTick.current

    val themes = remember(refreshTick, closedTick) { ThemeStore.list(context) }
    val activeId = ThemeEngine.active.id

    // Importar .aerotheme.zip de qualquer gerenciador de arquivos.
    val zipPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null || importing) return@rememberLauncherForActivityResult
        importing = true
        scope.launch(Dispatchers.IO) {
            val result = ThemeStore.importFromZip(context, uri)
            withContext(Dispatchers.Main) {
                importing = false
                when (result) {
                    is ThemeStore.ThemeImportResult.Ok -> {
                        // Conta o que chegou de verdade (cores/fundo/música +
                        // skins): se o pad vier zerado, o zip não trouxe.
                        val padCount = result.theme.padSkin.buttons.size
                        val padInfo = if (padCount > 0) {
                            context.getString(R.string.theme_import_pad_count, padCount)
                        } else {
                            context.getString(R.string.theme_import_no_pad)
                        }
                        Toast.makeText(
                            context,
                            context.getString(R.string.theme_toast_imported, result.theme.name, padInfo),
                            Toast.LENGTH_LONG
                        ).show()
                        refreshTick++
                    }
                    is ThemeStore.ThemeImportResult.Fail -> {
                        Toast.makeText(context, result.reason, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun shareTheme(theme: AppTheme) {
        scope.launch(Dispatchers.IO) {
            val zip = ThemeStore.exportToZip(context, theme.id)
            withContext(Dispatchers.Main) {
                if (zip == null) {
                    Toast.makeText(context, context.getString(R.string.theme_toast_export_failed), Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                val uri = runCatching {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        zip
                    )
                }.getOrNull()
                if (uri == null) {
                    Toast.makeText(context, context.getString(R.string.theme_toast_export_failed), Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching {
                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.theme_share_title)))
                }.onFailure {
                    Toast.makeText(context, context.getString(R.string.common_share_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // O editor abre em tela cheia (raiz do app): aqui é só a lista.
    Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.theme_title_list),
                color = AeroTextPrimary,
                fontSize = 26.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = stringResource(R.string.theme_subtitle_list),
                color = AeroTextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 18.dp)
            )

            ThemePillButton(
                label = stringResource(R.string.theme_btn_new),
                gamepadAutoFocus = gamepadActive && selectedCategory == "Temas",
                onClick = {
                    openEditor?.invoke(
                        AppTheme(id = ThemeStore.newId(), name = context.getString(R.string.theme_default_new_name)),
                        true
                    )
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            ThemePillButton(
                label = if (importing) stringResource(R.string.theme_btn_importing) else stringResource(R.string.theme_btn_import),
                onClick = {
                    if (!importing) zipPicker.launch(arrayOf("*/*"))
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            for (theme in themes) {
                ThemeCard(
                    theme = theme,
                    isActive = theme.id == activeId,
                    onActivate = {
                        ThemeEngine.activate(context, theme)
                        scope.launch(Dispatchers.IO) {
                            AppAudio.applyTheme(context)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, context.getString(R.string.theme_toast_applied, theme.name), Toast.LENGTH_SHORT).show()
                            }
                        }
                        refreshTick++
                    },
                    onEdit = {
                        if (theme.id == ThemeStore.DEFAULT_ID) {
                            // O padrão não mora em disco: edita uma cópia.
                            val copy = ThemeStore.duplicate(context, theme.id)
                            if (copy != null) {
                                Toast.makeText(context, context.getString(R.string.theme_toast_copy_for_edit), Toast.LENGTH_SHORT).show()
                                openEditor?.invoke(copy, false)
                                refreshTick++
                            }
                        } else {
                            openEditor?.invoke(theme, false)
                        }
                    },
                    onDuplicate = {
                        ThemeStore.duplicate(context, theme.id)
                        Toast.makeText(context, context.getString(R.string.theme_toast_duplicated), Toast.LENGTH_SHORT).show()
                        refreshTick++
                    },
                    onDelete = { pendingDelete = theme },
                    onExport = { shareTheme(theme) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            // Respiro pro fim não colar na BottomNav.
            Spacer(modifier = Modifier.height(60.dp))
    }

    // Confirmação de exclusão.
    pendingDelete?.let { theme ->
        GamepadDialogScope {
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text(stringResource(R.string.theme_dialog_delete_title)) },
                text = { Text(stringResource(R.string.theme_dialog_delete_text, theme.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            ThemeStore.delete(context, theme.id)
                            ThemeEngine.refresh(context)
                            scope.launch(Dispatchers.IO) {
                                AppAudio.applyTheme(context)
                            }
                            pendingDelete = null
                            refreshTick++
                        },
                        modifier = Modifier.gamepadFocusable(
                            autoFocus = true,
                            onConfirm = {
                                ThemeStore.delete(context, theme.id)
                                ThemeEngine.refresh(context)
                                scope.launch(Dispatchers.IO) {
                                    AppAudio.applyTheme(context)
                                }
                                pendingDelete = null
                                refreshTick++
                            }
                        )
                    ) { Text(stringResource(R.string.common_delete)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = { pendingDelete = null },
                        modifier = Modifier.gamepadFocusable(
                            onConfirm = { pendingDelete = null },
                            onBack = { pendingDelete = null }
                        )
                    ) { Text(stringResource(R.string.common_cancel)) }
                },
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

/** Cartão de um tema: nome, fitinha da paleta e as 4 ações. */
@Composable
private fun ThemeCard(
    theme: AppTheme,
    isActive: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(AeroGlassWhiteStrong, AeroGlassWhite)))
            .border(1.dp, AeroGlassBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = theme.name,
                color = AeroTextPrimary,
                fontSize = 17.sp,
                modifier = Modifier.weight(1f)
            )
            if (isActive) {
                Text(text = stringResource(R.string.theme_badge_active), color = AeroTextSecondary, fontSize = 13.sp)
            }
        }
        // Fitinha com as 11 cores — a "cara" do tema num relance.
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 10.dp, bottom = 12.dp)
        ) {
            for ((key, _) in THEME_COLOR_KEYS) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(theme.colors.get(key)))
                        .border(1.dp, AeroGlassBorder, RoundedCornerShape(5.dp))
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isActive) {
                ThemePillButton(label = stringResource(R.string.common_activate), primary = true, onClick = onActivate)
            }
            ThemePillButton(label = stringResource(R.string.common_edit), onClick = onEdit)
            ThemePillButton(label = stringResource(R.string.common_export), onClick = onExport)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemePillButton(label = stringResource(R.string.common_duplicate), onClick = onDuplicate)
            if (theme.id != ThemeStore.DEFAULT_ID) {
                ThemePillButton(label = stringResource(R.string.common_delete), onClick = onDelete)
            }
        }
    }
}

/** Pílula de ação do editor/lista (primária = destaque). */
@Composable
fun ThemePillButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    gamepadAutoFocus: Boolean = false,
    onBack: (() -> Unit)? = null
) {
    val touchOk = LocalTouchActionsEnabled.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (primary) AeroGlassWhiteStrong else AeroGlassWhite)
            .border(1.dp, AeroGlassBorder, RoundedCornerShape(50))
            .gamepadFocusable(autoFocus = gamepadAutoFocus, onConfirm = onClick, onBack = onBack)
            .clickable { if (touchOk) onClick() }
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            color = AeroTextPrimary,
            fontSize = 14.sp
        )
    }
}
