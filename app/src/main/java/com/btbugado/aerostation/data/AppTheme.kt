package com.btbugado.aerostation.data

import android.content.Context
import com.btbugado.aerostation.R
import org.json.JSONObject

/**
 * Um tema do AeroStation: paleta de cores + mídia opcional.
 *
 * Os campos de mídia guardam o CAMINHO ABSOLUTO resolvido (não serializado):
 * - [backgroundImagePath]: foto de fundo (desenhada atrás do vidro).
 * - [musicPath]: música de fundo em looping.
 * - [soundPaths]: efeitos por slot ("move", "confirm", "open", "close",
 *   "launch"). Slot ausente = som padrão sintetizado.
 *
 * No `theme.json` só vão nomes de arquivo relativos (a pasta do tema é
 * autocontida e vira um .zip na exportação).
 */
data class AppTheme(
    val id: String,
    val name: String,
    val colors: ThemeColors = ThemeColors(),
    /** Nome do arquivo da imagem dentro da pasta do tema (ou null). */
    val backgroundImageFile: String? = null,
    /**
     * Como o fundo preenche a tela: "crop" (cobre tudo, corta o excedente)
     * ou "fit" (mostra tudo, com faixas se as proporções diferirem).
     * Vale pra foto, GIF e vídeo.
     */
    val backgroundFit: String = FIT_CROP,
    /** Nome do arquivo da música dentro da pasta do tema (ou null). */
    val musicFile: String? = null,
    /** Slot -> nome do arquivo dentro da pasta do tema. */
    val soundFiles: Map<String, String> = emptyMap(),
    /** Skins dos botões do controle virtual (id ausente = padrão). */
    val padSkin: PadSkin = PadSkin(),
    // ---- resolvidos (nunca serializados) ----
    var backgroundImagePath: String? = null,
    var musicPath: String? = null,
    var soundPaths: Map<String, String> = emptyMap()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("colors", colors.toJson())
        .put("backgroundImage", backgroundImageFile ?: JSONObject.NULL)
        .put("backgroundFit", backgroundFit)
        .put("music", musicFile ?: JSONObject.NULL)
        .put("sounds", JSONObject().apply {
            for ((slot, file) in soundFiles) put(slot, file)
        })
        .put("pad", padSkin.toJson())

    companion object {
        const val FIT_CROP = "crop"
        const val FIT_FIT = "fit"

        /** Normaliza ("fit" ou qualquer outra coisa vira crop). */
        fun normalizeFit(value: String?): String =
            if (value == FIT_FIT) FIT_FIT else FIT_CROP

        /** O Frutiger Aero de fábrica — fallback de tudo e base do "criar do zero". */
        fun default(): AppTheme = default(null)

        /** Nome traduzido quando há contexto (listas); sem contexto, PT. */
        fun default(context: Context?): AppTheme = AppTheme(
            id = ThemeStore.DEFAULT_ID,
            name = context?.getString(R.string.theme_name_default) ?: "Aero Padrão"
        )

        fun fromJson(obj: JSONObject, fallbackName: String = "Sem nome"): AppTheme {
            val sounds = mutableMapOf<String, String>()
            val soundsObj = obj.optJSONObject("sounds")
            if (soundsObj != null) {
                for (slot in SOUND_SLOTS) {
                    soundsObj.optString(slot).takeIf { it.isNotEmpty() }?.let {
                        sounds[slot] = it
                    }
                }
            }
            return AppTheme(
                id = obj.optString("id", ThemeStore.DEFAULT_ID),
                name = obj.optString("name", fallbackName),
                colors = ThemeColors.fromJson(obj.optJSONObject("colors") ?: JSONObject()),
                backgroundImageFile = obj.optString("backgroundImage").takeIf { it.isNotEmpty() },
                backgroundFit = normalizeFit(obj.optString("backgroundFit", FIT_CROP)),
                musicFile = obj.optString("music").takeIf { it.isNotEmpty() },
                soundFiles = sounds,
                padSkin = PadSkin.fromJson(obj.optJSONObject("pad"))
            )
        }
    }
}

/** Slots de efeito que um tema pode substituir. */
val SOUND_SLOTS = listOf("move", "confirm", "open", "close", "launch")

/**
 * Paleta do tema como ARGB (Long). Os valores padrão são o Aero atual,
 * então o tema de fábrica é pixel a pixel igual ao app de hoje.
 */
data class ThemeColors(
    val skyTop: Long = 0xFF1E4E8CL,
    val skyMid: Long = 0xFF2E86ABL,
    val skyBottom: Long = 0xFF6FD6C4L,
    val leafGreen: Long = 0xFF8FD14FL,
    val glassWhite: Long = 0x33FFFFFFL,
    val glassWhiteStrong: Long = 0x66FFFFFFL,
    val glassBorder: Long = 0x80FFFFFFL,
    val highlight: Long = 0xFFE8FFF8L,
    val textPrimary: Long = 0xFFFFFFFFL,
    val textSecondary: Long = 0xB3FFFFFFL,
    val accentOrange: Long = 0xFFFFA451L
) {
    fun toJson(): JSONObject = JSONObject()
        .put("skyTop", skyTop)
        .put("skyMid", skyMid)
        .put("skyBottom", skyBottom)
        .put("leafGreen", leafGreen)
        .put("glassWhite", glassWhite)
        .put("glassWhiteStrong", glassWhiteStrong)
        .put("glassBorder", glassBorder)
        .put("highlight", highlight)
        .put("textPrimary", textPrimary)
        .put("textSecondary", textSecondary)
        .put("accentOrange", accentOrange)

    companion object {
        fun fromJson(obj: JSONObject): ThemeColors = ThemeColors(
            skyTop = obj.optLong("skyTop", 0xFF1E4E8CL),
            skyMid = obj.optLong("skyMid", 0xFF2E86ABL),
            skyBottom = obj.optLong("skyBottom", 0xFF6FD6C4L),
            leafGreen = obj.optLong("leafGreen", 0xFF8FD14FL),
            glassWhite = obj.optLong("glassWhite", 0x33FFFFFFL),
            glassWhiteStrong = obj.optLong("glassWhiteStrong", 0x66FFFFFFL),
            glassBorder = obj.optLong("glassBorder", 0x80FFFFFFL),
            highlight = obj.optLong("highlight", 0xFFE8FFF8L),
            textPrimary = obj.optLong("textPrimary", 0xFFFFFFFFL),
            textSecondary = obj.optLong("textSecondary", 0xB3FFFFFFL),
            accentOrange = obj.optLong("accentOrange", 0xFFFFA451L)
        )
    }

    /** Acesso por chave pra montar o editor sem 11 branches. */
    fun get(key: String): Long = when (key) {
        "skyTop" -> skyTop
        "skyMid" -> skyMid
        "skyBottom" -> skyBottom
        "leafGreen" -> leafGreen
        "glassWhite" -> glassWhite
        "glassWhiteStrong" -> glassWhiteStrong
        "glassBorder" -> glassBorder
        "highlight" -> highlight
        "textPrimary" -> textPrimary
        "textSecondary" -> textSecondary
        else -> accentOrange
    }

    fun with(key: String, value: Long): ThemeColors = when (key) {
        "skyTop" -> copy(skyTop = value)
        "skyMid" -> copy(skyMid = value)
        "skyBottom" -> copy(skyBottom = value)
        "leafGreen" -> copy(leafGreen = value)
        "glassWhite" -> copy(glassWhite = value)
        "glassWhiteStrong" -> copy(glassWhiteStrong = value)
        "glassBorder" -> copy(glassBorder = value)
        "highlight" -> copy(highlight = value)
        "textPrimary" -> copy(textPrimary = value)
        "textSecondary" -> copy(textSecondary = value)
        else -> copy(accentOrange = value)
    }
}

/** Ids skináveis do controle virtual (overlay + barra dos dialogs). */
val PAD_BUTTON_IDS = listOf("a", "b", "x", "y", "up", "down", "left", "right", "l1", "r1")

/** Rótulos amigáveis pros ids acima (editor): ids de string (traduzidos).
 * Null = rótulo universal (L1/R1) ou id desconhecido: mostra o próprio id. */
fun padButtonLabelRes(id: String): Int? = when (id) {
    "a" -> R.string.theme_pad_a
    "b" -> R.string.theme_pad_b
    "x" -> R.string.theme_pad_x
    "y" -> R.string.theme_pad_y
    "up" -> R.string.theme_pad_up
    "down" -> R.string.theme_pad_down
    "left" -> R.string.theme_pad_left
    "right" -> R.string.theme_pad_right
    else -> null
}

/** Formas desenhadas em código (sem imagem). */
val PAD_SHAPES = listOf("circle", "square", "triangle", "diamond", "star")

/** Rótulos das formas (editor): ids de string (traduzidos). */
fun padShapeLabelRes(shape: String): Int = when (shape) {
    "circle" -> R.string.theme_shape_circle
    "square" -> R.string.theme_shape_square
    "triangle" -> R.string.theme_shape_triangle
    "diamond" -> R.string.theme_shape_diamond
    "star" -> R.string.theme_shape_star
    else -> R.string.theme_fallback_untitled
}

/**
 * Skin de UM botão do controle virtual:
 * - mode "auto": visual atual (vidro + letra colorida).
 * - mode "shape": forma desenhada na [shapeColor], com ou sem letra.
 * - mode "image": PNG do usuário (fundo transparente, feito no Ibis etc);
 *   cobre o botão inteiro — a letra vai desligada (o desenho já tem tudo).
 */
data class PadButtonSkin(
    val mode: String = MODE_AUTO,
    val shape: String = "circle",
    val shapeColor: Long = 0xFFFFFFFFL,
    val showLabel: Boolean = true,
    /** Nome do arquivo dentro da pasta do tema (ou null). */
    val imageFile: String? = null,
    /** Caminho absoluto resolvido (nunca serializado). */
    var imagePath: String? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("mode", mode)
        .put("shape", shape)
        .put("color", shapeColor)
        .put("label", showLabel)
        .put("image", imageFile ?: JSONObject.NULL)

    companion object {
        const val MODE_AUTO = "auto"
        const val MODE_SHAPE = "shape"
        const val MODE_IMAGE = "image"

        fun auto(): PadButtonSkin = PadButtonSkin()

        fun fromJson(obj: JSONObject): PadButtonSkin {
            val mode = obj.optString("mode", MODE_AUTO)
            return PadButtonSkin(
                mode = when (mode) {
                    MODE_SHAPE, MODE_IMAGE -> mode
                    else -> MODE_AUTO
                },
                shape = obj.optString("shape", "circle").takeIf { it in PAD_SHAPES } ?: "circle",
                shapeColor = obj.optLong("color", 0xFFFFFFFFL),
                showLabel = obj.optBoolean("label", true),
                imageFile = obj.optString("image").takeIf { it.isNotEmpty() }
            )
        }
    }
}

/** Skins dos botões: id -> skin. Id ausente = auto. */
data class PadSkin(
    val buttons: Map<String, PadButtonSkin> = emptyMap()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        for ((id, skin) in buttons) {
            if (id in PAD_BUTTON_IDS) put(id, skin.toJson())
        }
    }

    companion object {
        fun fromJson(obj: JSONObject?): PadSkin {
            if (obj == null) return PadSkin()
            val map = mutableMapOf<String, PadButtonSkin>()
            for (id in PAD_BUTTON_IDS) {
                obj.optJSONObject(id)?.let {
                    runCatching { map[id] = PadButtonSkin.fromJson(it) }
                }
            }
            return PadSkin(map)
        }
    }

    fun forId(id: String): PadButtonSkin =
        buttons[id] ?: PadButtonSkin.auto()
}

/** As 11 chaves de cor (o rótulo vai por [colorLabelRes], traduzido). */
val THEME_COLOR_KEYS = listOf(
    "skyTop" to "Céu (topo)",
    "skyMid" to "Céu (meio)",
    "skyBottom" to "Água (base)",
    "leafGreen" to "Verde folha",
    "glassWhite" to "Vidro",
    "glassWhiteStrong" to "Vidro forte",
    "glassBorder" to "Borda do vidro",
    "highlight" to "Brilho",
    "textPrimary" to "Texto principal",
    "textSecondary" to "Texto secundário",
    "accentOrange" to "Laranja destaque"
)

/** Rótulo traduzido de cada chave de cor (o par acima só carrega a chave). */
fun colorLabelRes(key: String): Int = when (key) {
    "skyTop" -> R.string.theme_color_sky_top
    "skyMid" -> R.string.theme_color_sky_mid
    "skyBottom" -> R.string.theme_color_sky_bottom
    "leafGreen" -> R.string.theme_color_leaf
    "glassWhite" -> R.string.theme_color_glass
    "glassWhiteStrong" -> R.string.theme_color_glass_strong
    "glassBorder" -> R.string.theme_color_glass_border
    "highlight" -> R.string.theme_color_highlight
    "textPrimary" -> R.string.theme_color_text_primary
    "textSecondary" -> R.string.theme_color_text_secondary
    "accentOrange" -> R.string.theme_color_accent
    else -> R.string.theme_fallback_untitled
}
