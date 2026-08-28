package com.dk.tvplayer.util

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

fun Modifier.handlePlaybackShortcuts(
    onPlayPauseToggle: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onNextChannel: () -> Unit,
    onPreviousChannel: () -> Unit,
    onMuteToggle: () -> Unit,
    onPipToggle: () -> Unit
): Modifier = this.onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown) {
        when (event.nativeKeyEvent.keyCode) {
            AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            AndroidKeyEvent.KEYCODE_SPACE,
            AndroidKeyEvent.KEYCODE_DPAD_CENTER -> {
                onPlayPauseToggle()
                true
            }
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
            AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                onSeekForward()
                true
            }
            AndroidKeyEvent.KEYCODE_DPAD_LEFT,
            AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                onSeekBackward()
                true
            }
            AndroidKeyEvent.KEYCODE_CHANNEL_UP,
            AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                onNextChannel()
                true
            }
            AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                onPreviousChannel()
                true
            }
            AndroidKeyEvent.KEYCODE_VOLUME_MUTE,
            AndroidKeyEvent.KEYCODE_M -> {
                onMuteToggle()
                true
            }
            AndroidKeyEvent.KEYCODE_P -> {
                onPipToggle()
                true
            }
            else -> false
        }
    } else false
}
