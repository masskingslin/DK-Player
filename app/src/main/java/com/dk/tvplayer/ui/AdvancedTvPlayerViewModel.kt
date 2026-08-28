package com.dk.tvplayer.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dk.tvplayer.cast.EnhancedCastManager
import com.dk.tvplayer.util.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AdvancedTvPlayerViewModel(application: Application) : AndroidViewModel(application) {
    
    private val errorHandlingManager = ErrorHandlingManager()
    private val advancedKeyHandler = AdvancedKeyHandler()
    private val settingsManager = SettingsManager(application)
    
    val errorEvents: StateFlow<ErrorEvent?> = errorHandlingManager.errorEvents
    val retryState: StateFlow<RetryState> = errorHandlingManager.retryState
    val keyActions = advancedKeyHandler.keyActions

    private val _appSettings = MutableStateFlow(AppSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings

    private val _themeConfig = MutableStateFlow(ThemeConfig())
    val themeConfig: StateFlow<ThemeConfig> = _themeConfig

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            settingsManager.getAppSettings().collect { settings ->
                _appSettings.value = settings
            }
        }

        viewModelScope.launch {
            settingsManager.getThemeConfig().collect { theme ->
                _themeConfig.value = theme
            }
        }
    }

    fun handleKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        return advancedKeyHandler.handleKeyDown(keyCode, event)
    }

    fun customizeKeyBinding(keyCode: Int, actionType: KeyActionType) {
        advancedKeyHandler.customizeKeyBinding(keyCode, actionType)
    }

    fun resetKeyBindings() {
        advancedKeyHandler.resetToDefaults()
    }

    fun handlePlaybackError(exception: androidx.media3.common.PlaybackException) {
        viewModelScope.launch {
            errorHandlingManager.handlePlaybackError(exception)
        }
    }

    fun resetErrorState() {
        errorHandlingManager.resetRetryCount()
    }

    fun saveThemeConfig(theme: ThemeConfig) {
        viewModelScope.launch {
            settingsManager.saveThemeConfig(theme)
            _themeConfig.value = theme
        }
    }

    fun savePlaybackSettings(settings: PlaybackSettings) {
        viewModelScope.launch {
            settingsManager.savePlaybackSettings(settings)
            _appSettings.value = _appSettings.value.copy(playbackSettings = settings)
        }
    }

    fun saveUISettings(settings: UISettings) {
        viewModelScope.launch {
            settingsManager.saveUISettings(settings)
            _appSettings.value = _appSettings.value.copy(uiSettings = settings)
        }
    }

    fun resetSettingsToDefaults() {
        viewModelScope.launch {
            settingsManager.resetToDefaults()
            _appSettings.value = AppSettings()
            _themeConfig.value = ThemeConfig()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            settingsManager.setPlaybackSpeed(speed)
        }
    }

    fun setGridColumns(columns: Int) {
        viewModelScope.launch {
            settingsManager.setGridColumns(columns)
        }
    }

    fun toggleAnimations(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.toggleAnimations(enabled)
        }
    }

    fun getCurrentErrorMessage(): String? {
        return errorHandlingManager.getCurrentErrorEvent()?.message
    }

    fun getKeyBindings(): Map<Int, KeyActionType> {
        return advancedKeyHandler.getKeyBindings()
    }
}
