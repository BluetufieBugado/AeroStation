package com.btbugado.aerostation.data

/**
 * Um jogo já com as customizações aplicadas por cima — é o que a UI
 * realmente exibe. [game] continua sendo a verdade "crua" vinda do scan.
 */
data class DisplayGame(
    val game: Game,
    val displayName: String,
    val artUrl: String?
)

fun Game.withOverride(override: GameOverride?): DisplayGame = DisplayGame(
    game = this,
    displayName = override?.customName ?: name,
    artUrl = override?.artUri
)
