package com.dk.tvplayer.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SleepTimerManager(
    private val onTimerExpired: () -> Unit
) {
    private var timerJob: Job? = null
    private val _remainingSeconds = MutableStateFlow<Long?>(null)
    val remainingSeconds = _remainingSeconds.asStateFlow()

    fun setTimer(minutes: Int, scope: CoroutineScope) {
        timerJob?.cancel()
        if (minutes <= 0) {
            _remainingSeconds.value = null
            return
        }
        timerJob = scope.launch {
            var timeLeft = minutes * 60L
            while (timeLeft > 0) {
                _remainingSeconds.value = timeLeft
                delay(1000L)
                timeLeft--
            }
            _remainingSeconds.value = null
            onTimerExpired()
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        _remainingSeconds.value = null
    }
}
