package com.btbugado.aerostation.data

import android.content.Context
import android.os.SystemClock
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Descobre o Title ID de um jogo de PS Vita (ex: "PCSB00245").
 *
 * O Vita3K não abre o arquivo da ROM direto: o jogo precisa estar INSTALADO
 * nele, e o boot externo é pelo Title ID (extra "title_id" / array
 * "AppStartParameters" = ["-r", titleId] na activity Emulator — receita do
 * ES-DE e da issue Vita3K-Android#505, confirmada no fonte do app).
 *
 * Ordem: nome do arquivo (a maioria dos dumps já traz o ID, ex:
 * "PCSE00546.vpk" ou "Jogo [PCSE00546].vpk") e, se não achar, o
 * `sce_sys/param.sfo` de dentro do .vpk/.zip (formato SFO padrão do
 * PlayStation). O scan do zip tem orçamento de tempo pra não travar a UI
 * num .vpk gigante onde o param.sfo está no fim do arquivo.
 */
object VitaTitleId {

    /** Title ID: 4 letras + 5 dígitos (ex: PCSB00245, PCSE00546). */
    private val TITLE_ID_REGEX = Regex("[A-Z]{4}[0-9]{5}")

    /** Caminho do param.sfo dentro do .vpk (sempre esse). */
    private const val PARAM_SFO_PATH = "sce_sys/param.sfo"

    /** Teto do scan do zip: passou disso, desiste sem travar a UI. */
    private const val ZIP_SCAN_BUDGET_MS = 5000L

    /** Teto do param.sfo lido (ele tem ~1-2 KB; folga generosa). */
    private const val PARAM_SFO_MAX_BYTES = 128 * 1024

    fun resolve(context: Context, game: Game): String? {
        fileNameOf(context, game)?.let { name ->
            TITLE_ID_REGEX.find(name.uppercase())?.value?.let { return it }
        }
        // 7z precisaria de lib nativa pra abrir — nele só vale o nome.
        if (game.extension == "vpk" || game.extension == "zip") {
            runCatching { titleIdFromParamSfo(context, game) }.getOrNull()?.let { return it }
        }
        return null
    }

    /**
     * Nome real do arquivo: DISPLAY_NAME do provedor primeiro; fallback pro
     * último segmento da URI (o docId costuma conter o caminho, ex:
     * "primary:PS Vita/PCSE00546.vpk") e, por último, o nome de exibição.
     */
    private fun fileNameOf(context: Context, game: Game): String? {
        runCatching {
            context.contentResolver.query(
                game.uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        cursor.getString(idx)?.takeIf { it.isNotBlank() }?.let { return it }
                    }
                }
            }
        }
        game.uri.lastPathSegment
            ?.substringAfterLast(':')
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return game.name.takeIf { it.isNotBlank() }
    }

    private fun titleIdFromParamSfo(context: Context, game: Game): String? {
        val start = SystemClock.elapsedRealtime()
        try {
            context.contentResolver.openInputStream(game.uri)?.use { raw ->
                ZipInputStream(raw.buffered(32 * 1024)).use { zip ->
                    while (true) {
                        if (SystemClock.elapsedRealtime() - start > ZIP_SCAN_BUDGET_MS) return null
                        val entry = zip.nextEntry ?: break
                        if (!entry.isDirectory && entry.name.equals(PARAM_SFO_PATH, ignoreCase = true)) {
                            return parseSfoTitleId(zip.readCapped(PARAM_SFO_MAX_BYTES))
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Zip inválido/ilegível: sem Title ID por aqui.
        }
        return null
    }

    private fun InputStream.readCapped(max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (total < max) {
            val n = read(buf, 0, minOf(buf.size, max - total))
            if (n < 0) break
            out.write(buf, 0, n)
            total += n
        }
        return out.toByteArray()
    }

    /**
     * Lê o TITLE_ID do param.sfo (header "\0PSF" + entradas de 16 bytes +
     * tabelas de chaves/dados, tudo little-endian). Só aceita valor no
     * formato de Title ID — qualquer outra coisa é null.
     */
    private fun parseSfoTitleId(sfo: ByteArray): String? {
        if (sfo.size < 20) return null
        if (sfo[0] != 0.toByte() || sfo[1] != 'P'.code.toByte() ||
            sfo[2] != 'S'.code.toByte() || sfo[3] != 'F'.code.toByte()
        ) return null
        val keyTable = u32LE(sfo, 8)
        val dataTable = u32LE(sfo, 12)
        val count = u32LE(sfo, 16)
        if (keyTable < 0 || dataTable < 0 || count < 0 || count > 256) return null
        if (keyTable > sfo.size || dataTable > sfo.size) return null

        var i = 0L
        while (i < count) {
            val base = 20 + i * 16
            if (base + 16 > sfo.size) return null
            val b = base.toInt()
            val keyOff = u16LE(sfo, b)
            val format = u16LE(sfo, b + 2)
            val length = u32LE(sfo, b + 4)
            val dataOff = u32LE(sfo, b + 12)
            if (keyOff < 0 || format < 0 || length < 0 || dataOff < 0) return null
            val keyStart = keyTable + keyOff
            val dataStart = dataTable + dataOff
            if (keyStart < 0 || keyStart >= sfo.size) return null
            if (dataStart < 0 || dataStart >= sfo.size) return null
            if (length > sfo.size - dataStart) return null
            val key = readCString(sfo, keyStart.toInt(), 64)
            // Formato 0 = inteiro; string é qualquer outro (TITLE_ID é texto).
            if (key == "TITLE_ID" && format != 0) {
                val raw = readCString(sfo, dataStart.toInt(), length.toInt()).trim()
                if (TITLE_ID_REGEX.matches(raw)) return raw
            }
            i++
        }
        return null
    }

    private fun u16LE(bytes: ByteArray, off: Int): Int {
        if (off < 0 || off + 2 > bytes.size) return -1
        return (bytes[off].toInt() and 0xFF) or ((bytes[off + 1].toInt() and 0xFF) shl 8)
    }

    private fun u32LE(bytes: ByteArray, off: Int): Long {
        if (off < 0 || off + 4 > bytes.size) return -1L
        return (bytes[off].toLong() and 0xFF) or
            ((bytes[off + 1].toLong() and 0xFF) shl 8) or
            ((bytes[off + 2].toLong() and 0xFF) shl 16) or
            ((bytes[off + 3].toLong() and 0xFF) shl 24)
    }

    private fun readCString(bytes: ByteArray, off: Int, maxLen: Int): String {
        if (off < 0 || off >= bytes.size || maxLen <= 0) return ""
        var end = off
        val limit = minOf(bytes.size, off + maxLen)
        while (end < limit && bytes[end] != 0.toByte()) end++
        return String(bytes, off, end - off, Charsets.US_ASCII)
    }
}
