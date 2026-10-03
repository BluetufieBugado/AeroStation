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
 * @param storeAppId ID do jogo na loja pro boot externo por ID (GameNative:
 *   Steam AppID etc). Null = não mapeado (pede pra definir).
 * @param storeSource loja do ID acima (STEAM/EPIC/GOG/AMAZON). Null = STEAM.
 */
data class GameOverride(
    val customName: String? = null,
    val artUri: String? = null,
    val artSource: ArtSource = ArtSource.NONE,
    val console: String? = null,
    val storeAppId: Int? = null,
    val storeSource: String? = null
)
