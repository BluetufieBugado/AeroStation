package com.btbugado.aerostation.data

/** De onde veio a capa exibida atualmente pra um jogo. */
enum class ArtSource { NONE, AUTO, CUSTOM }

/**
 * Customização de um jogo específico, guardada por cima dos dados
 * que vêm do scan (que são só o que existe no arquivo em si).
 *
 * @param customName nome escolhido pelo usuário, sobrepõe o nome do arquivo
 * @param artUri URL remota (capa automática) ou caminho local (capa customizada)
 * @param artSource indica se a capa atual é automática, customizada, ou nenhuma
 * @param console plataforma forçada pelo usuário (null = automática da extensão).
 *   Resolve formatos ambíguos (.iso/.bin/.chd valem pra PS1, PS2, PSP...).
 */
data class GameOverride(
    val customName: String? = null,
    val artUri: String? = null,
    val artSource: ArtSource = ArtSource.NONE,
    val console: String? = null
)
