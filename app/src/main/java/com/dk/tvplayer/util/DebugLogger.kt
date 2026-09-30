package com.dk.tvplayer.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * In-app logcat capture, like VLC's Advanced → Debug logs. An app can always read its own
 * process's log lines, so no READ_LOGS permission is needed. While logging is on, lines are
 * kept in a bounded in-memory buffer that the Debug logs screen shows live.
 */
object DebugLogger {
    private const val PREFS = "dk_debug"
    private const val MAX_LINES = 5000

    private var appContext: Context? = null
    private var process: Process? = null
    private var reader: Thread? = null
    private val buffer = ArrayDeque<String>()

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val _logging = MutableStateFlow(false)
    val logging: StateFlow<Boolean> = _logging.asStateFlow()

    private val _verbose = MutableStateFlow(false)
    /** When on, capture every level (V and up); otherwise info and above. */
    val verbose: StateFlow<Boolean> = _verbose.asStateFlow()

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        _verbose.value = prefs()?.getBoolean("verbose", false) ?: false
    }

    fun setVerbose(value: Boolean) {
        _verbose.value = value
        prefs()?.edit()?.putBoolean("verbose", value)?.apply()
        // A running capture uses the old level; restart it so the change takes effect.
        if (_logging.value) {
            stop()
            start()
        }
    }

    @Synchronized
    fun start() {
        if (_logging.value) return
        val level = if (_verbose.value) "V" else "I"
        val proc = runCatching {
            ProcessBuilder("logcat", "-v", "threadtime", "--pid=${android.os.Process.myPid()}", "*:$level")
                .redirectErrorStream(true)
                .start()
        }.getOrNull()
        if (proc == null) {
            append("Couldn't start logcat on this device")
            return
        }
        process = proc
        _logging.value = true
        reader = Thread({
            runCatching {
                BufferedReader(InputStreamReader(proc.inputStream)).use { r ->
                    while (true) {
                        val line = r.readLine() ?: break
                        append(line)
                    }
                }
            }
        }, "dk-debug-logcat").apply { isDaemon = true; start() }
    }

    @Synchronized
    fun stop() {
        process?.destroy()
        process = null
        reader = null
        _logging.value = false
    }

    @Synchronized
    fun clear() {
        buffer.clear()
        _lines.value = emptyList()
    }

    /** Everything captured so far as one string, for copying or sharing. */
    fun text(): String = synchronized(this) { buffer.joinToString("\n") }

    /** One-shot dump of the app's current logcat buffer (works even if live logging is off). */
    fun dumpLogcat(): String {
        val level = if (_verbose.value) "V" else "I"
        return runCatching {
            val p = ProcessBuilder("logcat", "-d", "-v", "threadtime", "--pid=${android.os.Process.myPid()}", "*:$level")
                .redirectErrorStream(true)
                .start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out
        }.getOrDefault("")
    }

    @Synchronized
    private fun append(line: String) {
        buffer.addLast(line)
        while (buffer.size > MAX_LINES) buffer.removeFirst()
        _lines.value = buffer.toList()
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
