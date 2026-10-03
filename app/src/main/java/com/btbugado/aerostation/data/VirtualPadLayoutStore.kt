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
    val scale: Float = 1f,
    /** Tamanho por grupo (multiplica a global): direcional, ações, ombros. */
    val dpadScale: Float = 1f,
    val actionsScale: Float = 1f,
    val shoulderScale: Float = 1f,
    /** Opacidade geral dos controles (1 = sólido). Ajuda em PNG chamativo. */
    val padOpacity: Float = 1f
) {
    companion object {
        const val MIN_SCALE = 0.75f
        const val MAX_SCALE = 1.4f
        const val MIN_GROUP_SCALE = 0.6f
        const val MAX_GROUP_SCALE = 1.6f
        const val MIN_OPACITY = 0.3f
    }

    val safeScale: Float get() = scale.coerceIn(MIN_SCALE, MAX_SCALE)
    val safeDpadScale: Float get() = dpadScale.coerceIn(MIN_GROUP_SCALE, MAX_GROUP_SCALE)
    val safeActionsScale: Float get() = actionsScale.coerceIn(MIN_GROUP_SCALE, MAX_GROUP_SCALE)
    val safeShoulderScale: Float get() = shoulderScale.coerceIn(MIN_GROUP_SCALE, MAX_GROUP_SCALE)
    val safePadOpacity: Float get() = padOpacity.coerceIn(MIN_OPACITY, 1f)
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
            scale = prefs.getFloat("scale", 1f),
            dpadScale = prefs.getFloat("dpadScale", 1f),
            actionsScale = prefs.getFloat("actionsScale", 1f),
            shoulderScale = prefs.getFloat("shoulderScale", 1f),
            padOpacity = prefs.getFloat("padOpacity", 1f)
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
            .putFloat("dpadScale", layout.dpadScale)
            .putFloat("actionsScale", layout.actionsScale)
            .putFloat("shoulderScale", layout.shoulderScale)
            .putFloat("padOpacity", layout.padOpacity)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
