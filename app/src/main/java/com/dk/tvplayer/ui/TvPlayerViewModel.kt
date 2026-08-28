package com.dk.tvplayer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dk.tvplayer.data.local.StreamEntity
import com.dk.tvplayer.data.local.TvChannelEntity
import com.dk.tvplayer.data.repository.TvRepository
import com.dk.tvplayer.player.TvExoPlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream

class TvPlayerViewModel(
    private val repository: TvRepository,
    val playerManager: TvExoPlayerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(TvUiState())
    val uiState: StateFlow<TvUiState> = _uiState.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            repository.getAllChannels().collect { list ->
                _uiState.update { current ->
                    current.copy(
                        channels = list,
                        filteredChannels = filterChannels(
                            list,
                            current.selectedCategory,
                            current.searchQuery,
                            current.favoriteChannelIds,
                            current.showFavoritesOnly
                        )
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.getAllGroups().collect { groups ->
                _uiState.update { it.copy(categories = listOf("All") + groups) }
            }
        }

        viewModelScope.launch {
            repository.getHistory().collect { hist ->
                _uiState.update { it.copy(history = hist) }
            }
        }

        viewModelScope.launch {
            repository.getCustomStreams().collect { streams ->
                _uiState.update { it.copy(customStreams = streams) }
            }
        }
    }

    fun selectCategory(category: String) {
        _uiState.update { current ->
            current.copy(
                selectedCategory = category,
                filteredChannels = filterChannels(
                    current.channels,
                    category,
                    current.searchQuery,
                    current.favoriteChannelIds,
                    current.showFavoritesOnly
                )
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { current ->
            current.copy(
                searchQuery = query,
                filteredChannels = filterChannels(
                    current.channels,
                    current.selectedCategory,
                    query,
                    current.favoriteChannelIds,
                    current.showFavoritesOnly
                )
            )
        }
    }

    fun toggleFavorite(channelId: String) {
        _uiState.update { current ->
            val updated = current.favoriteChannelIds.toMutableSet()
            if (!updated.add(channelId)) updated.remove(channelId)
            current.copy(
                favoriteChannelIds = updated,
                filteredChannels = filterChannels(
                    current.channels,
                    current.selectedCategory,
                    current.searchQuery,
                    updated,
                    current.showFavoritesOnly
                )
            )
        }
    }

    fun toggleShowFavoritesOnly() {
        _uiState.update { current ->
            val newValue = !current.showFavoritesOnly
            current.copy(
                showFavoritesOnly = newValue,
                filteredChannels = filterChannels(
                    current.channels,
                    current.selectedCategory,
                    current.searchQuery,
                    current.favoriteChannelIds,
                    newValue
                )
            )
        }
    }

    fun selectChannel(channel: TvChannelEntity) {
        _uiState.update { it.copy(selectedChannel = channel) }
        playerManager.play(channel.streamUrl)
        observeEpg(channel.channelId)
    }

    fun playMedia(url: String, title: String) {
        playerManager.play(url)
        savePlaybackProgress(url, title, 0L, 0L)
    }

    fun savePlaybackProgress(url: String, title: String, position: Long, duration: Long) {
        viewModelScope.launch {
            repository.saveHistory(url, title, position, duration)
        }
    }

    fun addCustomStream(name: String, url: String) {
        viewModelScope.launch {
            repository.insertCustomStream(name, url)
        }
    }

    fun deleteCustomStream(stream: StreamEntity) {
        viewModelScope.launch {
            repository.deleteCustomStream(stream)
        }
    }

    fun refreshLocalVideos() {
        viewModelScope.launch {
            val videos = repository.scanLocalVideos()
            _uiState.update { it.copy(localVideos = videos) }
        }
    }

    fun refreshLocalAudio() {
        viewModelScope.launch {
            val audio = repository.scanLocalAudio()
            _uiState.update { it.copy(localAudio = audio) }
        }
    }

    fun importM3u(inputStream: InputStream) {
        viewModelScope.launch {
            repository.loadM3u(inputStream)
        }
    }

    private fun observeEpg(channelId: String) {
        viewModelScope.launch {
            repository.getPrograms(channelId).collect { programs ->
                _uiState.update { it.copy(currentEpgPrograms = programs) }
            }
        }
    }

    private fun filterChannels(
        channels: List<TvChannelEntity>,
        category: String,
        query: String,
        favoriteIds: Set<String>,
        showFavoritesOnly: Boolean
    ): List<TvChannelEntity> {
        return channels.filter { channel ->
            val matchesCategory = (category == "All" || channel.groupTitle.equals(category, ignoreCase = true))
            val matchesQuery = query.isEmpty() || channel.name.contains(query, ignoreCase = true)
            val matchesFavorite = !showFavoritesOnly || favoriteIds.contains(channel.channelId)
            matchesCategory && matchesQuery && matchesFavorite
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}