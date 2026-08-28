package com.dk.tvplayer.util

import androidx.media3.common.PlaybackException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.min

data class RetryPolicy(
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 10000,
    val backoffMultiplier: Float = 2.0f
)

data class ErrorEvent(
    val errorCode: Int,
    val message: String,
    val exception: Exception?,
    val timestamp: Long = System.currentTimeMillis(),
    val retryAttempt: Int = 0,
    val isRecoverable: Boolean = true
)

class ErrorHandlingManager {
    private val _errorEvents = MutableStateFlow<ErrorEvent?>(null)
    val errorEvents: StateFlow<ErrorEvent?> = _errorEvents

    private val _retryState = MutableStateFlow<RetryState>(RetryState.Idle)
    val retryState: StateFlow<RetryState> = _retryState

    private val retryPolicy = RetryPolicy()
    private var currentRetryCount = 0

    suspend fun handlePlaybackError(exception: PlaybackException): Boolean {
        val isRecoverable = isErrorRecoverable(exception)
        val errorMessage = getErrorMessage(exception)
        
        val errorEvent = ErrorEvent(
            errorCode = exception.errorCode,
            message = errorMessage,
            exception = exception,
            isRecoverable = isRecoverable,
            retryAttempt = currentRetryCount
        )
        
        _errorEvents.value = errorEvent
        
        return if (isRecoverable && currentRetryCount < retryPolicy.maxRetries) {
            attemptRetry()
        } else {
            _retryState.value = RetryState.Failed
            false
        }
    }

    private suspend fun attemptRetry(): Boolean {
        _retryState.value = RetryState.Retrying(currentRetryCount + 1)
        currentRetryCount++
        
        val delayMs = calculateBackoffDelay()
        delay(delayMs)
        
        return true
    }

    private fun calculateBackoffDelay(): Long {
        val exponentialDelay = retryPolicy.initialDelayMs * 
            Math.pow(retryPolicy.backoffMultiplier.toDouble(), currentRetryCount.toDouble()).toLong()
        return min(exponentialDelay, retryPolicy.maxDelayMs)
    }

    private fun isErrorRecoverable(exception: PlaybackException): Boolean {
        return when (exception.errorCode) {
            PlaybackException.ERROR_CODE_TIMEOUT -> true
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> true
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> true
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> true
            PlaybackException.ERROR_CODE_REMOTE_ERROR -> true
            else -> false
        }
    }

    private fun getErrorMessage(exception: PlaybackException): String {
        return when (exception.errorCode) {
            PlaybackException.ERROR_CODE_TIMEOUT -> "Connection timeout. Please check your network."
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Network connection failed. Please check your internet."
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Network timeout. Please try again."
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "Invalid video format. Stream may not be compatible."
            PlaybackException.ERROR_CODE_REMOTE_ERROR -> "Server error. Please try again later."
            PlaybackException.ERROR_CODE_UNSPECIFIED -> "An unexpected error occurred."
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> "Stream is behind live window."
            else -> "Playback error: ${exception.message}"
        }
    }

    fun resetRetryCount() {
        currentRetryCount = 0
        _retryState.value = RetryState.Idle
    }

    fun getCurrentErrorEvent(): ErrorEvent? = _errorEvents.value
}

sealed class RetryState {
    object Idle : RetryState()
    data class Retrying(val attemptNumber: Int) : RetryState()
    object Success : RetryState()
    object Failed : RetryState()
}
