package com.dk.tvplayer.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.dk.tvplayer.data.local.ChannelEntity
import com.dk.tvplayer.data.local.PlaylistEntity
import com.dk.tvplayer.data.local.TvDatabase
import com.dk.tvplayer.data.repository.TvRepository
import com.dk.tvplayer.player.TvExoPlayerManager
import com.dk.tvplayer.ui.components.SortOption
import com.dk.tvplayer.ui.theme.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TvPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Room.databaseBuilder(
        application,
        TvDatabase::class.java,
        "tv_database"
    ).fallbackToDestructiveMigration().build()

    private val repository = TvRepository(application, db.tvDao())
    val playerManager = TvExoPlayerManager(application, viewModelScope)

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow<String?>(null)
    private val _showFavoritesOnly = MutableStateFlow(false)
    private val _selectedSort = MutableStateFlow(SortOption.DEFAULT)
    private val _selectedChannelIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _themeMode = MutableStateFlow(ThemeMode.DARK)
    private val _isInPipMode = MutableStateFlow(false)
    private val _currentChannel = MutableStateFlow<ChannelEntity?>(null)
    private val _playbackSpeed = MutableStateFlow(1.0f)

    private val filteredChannels = combine(
        _searchQuery,
        _selectedCategory,
        _showFavoritesOnly,
        _selectedSort
    ) { q, cat, fav, sort ->
        Tuple4(q, cat, fav, sort)
    }.flatMapLatest { tuple ->
        repository.getFilteredChannels(
            query = tuple.v1.ifBlank { null },
            category = tuple.v2,
            onlyFavorites = tuple.v3,
            sortBy = tuple.v4.name
        )
    }

    val uiState: StateFlow<TvUiState> = combine(
        filteredChannels,
        repository.getAllPlaylists(),
        repository.getAllCategories(),
        _selectedChannelIds,
        _themeMode,
        _isInPipMode,
        _currentChannel,
        _playbackSpeed,
        playerManager.sleepTimer.remainingSeconds,
        playerManager.playerError,
        playerManager.subtitleTracks
    ) { channels, playlists, categories, selectedIds, theme, pip, curChannel, speed, sleepSecs, err, subs ->
        TvUiState(
            channels = channels,
            playlists = playlists,
            categories = categories,
            selectedCategory = _selectedCategory.value,
            searchQuery = _searchQuery.value,
            showFavoritesOnly = _showFavoritesOnly.value,
            selectedSort = _selectedSort.value,
            currentlyPlayingChannel = curChannel,
            isPlaying = playerManager.player.isPlaying,
            isInPipMode = pip,
            playbackSpeed = speed,
            remainingSleepSeconds = sleepSecs,
            playerError = err,
            subtitleTracks = subs,
            selectedChannelIds = selectedIds,
            themeMode = theme
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TvUiState()
    )

    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }
    fun onCategorySelected(category: String?) { _selectedCategory.value = category }
    fun onToggleFavorites() { _showFavoritesOnly.value = !_showFavoritesOnly.value }
    fun onSortSelected(sort: SortOption) { _selectedSort.value = sort }

    fun playChannel(channel: ChannelEntity, subtitleUri: Uri? = null) {
        _currentChannel.value = channel
        playerManager.playStream(channel.url, subtitleUri)
        viewModelScope.launch { repository.recordChannelPlayed(channel) }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        playerManager.setPlaybackSpeed(speed)
    }

    fun setSleepTimer(minutes: Int) {
        playerManager.sleepTimer.setTimer(minutes, viewModelScope)
    }

    fun setPipMode(enabled: Boolean) {
        _isInPipMode.value = enabled
    }

    fun toggleChannelSelection(channelId: Long) {
        val current = _selectedChannelIds.value.toMutableSet()
        if (current.contains(channelId)) current.remove(channelId) else current.add(channelId)
        _selectedChannelIds.value = current
    }

    fun selectAllChannels(channels: List<ChannelEntity>) {
        _selectedChannelIds.value = channels.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedChannelIds.value = emptySet()
    }

    fun batchDeleteSelected() {
        val ids = _selectedChannelIds.value.toList()
        viewModelScope.launch {
            repository.batchDeleteChannels(ids)
            clearSelection()
        }
    }

    fun batchMoveSelected(targetGroup: String) {
        val ids = _selectedChannelIds.value.toList()
        viewModelScope.launch {
            repository.batchMoveChannels(ids, targetGroup)
            clearSelection()
        }
    }

    fun addPlaylist(title: String, urlOrPath: String, isLocal: Boolean) {
        viewModelScope.launch { repository.addPlaylist(title, urlOrPath, isLocal) }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch { repository.deletePlaylist(playlist) }
    }

    fun syncPlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch { repository.syncPlaylist(playlist.id, playlist.urlOrPath, playlist.isLocalFile) }
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch { repository.toggleFavorite(channel) }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    fun exportSettings(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repository.exportDataToJson(uri)) }
    }

    fun importSettings(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repository.importDataFromJson(uri)) }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }

    private data class Tuple4<A, B, C, D>(val v1: A, val v2: B, val v3: C, val v4: D)
}
