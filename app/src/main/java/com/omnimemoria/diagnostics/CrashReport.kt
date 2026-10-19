package com.omnimemoria.diagnostics

import android.content.Context
import android.os.Build
import android.util.AtomicFile
import com.omnimemoria.R
import java.io.File
import java.util.Date

/** One local, bounded report. Never uploads anything or consumes the platform crash handler. */
object CrashReport {
    private const val LIMIT = 64 * 1024
    private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, "last-crash.txt"))

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                val report = buildString {
                    appendLine("OmniMemoria $version · ${context.getString(R.string.build_revision)}")
                    appendLine("${Date()} · Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
                    appendLine("${Build.MANUFACTURER} ${Build.MODEL} · ${Build.SUPPORTED_ABIS.joinToString()}")
                    appendLine("Thread: ${thread.name}")
                    append(error.stackTraceToString().take(LIMIT - 1024))
                }.take(LIMIT)
                val target = file(context)
                val stream = target.startWrite()
                try {
                    stream.write(report.toByteArray(Charsets.UTF_8))
                    target.finishWrite(stream)
                } catch (failure: Throwable) {
                    target.failWrite(stream)
                }
            } catch (_: Throwable) {
                // Reporting must never prevent the original crash from reaching Android.
            } finally {
                if (previous != null) previous.uncaughtException(thread, error)
                else { android.os.Process.killProcess(android.os.Process.myPid()); kotlin.system.exitProcess(10) }
            }
        }
    }

    fun read(context: Context): String? = runCatching {
        file(context).openRead().use { input -> input.readBytes().toString(Charsets.UTF_8).take(LIMIT) }
    }.getOrNull()?.takeIf(String::isNotBlank)

    fun clear(context: Context) { runCatching { file(context).delete() } }
}
