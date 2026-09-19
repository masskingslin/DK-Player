package com.dk.tvplayer.util

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Installs a global uncaught-exception handler that writes the full stack trace to a
 * file before the process dies, alongside device/app info that's useful for pinning
 * down a crash that only happens "sometimes" — without this, an intermittent crash on
 * a real device leaves nothing behind unless someone happened to have `adb logcat`
 * attached at that exact moment, which makes it effectively undiagnosable after the
 * fact. Logs live under the same cache/exports dir the app already shares files from
 * (see ShareFileUtils / file_paths.xml), so no new FileProvider path is needed.
 */
object CrashLogger {

    private const val MAX_LOGS_KEPT = 10

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrashLog(appContext, thread, throwable) }
            // Always chain to whatever handler was previously installed (normally
            // Android's own, which shows the "app has stopped" dialog / feeds Play
            // Vitals) — replacing it entirely would just swap one blind spot for
            // another.
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val dir = crashLogDir(context)
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(dir, "crash_$timestamp.txt")

        val stackTraceWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(stackTraceWriter))

        val report = buildString {
            appendLine("Time: ${Date()}")
            appendLine("Thread: ${thread.name}")
            appendLine("App version: ${appVersionName(context)}")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine()
            append(stackTraceWriter.toString())
        }
        file.writeText(report)

        // Keep only the most recent MAX_LOGS_KEPT files so this can't grow unbounded on
        // a device that crashes repeatedly before anyone gets a chance to look at it.
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_LOGS_KEPT)
            ?.forEach { it.delete() }
    }

    private fun appVersionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    }.getOrDefault("unknown")

    private fun crashLogDir(context: Context): File =
        File(context.cacheDir, "exports/crash_logs").apply { mkdirs() }

    /** Most recent crash log, if any — used by the Settings "Share Crash Log" action. */
    fun latestLogFile(context: Context): File? =
        crashLogDir(context).listFiles()?.maxByOrNull { it.lastModified() }

    fun hasLogs(context: Context): Boolean = crashLogDir(context).listFiles()?.isNotEmpty() == true
}
