package com.dk.tvplayer.remote

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a remote browser is allowed to see and do. Persisted in SharedPreferences. */
enum class RemoteContent(val key: String, val label: String, val default: Boolean) {
    VIDEO("video", "Video", true),
    AUDIO("audio", "Audio", true),
    PLAYLISTS("playlists", "Playlists", true),
    SEARCH("search", "Search", true),
    FILE_BROWSER("files", "File browser", true),
    HISTORY("history", "History", false),
    CONTROL("control", "Control playback", true),
    LOGS("logs", "Share log files", false)
}

object RemoteAccessConfig {
    private const val PREFS = "dk_remote_access"
    private var appContext: Context? = null

    private val _enabled = MutableStateFlow(false)
    /** The user's "Enable remote access" choice (the server itself is [RemoteAccessState.running]). */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _onboardingDone = MutableStateFlow(false)
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    private val _content = MutableStateFlow(RemoteContent.values().associateWith { it.default })
    val content: StateFlow<Map<RemoteContent, Boolean>> = _content.asStateFlow()

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val prefs = prefs() ?: return
        _enabled.value = prefs.getBoolean("enabled", false)
        _onboardingDone.value = prefs.getBoolean("onboardingDone", false)
        _content.value = RemoteContent.values().associateWith { prefs.getBoolean("c_${it.key}", it.default) }
    }

    fun isAllowed(item: RemoteContent): Boolean = _content.value[item] == true

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        prefs()?.edit()?.putBoolean("enabled", value)?.apply()
    }

    fun setOnboardingDone() {
        _onboardingDone.value = true
        prefs()?.edit()?.putBoolean("onboardingDone", true)?.apply()
    }

    fun setAllowed(item: RemoteContent, allowed: Boolean) {
        _content.value = _content.value + (item to allowed)
        prefs()?.edit()?.putBoolean("c_${item.key}", allowed)?.apply()
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Live server state shown on the Remote access screen. */
object RemoteAccessState {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _addresses = MutableStateFlow<List<String>>(emptyList())
    val addresses: StateFlow<List<String>> = _addresses.asStateFlow()

    private val _otp = MutableStateFlow("")
    /** The current one-time code a browser must enter to sign in. */
    val otp: StateFlow<String> = _otp.asStateFlow()

    private val _sessions = MutableStateFlow(0)
    val sessions: StateFlow<Int> = _sessions.asStateFlow()

    private val _fingerprint = MutableStateFlow("")
    /** SHA-256 fingerprint of the server's certificate, to verify against the browser warning. */
    val fingerprint: StateFlow<String> = _fingerprint.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setRunning(value: Boolean) { _running.value = value }
    fun setAddresses(value: List<String>) { _addresses.value = value }
    fun setOtp(value: String) { _otp.value = value }
    fun setSessions(value: Int) { _sessions.value = value }
    fun setFingerprint(value: String) { _fingerprint.value = value }
    fun setError(value: String?) { _error.value = value }

    fun reset() {
        _running.value = false
        _addresses.value = emptyList()
        _otp.value = ""
        _sessions.value = 0
    }
}
