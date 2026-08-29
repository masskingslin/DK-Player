package com.dk.tvplayer.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dk_player_settings")

class SettingsManager(private val context: Context) {

    companion object {
        val HARDWARE_ACCELERATION = booleanPreferencesKey("hardware_acceleration")
        val BACKGROUND_AUDIO = booleanPreferencesKey("background_audio")
        val AUTO_RESUME = booleanPreferencesKey("auto_resume")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val SUBTITLE_FONT_SIZE = floatPreferencesKey("subtitle_font_size")
        val SUBTITLE_BACKGROUND_OPACITY = floatPreferencesKey("subtitle_bg_opacity")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val SLEEP_TIMER_MINUTES = intPreferencesKey("sleep_timer_minutes")
        val BUFFER_FOR_PLAYBACK_MS = intPreferencesKey("buffer_playback_ms")
    }

    val hardwareAccelerationFlow: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[HARDWARE_ACCELERATION] ?: true }

    val backgroundAudioFlow: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[BACKGROUND_AUDIO] ?: false }

    val autoResumeFlow: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[AUTO_RESUME] ?: true }

    val themeModeFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[THEME_MODE] ?: "SYSTEM" }

    val accentColorFlow: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[ACCENT_COLOR] ?: "PURPLE" }

    val subtitleFontSizeFlow: Flow<Float> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[SUBTITLE_FONT_SIZE] ?: 1.0f }

    val playbackSpeedFlow: Flow<Float> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[PLAYBACK_SPEED] ?: 1.0f }

    suspend fun setHardwareAcceleration(enabled: Boolean) {
        context.dataStore.edit { it[HARDWARE_ACCELERATION] = enabled }
    }

    suspend fun setBackgroundAudio(enabled: Boolean) {
        context.dataStore.edit { it[BACKGROUND_AUDIO] = enabled }
    }

    suspend fun setAutoResume(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_RESUME] = enabled }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[THEME_MODE] = mode }
    }

    suspend fun setAccentColor(color: String) {
        context.dataStore.edit { it[ACCENT_COLOR] = color }
    }

    suspend fun setSubtitleFontSize(size: Float) {
        context.dataStore.edit { it[SUBTITLE_FONT_SIZE] = size }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.dataStore.edit { it[PLAYBACK_SPEED] = speed }
    }
}
