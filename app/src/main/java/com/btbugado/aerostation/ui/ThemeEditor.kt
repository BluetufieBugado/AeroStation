package com.btbugado.aerostation.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.btbugado.aerostation.R
import com.btbugado.aerostation.data.AppAudio
import com.btbugado.aerostation.data.AppTheme
import com.btbugado.aerostation.data.PAD_BUTTON_IDS
import com.btbugado.aerostation.data.PAD_SHAPES
import com.btbugado.aerostation.data.PadButtonSkin
import com.btbugado.aerostation.data.PadSkin
import com.btbugado.aerostation.data.SOUND_SLOTS
import com.btbugado.aerostation.data.THEME_COLOR_KEYS
import com.btbugado.aerostation.data.ThemeAudioConverter
import com.btbugado.aerostation.data.ThemeColors
import com.btbugado.aerostation.data.ThemeEngine
import com.btbugado.aerostation.data.ThemeStore
import com.btbugado.aerostation.data.colorLabelRes
import com.btbugado.aerostation.data.padButtonLabelRes
import com.btbugado.aerostation.data.padShapeLabelRes
import com.btbugado.aerostation.ui.components.LocalGamepadCursor
import com.btbugado.aerostation.ui.components.LocalHideNavForOverlay
import com.btbugado.aerostation.ui.components.LocalHidePadForOverlay
import com.btbugado.aerostation.ui.components.LocalTouchActionsEnabled
import com.btbugado.aerostation.ui.components.PadFace
import com.btbugado.aerostation.ui.components.gamepadFocusable
import com.btbugado.aerostation.ui.theme.AeroGlassBorder
import com.btbugado.aerostation.ui.theme.AeroGlassWhite
import com.btbugado.aerostation.ui.theme.AeroGlassWhiteStrong
import com.btbugado.aerostation.ui.theme.AeroTextPrimary
import com.btbugado.aerostation.ui.theme.AeroTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Editor de tema: cria do zero ou edita um existente. Tudo em memória até
 * o Salvar — as mídias escolhidas já vão pra pasta do tema na hora (a pasta
 * sem theme.json é invisível na lista; cancelar um tema novo apaga ela).
 */
@Composable
fun ThemeEditor(
    initial: AppTheme,
    isNew: Boolean,
    gamepadActive: Boolean,
    onClose: (saved: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember(initial) { mutableStateOf(initial.name) }
    var colors by remember(initial) { mutableStateOf(initial.colors) }
    var bgFile by remember(initial) { mutableStateOf(initial.backgroundImageFile) }
    var bgPath by remember(initial) { mutableStateOf(initial.backgroundImagePath) }
    var bgFit by remember(initial) { mutableStateOf(initial.backgroundFit) }
    var musicFile by remember(initial) { mutableStateOf(initial.musicFile) }
    var soundFiles by remember(initial) { mutableStateOf(initial.soundFiles.toMutableMap()) }
    var padSkins by remember(initial) { mutableStateOf(initial.padSkin.buttons.toMutableMap()) }

    // Qual slot o seletor de arquivo vai preencher ("bg", "music", "sfx:<slot>").
    var pendingSlot by remember { mutableStateOf<String?>(null) }
    // Qual botão do pad o seletor de imagem vai vestir (id ou null).
    var pendingPadId by remember { mutableStateOf<String?>(null) }
    // Conversão em andamento ("música" ou nome do efeito) — bloqueia picks.
    var convertingKind by remember { mutableStateOf<String?>(null) }

    fun updateSkin(id: String, transform: (PadButtonSkin) -> PadButtonSkin) {
        val map = padSkins.toMutableMap()
        map[id] = transform(map[id] ?: PadButtonSkin.auto())
        padSkins = map
    }

    /**
     * Volta UM botão ao padrão de verdade: apaga o arquivo de imagem (se
     * houver) e REMOVE a entrada — sem resto de modo/cor pra assombrar.
     */
    fun resetSkin(id: String) {
        padSkins[id]?.imageFile?.let { ThemeStore.removeMedia(context, initial.id, it) }
        val map = padSkins.toMutableMap()
        map.remove(id)
        padSkins = map
    }

    /** Volta TODOS os botões ao padrão (roupas + arquivos). */
    fun resetAllSkins() {
        ThemeStore.clearPadMedia(context, initial.id)
        padSkins = mutableMapOf()
    }

    fun folderPathOf(fileName: String): String =
        java.io.File(
            java.io.File(context.filesDir, "themes"),
            initial.id
        ).resolve(fileName).absolutePath

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val slot = pendingSlot
        pendingSlot = null
        if (uri == null || slot != "bg") return@rememberLauncherForActivityResult
        val saved = ThemeStore.importMedia(context, initial.id, uri, "bg")
        if (saved != null) {
            bgFile = saved
            bgPath = folderPathOf(saved)
        } else {
            Toast.makeText(context, context.getString(R.string.common_image_failed), Toast.LENGTH_SHORT).show()
        }
    }
    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val slot = pendingSlot
        pendingSlot = null
        if (uri == null || slot == null || slot == "bg") return@rememberLauncherForActivityResult
        // Um nome por slot (music.m4a, move.wav...); a conversão é pesada
        // (decode + AAC) e roda fora da UI, com aviso na tela.
        val kind = if (slot == "music") "music" else slot.removePrefix("sfx:")
        convertingKind = if (slot == "music") context.getString(R.string.theme_converting_music) else soundSlotLabel(context, kind)
        scope.launch(Dispatchers.IO) {
            val saved = ThemeAudioConverter.importAudio(context, initial.id, uri, kind)
            withContext(Dispatchers.Main) {
                convertingKind = null
                if (saved != null) {
                    if (slot == "music") {
                        musicFile = saved
                    } else if (slot.startsWith("sfx:")) {
                        val map = soundFiles.toMutableMap()
                        map[slot.removePrefix("sfx:")] = saved
                        soundFiles = map
                    }
                    Toast.makeText(context, context.getString(R.string.theme_toast_audio_ready), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.theme_toast_audio_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    // Roupa dos botões: PNG com transparência (Ibis Paint etc).
    val padImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val id = pendingPadId
        pendingPadId = null
        if (uri == null || id == null) return@rememberLauncherForActivityResult
        val saved = ThemeStore.importMedia(context, initial.id, uri, "pad_$id")
        if (saved != null) {
            updateSkin(id) { it.copy(mode = PadButtonSkin.MODE_IMAGE, imageFile = saved) }
        } else {
            Toast.makeText(context, context.getString(R.string.common_image_failed), Toast.LENGTH_SHORT).show()
        }
    }

    fun save() {
        val toSave = AppTheme(
            id = initial.id,
            name = name.trim().ifBlank { context.getString(R.string.theme_fallback_untitled) },
            colors = colors,
            backgroundImageFile = bgFile,
            backgroundFit = bgFit,
            musicFile = musicFile,
            soundFiles = soundFiles.toMap(),
            padSkin = PadSkin(padSkins.toMap())
        )
        ThemeStore.save(context, toSave)
        // Editou o tema que está ativo? Aplica na hora (cores + áudio).
        if (ThemeEngine.active.id == toSave.id) {
            ThemeEngine.refresh(context)
            scope.launch(Dispatchers.IO) { AppAudio.applyTheme(context) }
        }
        Toast.makeText(context, context.getString(R.string.theme_toast_saved), Toast.LENGTH_SHORT).show()
        onClose(true)
    }

    fun cancel() {
        if (isNew) {
            // Tema novo nunca salvo: limpa a pasta rascunho (só mídia).
            ThemeStore.delete(context, initial.id)
        }
        onClose(false)
    }

    // LazyColumn (não Column rolável): compõe só o visível — abrir o
    // editor com as 11 fileiras de cor + presets de uma vez travava.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ThemePillButton(
                label = stringResource(R.string.common_back_chevron),
                gamepadAutoFocus = gamepadActive,
                onClick = ::cancel,
                onBack = ::cancel
            )
        }
        item {
            Text(
                text = if (isNew) stringResource(R.string.theme_title_new) else stringResource(R.string.theme_title_edit),
                color = AeroTextPrimary,
                fontSize = 26.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = stringResource(R.string.theme_subtitle_optional),
                color = AeroTextSecondary,
                fontSize = 14.sp
            )
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.theme_field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            SectionTitle(stringResource(R.string.theme_section_preview))
            ThemePreview(name = name.ifBlank { stringResource(R.string.theme_fallback_untitled) }, colors = colors, bgPath = bgPath)
        }
        item {
            SectionTitle(stringResource(R.string.theme_section_colors))
            Text(
                text = stringResource(R.string.theme_hint_colors),
                color = AeroTextSecondary,
                fontSize = 13.sp
            )
        }
        items(THEME_COLOR_KEYS, key = { it.first }) { (key, _) ->
            ColorRow(
                label = stringResource(colorLabelRes(key)),
                argb = colors.get(key),
                onChange = { colors = colors.with(key, it) }
            )
        }
        item {
            SectionTitle(stringResource(R.string.theme_section_bg_image))
            Text(
                text = stringResource(R.string.theme_hint_bg_image),
                color = AeroTextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            MediaRow(
                fileName = bgFile?.let { (if (ThemeStore.isVideoBackground(bgPath)) "🎬 " else "") + it },
                thumbPath = if (bgPath != null && !ThemeStore.isVideoBackground(bgPath)) bgPath else null,
                pickLabel = stringResource(R.string.theme_btn_choose),
                onPick = {
                    pendingSlot = "bg"
                    imagePicker.launch(arrayOf("image/*", "video/*"))
                },
                onClear = bgFile?.let {
                    {
                        ThemeStore.removeMedia(context, initial.id, it)
                        bgFile = null
                        bgPath = null
                    }
                }
            )
            // Preencher cobre tudo (corta o excedente); Ajustar mostra tudo
            // (com faixas se as proporções diferirem). Vale pra foto e vídeo.
            if (bgFile != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemePillButton(
                        label = stringResource(R.string.theme_btn_fill_crop),
                        primary = bgFit != AppTheme.FIT_FIT,
                        onClick = { bgFit = AppTheme.FIT_CROP }
                    )
                    ThemePillButton(
                        label = stringResource(R.string.theme_btn_fill_fit),
                        primary = bgFit == AppTheme.FIT_FIT,
                        onClick = { bgFit = AppTheme.FIT_FIT }
                    )
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.theme_section_bg_music))
            ConvertingNote(kind = convertingKind)
            MediaRow(
                fileName = musicFile,
                thumbPath = null,
                pickLabel = stringResource(R.string.theme_btn_choose_music),
                onPick = {
                    if (convertingKind != null) {
                        Toast.makeText(context, context.getString(R.string.theme_toast_wait_convert), Toast.LENGTH_SHORT).show()
                    } else {
                        pendingSlot = "music"
                        audioPicker.launch(arrayOf("audio/*"))
                    }
                },
                onClear = musicFile?.let {
                    {
                        ThemeStore.removeMedia(context, initial.id, it)
                        musicFile = null
                    }
                }
            )
        }
        item {
            SectionTitle(stringResource(R.string.theme_section_sfx))
            ConvertingNote(kind = convertingKind)
        }
        items(SOUND_SLOTS, key = { it }) { slot ->
            MediaRow(
                title = soundSlotLabel(context, slot),
                fileName = soundFiles[slot],
                thumbPath = null,
                pickLabel = stringResource(R.string.theme_btn_choose),
                onPick = {
                    if (convertingKind != null) {
                        Toast.makeText(context, context.getString(R.string.theme_toast_wait_convert), Toast.LENGTH_SHORT).show()
                    } else {
                        pendingSlot = "sfx:$slot"
                        audioPicker.launch(arrayOf("audio/*"))
                    }
                },
                onClear = soundFiles[slot]?.let { file ->
                    {
                        ThemeStore.removeMedia(context, initial.id, file)
                        val map = soundFiles.toMutableMap()
                        map.remove(slot)
                        soundFiles = map
                    }
                }
            )
        }
        item {
            SectionTitle(stringResource(R.string.theme_section_pad))
            Text(
                text = stringResource(R.string.theme_hint_pad),
                color = AeroTextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ThemePillButton(label = stringResource(R.string.theme_btn_reset_pad), onClick = ::resetAllSkins)
        }
        items(PAD_BUTTON_IDS, key = { it }) { id ->
            val skin = padSkins[id] ?: PadButtonSkin.auto()
            // Caminho absoluto pra prévia (o rascunho guarda só o nome).
            val previewSkin = remember(skin, initial.id) {
                skin.copy().apply {
                    imagePath = skin.imageFile?.let { name ->
                        java.io.File(
                            java.io.File(context.filesDir, "themes"),
                            "${initial.id}/$name"
                        ).takeIf { it.exists() && it.length() > 0L }?.absolutePath
                    }
                }
            }
            PadSkinRow(
                buttonId = id,
                skin = skin,
                previewSkin = previewSkin,
                onMode = { mode ->
                    // "Padrão" redefine de verdade (remove a entrada); os
                    // outros modos ajustam a entrada existente.
                    if (mode == PadButtonSkin.MODE_AUTO) resetSkin(id)
                    else updateSkin(id) { it.copy(mode = mode) }
                },
                onShape = { shape -> updateSkin(id) { it.copy(shape = shape) } },
                onColor = { color -> updateSkin(id) { it.copy(shapeColor = color) } },
                onLabelToggle = { updateSkin(id) { it.copy(showLabel = !it.showLabel) } },
                onPickImage = {
                    pendingPadId = id
                    padImagePicker.launch(arrayOf("image/*"))
                },
                onClearImage = { resetSkin(id) }
            )
        }
        item {
            ThemePillButton(label = stringResource(R.string.theme_btn_save), primary = true, onClick = ::save)
            // Respiro do fim (nav escondida, mas sem grudar na borda).
            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = AeroTextPrimary,
        fontSize = 17.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/** Aviso de conversão em andamento (mp3 -> m4a/wav leva segundos). */
@Composable
private fun ConvertingNote(kind: String?) {
    if (kind == null) return
    Text(
        text = stringResource(R.string.theme_note_converting, kind),
        color = AeroTextPrimary,
        fontSize = 13.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/** Uma cor do tema: amostra + hexadecimal + barrinhas RGBA + paleta pronta. */
@Composable
private fun ColorRow(label: String, argb: Long, onChange: (Long) -> Unit) {
    var hex by remember(argb) { mutableStateOf(argb.toHex()) }
    // Slider mexe num canal sem tocar nos outros; o hex acompanha.
    fun setChannel(shift: Int, value: Int) {
        val mask = 0xFFL shl shift
        val updated = (argb and mask.inv()) or ((value.toLong() and 0xFFL) shl shift)
        hex = updated.toHex()
        onChange(updated)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AeroGlassWhite)
            .border(1.dp, AeroGlassBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(argb))
                    .border(1.dp, AeroGlassBorder, RoundedCornerShape(9.dp))
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(text = label, color = AeroTextPrimary, fontSize = 15.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = hex,
            onValueChange = { typed ->
                val clean = typed.uppercase().filter { it in "0123456789ABCDEF" }.take(8)
                hex = clean
                parseHex(clean)?.let { onChange(it) }
            },
            label = { Text(stringResource(R.string.theme_field_hex)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        ChannelSlider(
            label = "A",
            value = ((argb ushr 24) and 0xFF).toInt(),
            onChange = { setChannel(24, it) }
        )
        ChannelSlider(
            label = "R",
            value = ((argb ushr 16) and 0xFF).toInt(),
            onChange = { setChannel(16, it) }
        )
        ChannelSlider(
            label = "G",
            value = ((argb ushr 8) and 0xFF).toInt(),
            onChange = { setChannel(8, it) }
        )
        ChannelSlider(
            label = "B",
            value = (argb and 0xFF).toInt(),
            onChange = { setChannel(0, it) }
        )
        Spacer(modifier = Modifier.height(4.dp))
        // Paleta pronta, em fileiras de 7.
        for (chunk in PRESET_COLORS.chunked(7)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (preset in chunk) {
                    PresetSwatch(
                        argb = preset,
                        selected = preset == argb,
                        onClick = {
                            hex = preset.toHex()
                            onChange(preset)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** Uma barrinha 0..255 de um canal (A/R/G/B), que nem no estúdio web. */@Composable
private fun ChannelSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = AeroTextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.width(16.dp)
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..255f,
            steps = 254,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value.toString(),
            color = AeroTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp)
        )
    }
}

@Composable
private fun PresetSwatch(argb: Long, selected: Boolean, onClick: () -> Unit) {
    val touchOk = LocalTouchActionsEnabled.current
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(argb))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) Color.White else AeroGlassBorder,
                RoundedCornerShape(8.dp)
            )
            .gamepadFocusable(onConfirm = onClick)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = { if (touchOk) onClick() }
            )
    )
}

/** Letra/símbolo de prévia por botão (espelha o pad de verdade). */
private fun padPreviewLabel(id: String): String = when (id) {
    "a" -> "A"
    "b" -> "B"
    "x" -> "X"
    "y" -> "Y"
    "up" -> "▲"
    "down" -> "▼"
    "left" -> "◀"
    "right" -> "▶"
    "l1" -> "L1"
    "r1" -> "R1"
    else -> "?"
}

/**
 * Cartão de skin de UM botão: prévia ao vivo + modo (Padrão/Forma/Imagem)
 * e, conforme o modo, forma + cor + letra ou imagem do usuário.
 */
@Composable
private fun PadSkinRow(
    buttonId: String,
    skin: PadButtonSkin,
    previewSkin: PadButtonSkin,
    onMode: (String) -> Unit,
    onShape: (String) -> Unit,
    onColor: (Long) -> Unit,
    onLabelToggle: () -> Unit,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AeroGlassWhite)
            .border(1.dp, AeroGlassBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Prévia ao vivo (rascunho, não o tema ativo).
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(AeroGlassWhiteStrong, AeroGlassWhite)
                        )
                    )
                    .border(1.dp, AeroGlassBorder, RoundedCornerShape(28.dp))
                    .padding(6.dp)
            ) {
                PadFace(
                    buttonId = null,
                    label = padPreviewLabel(buttonId),
                    labelColor = Color.White,
                    fontSize = 15,
                    skinOverride = previewSkin
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = padButtonLabelRes(buttonId)?.let { stringResource(it) } ?: buttonId.uppercase(),
                color = AeroTextPrimary,
                fontSize = 15.sp
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemePillButton(
                label = stringResource(R.string.theme_mode_default),
                primary = skin.mode == PadButtonSkin.MODE_AUTO,
                onClick = { onMode(PadButtonSkin.MODE_AUTO) }
            )
            ThemePillButton(
                label = stringResource(R.string.theme_mode_shape),
                primary = skin.mode == PadButtonSkin.MODE_SHAPE,
                onClick = { onMode(PadButtonSkin.MODE_SHAPE) }
            )
            ThemePillButton(
                label = stringResource(R.string.theme_mode_image),
                primary = skin.mode == PadButtonSkin.MODE_IMAGE,
                onClick = { onMode(PadButtonSkin.MODE_IMAGE) }
            )
        }
        if (skin.mode == PadButtonSkin.MODE_SHAPE) {
            Spacer(modifier = Modifier.height(10.dp))
            for (chunk in PAD_SHAPES.chunked(3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (shape in chunk) {
                        ThemePillButton(
                            label = stringResource(padShapeLabelRes(shape)),
                            primary = skin.shape == shape,
                            onClick = { onShape(shape) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (preset in PRESET_COLORS.take(7)) {
                    PresetSwatch(
                        argb = preset,
                        selected = skin.shapeColor == preset,
                        onClick = { onColor(preset) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            ThemePillButton(
                label = if (skin.showLabel) stringResource(R.string.theme_label_yes) else stringResource(R.string.theme_label_no),
                onClick = onLabelToggle
            )
        }
        if (skin.mode == PadButtonSkin.MODE_IMAGE) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = skin.imageFile
                    ?: stringResource(R.string.theme_hint_no_pad_image),
                color = AeroTextSecondary,
                fontSize = 13.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemePillButton(label = stringResource(R.string.theme_btn_choose_pad_image), onClick = onPickImage)
                if (skin.imageFile != null) {
                    ThemePillButton(label = stringResource(R.string.theme_mode_default), onClick = onClearImage)
                }
            }
        }
    }
}

/** Linha de mídia: nome do arquivo (ou Padrão) + Escolher/Padrão. */
@Composable
private fun MediaRow(
    fileName: String?,
    thumbPath: String?,
    pickLabel: String,
    onPick: () -> Unit,
    onClear: (() -> Unit)?,
    title: String? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AeroGlassWhite)
            .border(1.dp, AeroGlassBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        if (thumbPath != null) {
            AsyncImage(
                model = thumbPath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(9.dp))
            )
            Spacer(modifier = Modifier.size(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            if (title != null) {
                Text(text = title, color = AeroTextPrimary, fontSize = 15.sp)
            }
            Text(
                text = fileName ?: stringResource(R.string.theme_media_default),
                color = AeroTextSecondary,
                fontSize = 13.sp,
                maxLines = 1
            )
        }
        ThemePillButton(label = pickLabel, onClick = onPick)
        if (onClear != null) {
            Spacer(modifier = Modifier.size(8.dp))
            ThemePillButton(label = stringResource(R.string.theme_mode_default), onClick = onClear)
        }
    }
}

/** Mini-amostra do tema com as cores do rascunho (não do tema ativo). */
@Composable
private fun ThemePreview(name: String, colors: ThemeColors, bgPath: String?) {
    val bgIsVideo = bgPath != null && ThemeStore.isVideoBackground(bgPath)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Color(colors.glassBorder), RoundedCornerShape(16.dp))
    ) {
        if (bgPath != null && !bgIsVideo) {
            AsyncImage(
                model = bgPath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(colors.skyTop),
                                Color(colors.skyMid),
                                Color(colors.skyBottom)
                            )
                        )
                    )
            )
        }
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = name, color = Color(colors.textPrimary), fontSize = 18.sp)
            Text(
                text = if (bgIsVideo) stringResource(R.string.theme_preview_video) else stringResource(R.string.theme_preview_tagline),
                color = Color(colors.textSecondary),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20))
                        .background(Color(colors.glassWhiteStrong))
                        .border(1.dp, Color(colors.glassBorder), RoundedCornerShape(20))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(text = stringResource(R.string.theme_preview_glass), color = Color(colors.textPrimary), fontSize = 13.sp)
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(colors.leafGreen))
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(colors.accentOrange))
                )
            }
        }
    }
}

private fun Long.toHex(): String = this.toString(16).uppercase().padStart(8, '0')

private fun parseHex(s: String): Long? {
    val clean = s.trim().removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return null
    if (!clean.all { it in "0123456789ABCDEFabcdef" }) return null
    val full = if (clean.length == 6) "FF$clean" else clean
    return full.toLongOrNull(16)
}

/** Paleta pronta pro editor (inclui os vidros translúcidos). */
private val PRESET_COLORS = listOf(
    0xFFFFFFFFL, 0xFF000000L, 0xFF1E4E8CL, 0xFF2E86ABL, 0xFF6FD6C4L,
    0xFF8FD14FL, 0xFFFFA451L, 0xFFE5484DL, 0xFF9B5DE5L, 0xFFF15BB5L,
    0xFFFFE440L, 0xFF00BBF9L, 0x33FFFFFFL, 0x66FFFFFFL, 0x80FFFFFFL,
    0xFFE8FFF8L, 0xB3FFFFFFL, 0xFF2B2D42L, 0xFF121212L, 0xFF5A189AL
)

private fun soundSlotLabel(context: Context, slot: String): String = when (slot) {
    "move" -> context.getString(R.string.theme_sfx_move)
    "confirm" -> context.getString(R.string.theme_sfx_confirm)
    "open" -> context.getString(R.string.theme_sfx_open)
    "close" -> context.getString(R.string.theme_sfx_close)
    "launch" -> context.getString(R.string.theme_sfx_launch)
    else -> slot
}

/**
 * Editor em TELA CHEIA (renderizado na raiz do app, acima dos Ajustes):
 * a página de trás sai da composição, então aqui é só o conteúdo sobre o
 * fundo animado da raiz. Esconde BottomNav e pad virtual enquanto aberto.
 * Voltar/Salvar devolvem pra lista de temas.
 */
@Composable
fun ThemeEditorOverlay(
    initial: AppTheme,
    isNew: Boolean,
    gamepadActive: Boolean,
    onClose: (saved: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // Fullscreen: sem nav, sem pad (o editor é touch/teclado + foco físico).
    // Limpa o foco da página que saiu de cena pra não sobrar cursor fantasma.
    val setNavHidden = LocalHideNavForOverlay.current
    val setPadHidden = LocalHidePadForOverlay.current
    val gamepadCursor = LocalGamepadCursor.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        gamepadCursor?.clearAll()
        runCatching { focusManager.clearFocus(force = true) }
        setNavHidden?.invoke(true)
        setPadHidden?.invoke(true)
    }
    DisposableEffect(Unit) {
        onDispose {
            setNavHidden?.invoke(false)
            setPadHidden?.invoke(false)
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 20.dp, start = 32.dp, end = 32.dp, bottom = 24.dp)
        ) {
            ThemeEditor(
                initial = initial,
                isNew = isNew,
                gamepadActive = gamepadActive,
                onClose = onClose
            )
        }
    }
}
