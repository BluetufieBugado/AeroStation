package com.btbugado.aerostation.data

import android.net.Uri

/**
 * Representa uma ROM encontrada durante o scan da pasta escolhida pelo usuário.
 *
 * @param name nome de exibição (sem extensão, sem underscores/pontuação de "romset")
 * @param uri URI (SAF) do arquivo, usada depois para abrir com o LibretroDroid
 * @param extension extensão do arquivo em minúsculas, sem o ponto (ex: "sfc")
 * @param console console associado à extensão, quando reconhecida (ex: "Super Nintendo")
 * @param sizeBytes tamanho do arquivo em bytes, útil pra UI/debug
 */
data class Game(
    val name: String,
    val uri: Uri,
    val extension: String,
    val console: String?,
    val sizeBytes: Long
)
