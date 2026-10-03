package com.btbugado.aerostation.data

import android.content.Context
import android.net.Uri
import com.btbugado.aerostation.R
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.json.JSONObject

/**
 * Loja de temas: cada tema mora em `filesDir/themes/<id>/` com um
 * `theme.json` + os arquivos de mídia copiados pra dentro (pasta
 * autocontida = exportar é só zipar).
 *
 * O tema de fábrica ("Aero Padrão") não existe em disco: é o [AppTheme]
 * com os valores padrão, usado como fallback sempre que o id ativo some.
 */
object ThemeStore {
    const val DEFAULT_ID = "aero-default"
    private const val PREFS = "themes"
    private const val KEY_ACTIVE = "active_id"

    private fun dir(context: Context): File =
        File(context.filesDir, "themes").apply { mkdirs() }

    private fun themeDir(context: Context, id: String): File =
        File(dir(context), sanitize(id))

    /** Todos os temas instalados (fábrica primeiro, depois alfabético). */
    fun list(context: Context): List<AppTheme> {
        val out = mutableListOf(AppTheme.default(context))
        val root = dir(context)
        // Faxina: pasta sem theme.json é rascunho abandonado (ex: saiu do
        // editor trocando de aba) — some com ela em silêncio.
        root.listFiles()
            ?.filter { it.isDirectory && !File(it, "theme.json").exists() }
            ?.forEach { runCatching { it.deleteRecursively() } }
        val ids = root.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()
        for (id in ids) {
            load(context, id)?.let { out += it }
        }
        return out
    }

    fun load(context: Context, id: String): AppTheme? {
        if (id == DEFAULT_ID) return AppTheme.default(context)
        val folder = themeDir(context, id)
        val json = File(folder, "theme.json")
        if (!json.exists()) return null
        return runCatching {
            val theme = AppTheme.fromJson(
                JSONObject(json.readText()),
                context.getString(R.string.theme_fallback_untitled)
            )
            resolve(context, theme)
        }.getOrNull()
    }

    /** Tema ativo (ou o padrão, se o id sumiu do disco). */
    fun loadActive(context: Context): AppTheme {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE, DEFAULT_ID) ?: DEFAULT_ID
        return load(context, id) ?: AppTheme.default(context)
    }

    fun activeId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE, DEFAULT_ID) ?: DEFAULT_ID

    fun setActive(context: Context, id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACTIVE, id).apply()
    }

    /** Cria/atualiza um tema (gera id quando vazio) e resolve os caminhos. */
    fun save(context: Context, theme: AppTheme): AppTheme {
        val id = theme.id.ifBlank { newId() }
        val folder = themeDir(context, id).apply { mkdirs() }
        val saved = theme.copy(id = id)
        File(folder, "theme.json").writeText(saved.toJson().toString(2))
        return resolve(context, saved)
    }

    fun delete(context: Context, id: String) {
        if (id == DEFAULT_ID) return
        themeDir(context, id).deleteRecursively()
        if (activeId(context) == id) setActive(context, DEFAULT_ID)
    }

    fun duplicate(context: Context, id: String): AppTheme? {
        val src = load(context, id) ?: return null
        val copy = src.copy(
            id = newId(),
            name = context.getString(R.string.theme_copy_suffix, src.name)
        )
        val saved = save(context, copy)
        // Mídia junto: copia os arquivos pra pasta nova.
        val srcDir = themeDir(context, src.id)
        val dstDir = themeDir(context, saved.id)
        copyMediaInto(srcDir, dstDir, saved)
        return resolve(context, saved)
    }

    /**
     * Copia uma mídia (imagem/música/som) pra dentro da pasta do tema.
     * Retorna o NOME do arquivo gravado (o que vai no theme.json).
     */
    fun importMedia(context: Context, themeId: String, uri: Uri, kind: String): String? {
        val ext = extensionFor(context, uri, kind) ?: return null
        val folder = themeDir(context, themeId).apply { mkdirs() }
        val name = "$kind.$ext"
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                File(folder, name).outputStream().use { output -> input.copyTo(output) }
            }
            name
        }.getOrNull()
    }

    /** Remove uma mídia da pasta do tema (volta ao padrão naquele slot). */
    fun removeMedia(context: Context, themeId: String, fileName: String) {
        runCatching { File(themeDir(context, themeId), fileName).delete() }
    }

    /** Apaga todas as roupas de botão (pad_*) da pasta do tema. */
    fun clearPadMedia(context: Context, themeId: String) {
        runCatching {
            themeDir(context, themeId).listFiles()
                ?.filter { it.isFile && it.name.startsWith("pad_") }
                ?.forEach { it.delete() }
        }
    }

    // ---- importar / exportar (.aerotheme.zip) ----

    /**
     * Zipa a pasta do tema (theme.json + mídias) no cache, pronto pra
     * compartilhar via FileProvider. Nome amigável com o nome do tema.
     */
    fun exportToZip(context: Context, id: String): File? {
        val theme = load(context, id) ?: return null
        val folder = themeDir(context, theme.id)
        if (!File(folder, "theme.json").exists() && theme.id != DEFAULT_ID) return null
        return runCatching {
            val outDir = File(context.cacheDir, "theme-share").apply { mkdirs() }
            val safeName = theme.name
                .replace(Regex("[^a-zA-Z0-9À-ÿ _-]+"), "")
                .trim().take(40).ifBlank { context.getString(R.string.theme_fallback_filename) }
            val out = File(outDir, "$safeName.aerotheme.zip")
            ZipOutputStream(out.outputStream().buffered()).use { zip ->
                // theme.json fresquinho do objeto (garante consistência).
                zip.putNextEntry(ZipEntry("theme.json"))
                zip.write(theme.toJson().toString(2).toByteArray())
                zip.closeEntry()
                val media = mutableListOf<String>()
                media += themeMediaNames(theme)
                for (name in media.distinct()) {
                    if (!isSafeFileName(name)) continue
                    val file = File(folder, name)
                    if (!file.exists() || file.length() <= 0L) continue
                    zip.putNextEntry(ZipEntry(name))
                    file.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            out.takeIf { it.exists() && it.length() > 0L }
        }.getOrNull()
    }

    sealed interface ThemeImportResult {
        data class Ok(val theme: AppTheme) : ThemeImportResult
        data class Fail(val reason: String) : ThemeImportResult
    }

    /**
     * Importa um .zip: copia pro cache, valida o theme.json da raiz e
     * instala com id inédito (nunca sobrescreve nada). Só extrai os
     * arquivos referenciados no theme.json, com teto de 100MB.
     */
    fun importFromZip(context: Context, uri: Uri): ThemeImportResult {
        return runCatching {
            val tmp = File(context.cacheDir, "theme-import-tmp.zip")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (!tmp.exists() || tmp.length() <= 0L) {
                return ThemeImportResult.Fail(context.getString(R.string.theme_import_fail_read))
            }
            if (tmp.length() > 100L * 1024L * 1024L) {
                tmp.delete()
                return ThemeImportResult.Fail(context.getString(R.string.theme_import_fail_too_big))
            }
            ZipFile(tmp).use { zip ->
                val manifest = zip.getEntry("theme.json")
                    ?: return ThemeImportResult.Fail(context.getString(R.string.theme_import_fail_no_manifest))
                val theme = AppTheme.fromJson(
                    JSONObject(zip.getInputStream(manifest).bufferedReader().readText()),
                    context.getString(R.string.theme_fallback_untitled)
                )
                val wanted = mutableListOf<String>()
                wanted += themeMediaNames(theme)
                val id = newId()
                val folder = themeDir(context, id).apply { mkdirs() }
                for (name in wanted.distinct()) {
                    if (!isSafeFileName(name)) continue
                    val entry = zip.getEntry(name) ?: continue
                    if (entry.size > 50L * 1024L * 1024L) continue
                    zip.getInputStream(entry).buffered().use { input ->
                        File(folder, name).outputStream().buffered().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                // Áudios do zip também normalizam (música->m4a, efeito->wav),
                // igual ao editor: zip da web ou de outro celular sai menor
                // e garantido. Falha? Mantém o original (toca nativo).
                val refCount = (listOfNotNull(theme.musicFile) + theme.soundFiles.values)
                    .groupingBy { it }.eachCount()
                fun normalizedAudio(orig: String?, targetBase: String, isMusic: Boolean): String? {
                    if (orig.isNullOrBlank()) return null
                    val targetName = "$targetBase.${if (isMusic) "m4a" else "wav"}"
                    if (orig.equals(targetName, ignoreCase = true)) return orig
                    val ok = ThemeAudioConverter.normalizeFile(
                        context, File(folder, orig), isMusic, File(folder, targetName)
                    )
                    if (!ok) return orig
                    // Original órfão? Apaga (se mais alguém referencia, mantém).
                    if ((refCount[orig] ?: 0) <= 1) {
                        runCatching { File(folder, orig).delete() }
                    }
                    return targetName
                }
                val musicName = normalizedAudio(theme.musicFile, "music", true)
                val soundMap = mutableMapOf<String, String>()
                for ((slot, orig) in theme.soundFiles) {
                    normalizedAudio(orig, slot, false)?.let { soundMap[slot] = it }
                }
                val installed = save(
                    context,
                    theme.copy(id = id, musicFile = musicName, soundFiles = soundMap)
                )
                ThemeImportResult.Ok(installed)
            }.also { tmp.delete() }
        }.getOrElse {
            ThemeImportResult.Fail(context.getString(R.string.theme_import_fail_bad_zip))
        }
    }

    /** Nome de arquivo simples, sem pasta nem truque de path traversal. */
    private fun isSafeFileName(name: String): Boolean {
        if (name.isBlank() || name.length > 64) return false
        if (name.contains('/') || name.contains('\\') || name.contains("..")) return false
        return name.all { it.isLetterOrDigit() || it in "._-" }
    }

    /** Extensões de vídeo válidas pro fundo animado (loop, sem áudio). */
    val themeBgVideoExts = setOf("mp4", "mkv", "webm", "3gp", "mov", "avi")

    /** O fundo é vídeo (player em loop) em vez de imagem/GIF (Coil)? */
    fun isVideoBackground(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in themeBgVideoExts
    }

    /** Preenche os caminhos absolutos a partir dos nomes do theme.json. */
    fun resolve(context: Context, theme: AppTheme): AppTheme {
        if (theme.id == DEFAULT_ID) return theme
        val folder = themeDir(context, theme.id)
        fun fileOf(name: String?): String? {
            if (name.isNullOrBlank()) return null
            return File(folder, name).takeIf { it.exists() && it.length() > 0L }?.absolutePath
        }
        val sounds = mutableMapOf<String, String>()
        for ((slot, name) in theme.soundFiles) {
            fileOf(name)?.let { sounds[slot] = it }
        }
        val padButtons = theme.padSkin.buttons.mapValues { (_, skin) ->
            skin.copy().apply { imagePath = fileOf(skin.imageFile) }
        }
        return theme.copy(padSkin = theme.padSkin.copy(buttons = padButtons)).apply {
            backgroundImagePath = fileOf(theme.backgroundImageFile)
            musicPath = fileOf(theme.musicFile)
            soundPaths = sounds
        }
    }

    /** Todos os nomes de mídia referenciados por um tema (zip/dup/import). */
    fun themeMediaNames(theme: AppTheme): List<String> {
        val names = mutableListOf<String>()
        theme.backgroundImageFile?.let { names += it }
        theme.musicFile?.let { names += it }
        names += theme.soundFiles.values
        for ((_, skin) in theme.padSkin.buttons) {
            skin.imageFile?.let { names += it }
        }
        return names.distinct()
    }

    // ---- internos ----

    /** Id inédito pra um tema novo (a pasta só nasce de verdade no save). */
    fun newId(): String =
        "t" + System.currentTimeMillis().toString(36) +
            (0..999).random().toString(36)

    private fun sanitize(id: String): String =
        id.replace(Regex("[^a-zA-Z0-9_-]+"), "_").take(48).ifBlank { newId() }

    private fun copyMediaInto(srcDir: File, dstDir: File, theme: AppTheme) {
        for (name in themeMediaNames(theme)) {
            runCatching {
                val src = File(srcDir, name)
                if (src.exists()) src.copyTo(File(dstDir, name), overwrite = true)
            }
        }
    }

    /** Extensão por MIME (cai pra apropriada por tipo quando o MIME falha). */
    private fun extensionFor(context: Context, uri: Uri, kind: String): String? {
        val mime = runCatching {
            context.contentResolver.getType(uri)
        }.getOrNull()?.lowercase().orEmpty()
        // Nome original como pista (ex: "musica.mp3").
        val displayName = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
            }
        }.getOrNull()
        val fromName = displayName?.substringAfterLast('.', "")?.lowercase()
            ?.takeIf { it.isNotEmpty() && it.length <= 5 && it.all { ch -> ch.isLetterOrDigit() } }
        val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
        val audioExts = setOf("mp3", "wav", "ogg", "m4a", "flac", "opus", "mid", "midi")
        return when {
            mime.startsWith("image/") -> mime.substringAfter("image/").takeIf { it in imageExts }
                ?: fromName?.takeIf { it in imageExts } ?: "jpg"
            mime.startsWith("video/") -> mime.substringAfter("video/").takeIf { it in themeBgVideoExts }
                ?: fromName?.takeIf { it in themeBgVideoExts } ?: "mp4"
            mime.startsWith("audio/") || mime == "application/ogg" -> {
                val clean = mime.substringAfterLast('/').takeIf { it.length <= 5 }.orEmpty()
                when {
                    clean in audioExts -> clean
                    fromName in audioExts -> fromName
                    else -> "mp3"
                }
            }
            kind == "bg" -> fromName?.takeIf { it in imageExts + themeBgVideoExts } ?: "jpg"
            kind.startsWith("pad_") -> fromName?.takeIf { it in imageExts } ?: "png"
            else -> fromName?.takeIf { it in audioExts } ?: "mp3"
        }
    }
}
