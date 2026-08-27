package com.stream.tvplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stream.tvplayer.data.local.ChannelEntity
import com.stream.tvplayer.data.local.HistoryEntity
import com.stream.tvplayer.data.local.TvDatabase
import com.stream.tvplayer.data.repository.TvRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TvPlayerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val DEFAULT_M3U_URL = "https://iptv-org.github.io/iptv/index.m3u"
    }

    private val db = TvDatabase.getInstance(application)
    private val repository = TvRepository(db.tvDao(), db.historyDao(), db.streamDao())
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

        viewModelScope.launch {
            val existing = repository.getChannelsStream().first()
            if (existing.isEmpty()) {
                loadDefaultPlaylist()
            }
        }

        viewModelScope.launch {
            repository.getHistoryStream().collect { list ->
                _uiState.update { it.copy(history = list) }
            }
        }

        viewModelScope.launch {
            repository.getStreamsFlow().collect { list ->
                _uiState.update { it.copy(streams = list) }
            }
        }
    }

    fun loadDefaultPlaylist() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.syncPlaylist(DEFAULT_M3U_URL)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage) }
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

    fun selectChannel(channel: ChannelEntity) {
        val index = _uiState.value.channels.indexOf(channel)
        if (index >= 0) selectChannel(index)
    }

    fun toggleShuffle() {
        _uiState.update { it.copy(shuffleEnabled = !it.shuffleEnabled) }
    }

    fun nextChannel() {
        val total = _uiState.value.channels.size
        if (total > 0) {
            val next = if (_uiState.value.shuffleEnabled) {
                randomChannelIndexExcluding(_uiState.value.currentChannelIndex)
            } else {
                (_uiState.value.currentChannelIndex + 1) % total
            }
            selectChannel(next)
        }
    }

    fun prevChannel() {
        val total = _uiState.value.channels.size
        if (total > 0) {
            val prev = if (_uiState.value.shuffleEnabled) {
                randomChannelIndexExcluding(_uiState.value.currentChannelIndex)
            } else if (_uiState.value.currentChannelIndex > 0) {
                _uiState.value.currentChannelIndex - 1
            } else total - 1
            selectChannel(prev)
        }
    }

    private fun randomChannelIndexExcluding(current: Int): Int {
        val total = _uiState.value.channels.size
        if (total <= 1) return current
        var candidate: Int
        do {
            candidate = (0 until total).random()
        } while (candidate == current)
        return candidate
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

    // --- Search / category / favorites ---

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectCategory(category: String?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun toggleShowFavoritesOnly() {
        _uiState.update { it.copy(showFavoritesOnly = !it.showFavoritesOnly) }
    }

    fun toggleFavorite(channelId: Long) {
        _uiState.update { state ->
            val updated = state.favoriteChannelIds.toMutableSet()
            if (!updated.add(channelId)) updated.remove(channelId)
            state.copy(favoriteChannelIds = updated)
        }
    }

    // --- History ---

    fun recordHistory(entry: HistoryEntity) {
        viewModelScope.launch { repository.saveHistoryEntry(entry) }
    }

    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch { repository.deleteHistoryEntry(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }

    // --- Streams ---

    fun addStream(name: String, url: String) {
        viewModelScope.launch { repository.addStream(name, url) }
    }

    fun deleteStream(id: Long) {
        viewModelScope.launch { repository.deleteStream(id) }
    }
}