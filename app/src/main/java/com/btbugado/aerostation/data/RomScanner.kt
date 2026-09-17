package com.btbugado.aerostation.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Varre (recursivamente) a pasta escolhida pelo usuário procurando por ROMs.
 *
 * Usa a API de Storage Access Framework (DocumentFile), já que no Android
 * moderno não dá pra simplesmente abrir um java.io.File numa pasta arbitrária
 * fora do escopo do app — o usuário concede acesso a UMA árvore específica,
 * e é essa árvore que percorremos aqui.
 */
object RomScanner {

    /** Limite de segurança pra não deixar o scan rodar pra sempre em pastas gigantes/mal escolhidas. */
    private const val MAX_FILES = 5000

    /** Arquivo candidato antes do filtro de duplicatas (ex: .bin com .cue). */
    private data class FoundRom(
        val file: DocumentFile,
        val fileName: String,
        val dirKey: String,
        val baseKey: String,
        val extension: String,
        /** Nomes das pastas ancestrais, da mais próxima pra mais distante. */
        val dirNames: List<String>
    )

    suspend fun scan(context: Context, treeUri: Uri): List<Game> =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
            val found = mutableListOf<FoundRom>()
            // Pilha de (pasta, ancestrais dela da mais próxima pra mais distante).
            val stack = ArrayDeque<Pair<DocumentFile, List<String>>>()
            stack.addLast(root to emptyList())

            while (stack.isNotEmpty() && found.size < MAX_FILES) {
                val (current, ancestors) = stack.removeLast()
                val children = current.listFiles()

                for (child in children) {
                    if (found.size >= MAX_FILES) break

                    if (child.isDirectory) {
                        val name = child.name
                        stack.addLast(
                            child to (
                                if (name != null) listOf(name) + ancestors else ancestors
                                )
                        )
                        continue
                    }

                    val fileName = child.name ?: continue
                    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()

                    if (extension.isEmpty() || extension !in RomExtensions.allAcceptedExtensions) {
                        continue
                    }

                    found += FoundRom(
                        file = child,
                        fileName = fileName,
                        dirKey = current.uri.toString(),
                        baseKey = fileName.substringBeforeLast('.').lowercase(),
                        extension = extension,
                        dirNames = listOfNotNull(current.name) + ancestors
                    )
                }
            }

            // Jogos de PS1/Saturn em .cue + .bin apareciam como dois jogos, e
            // abrir o .bin cru falha no emulador (ele abre a cue sheet, que é
            // o "arquivo principal" — igual no DuckStation). Esconde o .bin
            // quando existe um .cue de mesmo nome na MESMA pasta. Pastas
            // diferentes não se misturam (são rips diferentes).
            val cueKeys = found
                .filter { it.extension == "cue" }
                .mapTo(HashSet()) { "${it.dirKey}\u0000${it.baseKey}" }

            found
                .filterNot { it.extension == "bin" && "${it.dirKey}\u0000${it.baseKey}" in cueKeys }
                .map {
                    Game(
                        name = cleanDisplayName(it.fileName),
                        uri = it.file.uri,
                        extension = it.extension,
                        console = resolveConsole(context, it),
                        sizeBytes = it.file.length()
                    )
                }
                .sortedBy { it.name.lowercase() }
        }

    /**
     * Console efetivo, em ordem de confiança:
     * 1. Conteúdo do .iso plano (pasta PSP_GAME, SYSTEM.CNF com BOOT2/BOOT).
     * 2. Nome das pastas (ROMs/PS2/jogo.chd) — o único sinal pro .chd,
     *    que é comprimido e ilegível sem codecs nativos. Só vale pros
     *    formatos ambíguos (iso/bin/cue/chd); extensão certa sempre vence.
     * 3. Mapeamento por extensão (padrão).
     * Nunca quebra o scan (falha = próximo da lista).
     */
    private fun resolveConsole(context: Context, found: FoundRom): String? {
        if (found.extension == "iso") {
            runCatching { DiscImageSniffer.sniffIso(context, found.file.uri) }
                .getOrNull()?.let { return it }
        }
        if (RomExtensions.isAmbiguous(found.extension)) {
            runCatching { RomExtensions.consoleForFolders(found.dirNames) }
                .getOrNull()?.let { return it }
        }
        return RomExtensions.consoleFor(found.extension)
    }

    /**
     * Limpa o nome do arquivo pra exibição: tira a extensão e troca
     * underscore/ponto por espaço. Não mexe em tags de região/revisão
     * (ex: "(USA)", "[!]") — isso é ajuste fino pra depois.
     */
    private fun cleanDisplayName(fileName: String): String {
        val withoutExtension = fileName.substringBeforeLast('.')
        return withoutExtension
            .replace('_', ' ')
            .replace('.', ' ')
            .trim()
            .ifEmpty { fileName }
    }
}
