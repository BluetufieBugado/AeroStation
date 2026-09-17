package com.btbugado.aerostation.data

import android.content.Context
import android.net.Uri

/**
 * Descobre o console real de uma imagem .iso (setor 2048) lendo o conteúdo:
 * - pasta PSP_GAME na raiz = PSP
 * - SYSTEM.CNF com "BOOT2" = PlayStation 2
 * - SYSTEM.CNF com "BOOT" (PS-X) = PlayStation
 *
 * Sem isso todo .iso caía no padrão "PlayStation" e abria no DuckStation —
 * incluindo jogos de PS2 e PSP. Retorna null se não der pra identificar
 * (aí vale o mapeamento por extensão).
 *
 * Limitado a .iso plano: .bin (setor 2352), .chd/.cso (comprimidos) e
 * multi-faixa continuam no override manual (segurar > Definir plataforma).
 */
object DiscImageSniffer {

    private const val SECTOR_SIZE = 2048L
    private const val PVD_SECTOR = 16L
    private const val MAX_DIR_BYTES = 64 * 1024
    private const val MAX_CNF_BYTES = 2048

    fun sniffIso(context: Context, uri: Uri): String? {
        return try {
            // Descritor de volume primário (ECMA-119 §8.4): "CD001" no offset 1.
            val pvd = readRange(context, uri, PVD_SECTOR * SECTOR_SIZE, SECTOR_SIZE.toInt())
                ?: return null
            if (pvd.size < 2048 || pvd[1] != 'C'.code.toByte() || pvd[2] != 'D'.code.toByte() ||
                pvd[3] != '0'.code.toByte() || pvd[4] != '0'.code.toByte() ||
                pvd[5] != '1'.code.toByte()
            ) {
                return null
            }
            // Registro do diretório raiz no offset 156 do PVD (§9.1).
            val root = parseRecord(pvd, 156) ?: return null
            val dirBytes = readRange(
                context, uri,
                root.extent * SECTOR_SIZE,
                root.size.coerceAtMost(MAX_DIR_BYTES.toLong()).toInt()
            ) ?: return null

            var pspGame = false
            var systemCnf: Record? = null
            var pos = 0
            while (pos < dirBytes.size) {
                val record = parseRecord(dirBytes, pos) ?: break
                val name = record.name
                if (name == "PSP_GAME" && record.isDir) pspGame = true
                if (name == "SYSTEM.CNF" && !record.isDir) systemCnf = record
                if (record.length <= 0) break
                pos += record.length
            }

            if (pspGame) return "PSP"

            val cnf = systemCnf ?: return null
            val head = readRange(
                context, uri,
                cnf.extent * SECTOR_SIZE,
                cnf.size.coerceAtMost(MAX_CNF_BYTES.toLong()).toInt()
            ) ?: return null
            // "BOOT2" contém "BOOT" — testar o PS2 primeiro, nessa ordem!
            val text = head.toString(Charsets.US_ASCII)
            when {
                text.contains("BOOT2") -> "PlayStation 2"
                text.contains("BOOT") -> "PlayStation"
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private data class Record(
        val length: Int,
        val extent: Long,
        val size: Long,
        val isDir: Boolean,
        val name: String
    )

    /** Registro de diretório ECMA-119 §9.1 (little-endian). Null se inválido. */
    private fun parseRecord(bytes: ByteArray, offset: Int): Record? {
        if (offset < 0 || offset >= bytes.size) return null
        val length = bytes[offset].toInt() and 0xFF
        if (length < 34 || offset + length > bytes.size) return null
        fun u32le(at: Int): Long =
            (bytes[at].toLong() and 0xFF) or
                ((bytes[at + 1].toLong() and 0xFF) shl 8) or
                ((bytes[at + 2].toLong() and 0xFF) shl 16) or
                ((bytes[at + 3].toLong() and 0xFF) shl 24)
        val extent = u32le(offset + 2)
        val size = u32le(offset + 10)
        val flags = bytes[offset + 25].toInt() and 0xFF
        val nameLen = bytes[offset + 32].toInt() and 0xFF
        if (33 + nameLen > length) return null
        var name = String(bytes, offset + 33, nameLen, Charsets.US_ASCII)
        // Arquivos carregam sufixo de versão ("SYSTEM.CNF;1"); diretórios
        // raiz usam bytes \0 (self) e \1 (pai) — ignorados pelo chamador.
        if (!name.startsWith("\u0000") && !name.startsWith("\u0001")) {
            name = name.substringBefore(';').uppercase()
        }
        return Record(
            length = length,
            extent = extent,
            size = size,
            isDir = (flags and 0x02) != 0,
            name = name
        )
    }

    /** Lê [length] bytes do [offset] (abre um stream novo por chamada). */
    private fun readRange(context: Context, uri: Uri, offset: Long, length: Int): ByteArray? {
        if (length <= 0) return null
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                var remaining = offset
                while (remaining > 0) {
                    val skipped = stream.skip(remaining)
                    if (skipped <= 0) return null
                    remaining -= skipped
                }
                val out = ByteArray(length)
                var filled = 0
                while (filled < length) {
                    val read = stream.read(out, filled, length - filled)
                    if (read < 0) break
                    filled += read
                }
                if (filled == 0) null else out.copyOf(filled)
            }
        } catch (e: Exception) {
            null
        }
    }
}
