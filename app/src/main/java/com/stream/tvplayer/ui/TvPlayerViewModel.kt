package com.stream.tvplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stream.tvplayer.data.local.TvDatabase
import com.stream.tvplayer.data.repository.TvRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TvPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TvRepository(TvDatabase.getInstance(application).tvDao())
    private val _uiState = MutableStateFlow(TvUiState())
    val uiState: StateFlow<TvUiState> = _uiState.asStateFlow()

    private var autoHideJob: Job? = null

    init {
        viewModelScope.launch {
            repository.getChannelsStream().collect { list ->
                _uiState.update { it.copy(channels = list) }
                if (list.isNotEmpty() && _uiState.value.currentChannel == null) {
                    selectChannel(0)
                }
            }
        }
    }

    fun syncFeeds(m3uUrl: String, epgUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.syncPlaylist(m3uUrl)
                repository.syncEpg(epgUrl)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    fun selectChannel(index: Int) {
        val list = _uiState.value.channels
        if (index in list.indices) {
            _uiState.update { it.copy(currentChannelIndex = index, isOverlayVisible = true) }
            refreshScheduleForCurrent()
            triggerOverlayTimeout()
        }
    }

    fun nextChannel() {
        val total = _uiState.value.channels.size
        if (total > 0) {
            val next = (_uiState.value.currentChannelIndex + 1) % total
            selectChannel(next)
        }
    }

    fun prevChannel() {
        val total = _uiState.value.channels.size
        if (total > 0) {
            val prev = if (_uiState.value.currentChannelIndex > 0) {
                _uiState.value.currentChannelIndex - 1
            } else total - 1
            selectChannel(prev)
        }
    }

    private fun refreshScheduleForCurrent() {
        val channel = _uiState.value.currentChannel ?: return
        viewModelScope.launch {
            val (current, next) = repository.getLiveSchedule(channel.tvgId ?: channel.name)
            _uiState.update { it.copy(currentProgram = current, nextProgram = next) }
        }
    }

    fun toggleSidebar(open: Boolean? = null) {
        val target = open ?: !_uiState.value.isSidebarOpen
        _uiState.update { it.copy(isSidebarOpen = target) }
    }

    fun toggleOverlay() {
        val next = !_uiState.value.isOverlayVisible
        _uiState.update { it.copy(isOverlayVisible = next) }
        if (next) triggerOverlayTimeout()
    }

    private fun triggerOverlayTimeout() {
        autoHideJob?.cancel()
        autoHideJob = viewModelScope.launch {
            delay(5000)
            _uiState.update { it.copy(isOverlayVisible = false) }
        }
    }
}
