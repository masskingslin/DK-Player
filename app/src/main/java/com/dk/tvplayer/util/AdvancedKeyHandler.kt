package com.dk.tvplayer.util

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

data class KeyAction(
    val type: KeyActionType,
    val keyCode: Int,
    val timestamp: Long = System.currentTimeMillis()
)

enum class KeyActionType {
    PLAY_PAUSE,
    VOLUME_UP,
    VOLUME_DOWN,
    SEEK_FORWARD,
    SEEK_BACKWARD,
    INCREASE_SPEED,
    DECREASE_SPEED,
    ENABLE_PIP,
    DISABLE_PIP,
    TOGGLE_SUBTITLES,
    NEXT_SUBTITLE,
    PREVIOUS_SUBTITLE,
    OPEN_MENU,
    CLOSE_MENU,
    SELECT_CHANNEL,
    FAVORITE_TOGGLE,
    SEARCH_OPEN,
    SETTINGS_OPEN,
    FULLSCREEN_TOGGLE,
    SLEEP_TIMER_TOGGLE,
    CHROMECAST_MENU,
    BATCH_SELECT_TOGGLE
}

class AdvancedKeyHandler {
    private val _keyActions = MutableSharedFlow<KeyAction>(replay = 0, extraBufferCapacity = 10)
    val keyActions: SharedFlow<KeyAction> = _keyActions

    private val keyBindings = mutableMapOf<Int, KeyActionType>().apply {
        this[KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE] = KeyActionType.PLAY_PAUSE
        this[KeyEvent.KEYCODE_SPACE] = KeyActionType.PLAY_PAUSE
        this[KeyEvent.KEYCODE_VOLUME_UP] = KeyActionType.VOLUME_UP
        this[KeyEvent.KEYCODE_VOLUME_DOWN] = KeyActionType.VOLUME_DOWN
        this[KeyEvent.KEYCODE_DPAD_RIGHT] = KeyActionType.SEEK_FORWARD
        this[KeyEvent.KEYCODE_DPAD_LEFT] = KeyActionType.SEEK_BACKWARD
        this[KeyEvent.KEYCODE_DPAD_UP] = KeyActionType.INCREASE_SPEED
        this[KeyEvent.KEYCODE_DPAD_DOWN] = KeyActionType.DECREASE_SPEED
        this[KeyEvent.KEYCODE_P] = KeyActionType.ENABLE_PIP
        this[KeyEvent.KEYCODE_S] = KeyActionType.TOGGLE_SUBTITLES
        this[KeyEvent.KEYCODE_N] = KeyActionType.NEXT_SUBTITLE
        this[KeyEvent.KEYCODE_B] = KeyActionType.PREVIOUS_SUBTITLE
        this[KeyEvent.KEYCODE_M] = KeyActionType.OPEN_MENU
        this[KeyEvent.KEYCODE_ESCAPE] = KeyActionType.CLOSE_MENU
        this[KeyEvent.KEYCODE_F] = KeyActionType.FULLSCREEN_TOGGLE
        this[KeyEvent.KEYCODE_T] = KeyActionType.SLEEP_TIMER_TOGGLE
        this[KeyEvent.KEYCODE_C] = KeyActionType.CHROMECAST_MENU
        this[KeyEvent.KEYCODE_Q] = KeyActionType.BATCH_SELECT_TOGGLE
        this[KeyEvent.KEYCODE_SLASH] = KeyActionType.SEARCH_OPEN
        this[KeyEvent.KEYCODE_COMMA] = KeyActionType.SETTINGS_OPEN
    }

    fun handleKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val actionType = keyBindings[keyCode] ?: return false
        
        try {
            _keyActions.tryEmit(KeyAction(actionType, keyCode))
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun customizeKeyBinding(keyCode: Int, actionType: KeyActionType) {
        keyBindings[keyCode] = actionType
    }

    fun removeKeyBinding(keyCode: Int) {
        keyBindings.remove(keyCode)
    }

    fun getKeyBindings(): Map<Int, KeyActionType> = keyBindings.toMap()

    fun resetToDefaults() {
        keyBindings.clear()
        keyBindings.apply {
            this[KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE] = KeyActionType.PLAY_PAUSE
            this[KeyEvent.KEYCODE_SPACE] = KeyActionType.PLAY_PAUSE
            this[KeyEvent.KEYCODE_VOLUME_UP] = KeyActionType.VOLUME_UP
            this[KeyEvent.KEYCODE_VOLUME_DOWN] = KeyActionType.VOLUME_DOWN
            this[KeyEvent.KEYCODE_DPAD_RIGHT] = KeyActionType.SEEK_FORWARD
            this[KeyEvent.KEYCODE_DPAD_LEFT] = KeyActionType.SEEK_BACKWARD
        }
    }
}
