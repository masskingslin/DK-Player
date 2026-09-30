package com.dk.tvplayer.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings from the Advanced screen. Kept in SharedPreferences (not the DataStore) because
 * two of them — network caching and the HTTP user agent — are needed synchronously while
 * the player is being built in Application.onCreate, and take effect on the next app start.
 */
object AdvancedPrefs {
    private const val PREFS = "dk_advanced"
    private var appContext: Context? = null

    private val _networkCachingMs = MutableStateFlow(0)
    /** Buffer (ms) to build up before/while playing network media; 0 = the player default. */
    val networkCachingMs: StateFlow<Int> = _networkCachingMs.asStateFlow()

    private val _httpUserAgent = MutableStateFlow("")
    /** Custom HTTP user agent; blank = the default. */
    val httpUserAgent: StateFlow<String> = _httpUserAgent.asStateFlow()

    private val _timeStretchAudio = MutableStateFlow(true)
    /** When off, changing speed also changes pitch (like an old tape deck). */
    val timeStretchAudio: StateFlow<Boolean> = _timeStretchAudio.asStateFlow()

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val p = prefs() ?: return
        _networkCachingMs.value = p.getInt("networkCachingMs", 0)
        _httpUserAgent.value = p.getString("httpUserAgent", "") ?: ""
        _timeStretchAudio.value = p.getBoolean("timeStretch", true)
    }

    fun setNetworkCachingMs(value: Int) {
        _networkCachingMs.value = value.coerceIn(0, 60_000)
        prefs()?.edit()?.putInt("networkCachingMs", _networkCachingMs.value)?.apply()
    }

    fun setHttpUserAgent(value: String) {
        _httpUserAgent.value = value.trim()
        prefs()?.edit()?.putString("httpUserAgent", _httpUserAgent.value)?.apply()
    }

    fun setTimeStretchAudio(value: Boolean) {
        _timeStretchAudio.value = value
        prefs()?.edit()?.putBoolean("timeStretch", value)?.apply()
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
