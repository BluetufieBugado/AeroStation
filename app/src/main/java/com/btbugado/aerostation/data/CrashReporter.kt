package com.btbugado.aerostation.data

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gravador de crash sem dependência externa: instala um
 * [Thread.UncaughtExceptionHandler] que salva o stack trace num .txt dentro
 * do armazenamento privado e depois delega pro handler original (o sistema
 * mostra o "app parou" normalmente).
 *
 * Na próxima abertura, o app lista [pendingCrashes] e oferece
 * copiar/compartilhar — jeito de obter o log testando direto no celular,
 * sem Android Studio/logcat.
 */
object CrashReporter {
    private const val DIR = "crashes"
    private const val MAX_FILES = 5
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Escrita síncrona na thread que crashou: o processo morre em
            // seguida, então nada aqui pode ser assíncrono nem tocar Compose.
            runCatching { writeCrash(app, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("?")
        val sb = StringBuilder()
        sb.appendLine("AeroStation crash $stamp")
        sb.appendLine("App: $version | Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("Aparelho: ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("Thread: ${thread.name}")
        sb.appendLine()
        var cause: Throwable? = throwable
        var first = true
        while (cause != null) {
            if (!first) sb.appendLine("Causado por:")
            first = false
            sb.appendLine("${cause.javaClass.name}: ${cause.message}")
            cause.stackTrace.forEach { sb.appendLine("    at $it") }
            cause = cause.cause
        }
        File(dir, "crash-$stamp.txt").writeText(sb.toString())
        // Mantém só os últimos arquivos.
        dir.listFiles()
            ?.sortedByDescending { it.name }
            ?.drop(MAX_FILES)
            ?.forEach { runCatching { it.delete() } }
    }

    /** Crashes ainda não enviados, do mais novo pro mais antigo. */
    fun pendingCrashes(context: Context): List<File> =
        runCatching {
            File(context.filesDir, DIR).listFiles()
                ?.filter { it.isFile && it.name.startsWith("crash-") && it.name.endsWith(".txt") }
                ?.sortedByDescending { it.name }
                .orEmpty()
        }.getOrDefault(emptyList())

    fun readCrash(file: File, maxChars: Int = 60_000): String =
        runCatching { file.readText().take(maxChars) }.getOrDefault("Não consegui ler o arquivo.")

    fun deleteCrash(file: File) {
        runCatching { file.delete() }
    }
}
