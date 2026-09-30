package com.dk.tvplayer.util

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** A PIN prompt waiting to be shown (see PinPromptHost); [onSuccess] runs once it's entered. */
class PendingPin(val reason: String, val onSuccess: () -> Unit)

sealed interface PinResult {
    data object Ok : PinResult
    data class Wrong(val triesLeft: Int) : PinResult
    data class Locked(val seconds: Int) : PinResult
}

/**
 * Parental control, like VLC's: a 4-digit PIN that can (a) lock the Settings screen and
 * (b) in "Safe mode", be required before files are deleted or playlists are changed.
 *
 * The PIN is never stored — only a salted PBKDF2 hash. Five wrong tries lock PIN entry for
 * 30 seconds. After a correct PIN, Safe-mode actions stay allowed for a minute (so a batch of
 * edits doesn't ask every time) and Settings stay open for five minutes.
 */
object ParentalControl {
    private const val PREFS = "dk_parental"
    private const val PIN_LENGTH = 4
    private const val MAX_TRIES = 5
    private const val LOCK_MS = 30_000L
    private const val ACTION_GRACE_MS = 60_000L
    private const val SETTINGS_GRACE_MS = 5 * 60_000L

    const val PIN_DIGITS = PIN_LENGTH

    private var appContext: Context? = null
    private var failedTries = 0
    private var lockedUntil = 0L
    private var actionUnlockedUntil = 0L
    private var settingsUnlockedUntil = 0L

    private val _hasPin = MutableStateFlow(false)
    val hasPin: StateFlow<Boolean> = _hasPin.asStateFlow()

    private val _restrictSettings = MutableStateFlow(false)
    val restrictSettings: StateFlow<Boolean> = _restrictSettings.asStateFlow()

    private val _safeMode = MutableStateFlow(false)
    val safeMode: StateFlow<Boolean> = _safeMode.asStateFlow()

    private val _pending = MutableStateFlow<PendingPin?>(null)
    val pending: StateFlow<PendingPin?> = _pending.asStateFlow()

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val p = prefs() ?: return
        _hasPin.value = p.getString("hash", null) != null
        // Without a PIN there is nothing to ask for, so the options can't be on.
        _restrictSettings.value = _hasPin.value && p.getBoolean("restrictSettings", false)
        _safeMode.value = _hasPin.value && p.getBoolean("safeMode", false)
    }

    // ---------- PIN ----------

    fun setPin(pin: String) {
        require(pin.length == PIN_LENGTH && pin.all(Char::isDigit))
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs()?.edit()
            ?.putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            ?.putString("hash", Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
            ?.apply()
        _hasPin.value = true
    }

    fun verifyPin(pin: String): PinResult {
        val now = SystemClock.elapsedRealtime()
        if (now < lockedUntil) return PinResult.Locked(((lockedUntil - now) / 1000 + 1).toInt())
        val p = prefs() ?: return PinResult.Wrong(0)
        val salt = p.getString("salt", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        val stored = p.getString("hash", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        if (salt == null || stored == null) return PinResult.Ok // no PIN set
        return if (MessageDigest.isEqual(hash(pin, salt), stored)) {
            failedTries = 0
            PinResult.Ok
        } else {
            failedTries++
            if (failedTries >= MAX_TRIES) {
                failedTries = 0
                lockedUntil = now + LOCK_MS
                PinResult.Locked((LOCK_MS / 1000).toInt())
            } else {
                PinResult.Wrong(MAX_TRIES - failedTries)
            }
        }
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, 50_000, 256))
            .encoded

    // ---------- options ----------

    fun setRestrictSettings(value: Boolean) {
        _restrictSettings.value = value
        prefs()?.edit()?.putBoolean("restrictSettings", value)?.apply()
        if (value) settingsUnlockedUntil = SystemClock.elapsedRealtime() + SETTINGS_GRACE_MS
    }

    fun setSafeMode(value: Boolean) {
        _safeMode.value = value
        prefs()?.edit()?.putBoolean("safeMode", value)?.apply()
    }

    // ---------- gates ----------

    fun isSettingsUnlocked(): Boolean =
        !_restrictSettings.value || SystemClock.elapsedRealtime() < settingsUnlockedUntil

    fun markSettingsUnlocked() {
        settingsUnlockedUntil = SystemClock.elapsedRealtime() + SETTINGS_GRACE_MS
        actionUnlockedUntil = SystemClock.elapsedRealtime() + ACTION_GRACE_MS
    }

    fun markActionUnlocked() {
        actionUnlockedUntil = SystemClock.elapsedRealtime() + ACTION_GRACE_MS
    }

    /**
     * Safe-mode check for an action that deletes files or changes playlists. Returns true if
     * the action was held back (a PIN prompt is now showing and [retry] runs after a correct
     * PIN) — the caller should then `return` instead of doing the work itself.
     */
    fun interceptForSafeMode(reason: String = "Enter your PIN to continue", retry: () -> Unit): Boolean {
        if (!_safeMode.value || SystemClock.elapsedRealtime() < actionUnlockedUntil) return false
        requestPin(reason) {
            markActionUnlocked()
            retry()
        }
        return true
    }

    /** Asks for the PIN (via the app-wide prompt) and runs [onSuccess] only if it's right. */
    fun requestPin(reason: String, onSuccess: () -> Unit) {
        _pending.value = PendingPin(reason, onSuccess)
    }

    fun clearPending() {
        _pending.value = null
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
