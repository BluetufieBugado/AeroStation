package com.btbugado.aerostation.data

import android.content.Context

/**
 * Posição e tamanho do controle virtual na tela principal.
 *
 * As posições são DELTAS em dp somados à posição padrão de cada grupo
 * (D-pad, botões, L1, R1). Delta (0,0) = posição padrão. Deltas sobrevivem
 * a rotações porque a base é recalculada a cada composição; a renderização
 * limita (clamp) o resultado para dentro da tela.
 */
data class VirtualPadLayout(
    val dpadDx: Float = 0f,
    val dpadDy: Float = 0f,
    val actionsDx: Float = 0f,
    val actionsDy: Float = 0f,
    val l1Dx: Float = 0f,
    val l1Dy: Float = 0f,
    val r1Dx: Float = 0f,
    val r1Dy: Float = 0f,
    /** Escala dos botões (1 = tamanho padrão). */
    val scale: Float = 1f
) {
    companion object {
        const val MIN_SCALE = 0.75f
        const val MAX_SCALE = 1.4f
    }

    val safeScale: Float get() = scale.coerceIn(MIN_SCALE, MAX_SCALE)
}

/** Guarda o layout do controle virtual. */
object VirtualPadLayoutStore {
    private const val PREFS = "virtual_pad_layout"

    fun load(context: Context): VirtualPadLayout {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return VirtualPadLayout(
            dpadDx = prefs.getFloat("dpadDx", 0f),
            dpadDy = prefs.getFloat("dpadDy", 0f),
            actionsDx = prefs.getFloat("actionsDx", 0f),
            actionsDy = prefs.getFloat("actionsDy", 0f),
            l1Dx = prefs.getFloat("l1Dx", 0f),
            l1Dy = prefs.getFloat("l1Dy", 0f),
            r1Dx = prefs.getFloat("r1Dx", 0f),
            r1Dy = prefs.getFloat("r1Dy", 0f),
            scale = prefs.getFloat("scale", 1f)
        )
    }

    fun save(context: Context, layout: VirtualPadLayout) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat("dpadDx", layout.dpadDx)
            .putFloat("dpadDy", layout.dpadDy)
            .putFloat("actionsDx", layout.actionsDx)
            .putFloat("actionsDy", layout.actionsDy)
            .putFloat("l1Dx", layout.l1Dx)
            .putFloat("l1Dy", layout.l1Dy)
            .putFloat("r1Dx", layout.r1Dx)
            .putFloat("r1Dy", layout.r1Dy)
            .putFloat("scale", layout.scale)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
