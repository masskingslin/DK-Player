package com.dk.tvplayer.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.dk.tvplayer.data.local.ChannelEntity
import com.dk.tvplayer.data.local.LocalAudioItem
import com.dk.tvplayer.data.local.LocalAudioScanner
import com.dk.tvplayer.data.local.LocalVideoItem
import com.dk.tvplayer.data.local.LocalVideoScanner
import com.dk.tvplayer.data.local.PlaylistEntity
import com.dk.tvplayer.data.local.StreamEntity
import com.dk.tvplayer.data.local.TvDatabase
import com.dk.tvplayer.data.repository.TvRepository
import com.dk.tvplayer.player.TvExoPlayerManager
import com.dk.tvplayer.ui.components.SortOption
import com.dk.tvplayer.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
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

    private val localVideoScanner = LocalVideoScanner(application)
    private val localAudioScanner = LocalAudioScanner(application)

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow<String?>(null)
    private val _showFavoritesOnly = MutableStateFlow(false)
    private val _selectedSort = MutableStateFlow(SortOption.DEFAULT)
    private val _selectedChannelIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _themeMode = MutableStateFlow(ThemeMode.DARK)
    private val _isInPipMode = MutableStateFlow(false)
    private val _currentChannel = MutableStateFlow<ChannelEntity?>(null)
    private val _selectedChannel = MutableStateFlow<ChannelEntity?>(null)
    private val _playbackSpeed = MutableStateFlow(1.0f)
    private val _localVideos = MutableStateFlow<List<LocalVideoItem>>(emptyList())
    private val _localAudio = MutableStateFlow<List<LocalAudioItem>>(emptyList())

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

    private val currentEpgPrograms = _selectedChannel.flatMapLatest { channel ->
        repository.getEpgForChannel(channel?.epgChannelId)
    }

    // kotlinx.coroutines' `combine` only has typed overloads for up to 5 flows;
    // beyond that it falls back to a vararg `Array<T>` form. We combine the many
    // source flows in groups of <= 5 and then merge the groups together.
    private data class GroupA(
        val channels: List<ChannelEntity>,
        val playlists: List<PlaylistEntity>,
        val categories: List<String>,
        val selectedIds: Set<Long>,
        val theme: ThemeMode
    )

    private data class GroupB(
        val pip: Boolean,
        val curChannel: ChannelEntity?,
        val speed: Float,
        val sleepSecs: Long?,
        val err: String?
    )

    private data class GroupC(
        val subs: List<com.dk.tvplayer.player.SubtitleTrackInfo>,
        val localVideos: List<LocalVideoItem>,
        val localAudio: List<LocalAudioItem>,
        val selectedChannel: ChannelEntity?,
        val history: List<com.dk.tvplayer.data.local.HistoryEntity>
    )

    private data class GroupD(
        val customStreams: List<StreamEntity>,
        val epgPrograms: List<com.dk.tvplayer.data.local.EpgProgramEntity>
    )

    private val groupAFlow = combine(
        filteredChannels,
        repository.getAllPlaylists(),
        repository.getAllCategories(),
        _selectedChannelIds,
        _themeMode
    ) { channels, playlists, categories, selectedIds, theme ->
        GroupA(channels, playlists, categories, selectedIds, theme)
    }

    private val groupBFlow = combine(
        _isInPipMode,
        _currentChannel,
        _playbackSpeed,
        playerManager.sleepTimer.remainingSeconds,
        playerManager.playerError
    ) { pip, curChannel, speed, sleepSecs, err ->
        GroupB(pip, curChannel, speed, sleepSecs, err)
    }

    private val groupCFlow = combine(
        playerManager.subtitleTracks,
        _localVideos,
        _localAudio,
        _selectedChannel,
        repository.getHistory()
    ) { subs, localVideos, localAudio, selectedChannel, history ->
        GroupC(subs, localVideos, localAudio, selectedChannel, history)
    }

    private val groupDFlow = combine(
        repository.getCustomStreams(),
        currentEpgPrograms
    ) { customStreams, epgPrograms ->
        GroupD(customStreams, epgPrograms)
    }

    val uiState: StateFlow<TvUiState> = combine(
        groupAFlow,
        groupBFlow,
        groupCFlow,
        groupDFlow
    ) { a, b, c, d ->
        TvUiState(
            filteredChannels = a.channels,
            playlists = a.playlists,
            categories = a.categories,
            selectedCategory = _selectedCategory.value,
            searchQuery = _searchQuery.value,
            showFavoritesOnly = _showFavoritesOnly.value,
            selectedSort = _selectedSort.value,
            selectedChannel = c.selectedChannel,
            currentlyPlayingChannel = b.curChannel,
            isPlaying = playerManager.player.isPlaying,
            isInPipMode = b.pip,
            playbackSpeed = b.speed,
            remainingSleepSeconds = b.sleepSecs,
            playerError = b.err,
            subtitleTracks = c.subs,
            selectedChannelIds = a.selectedIds,
            themeMode = a.theme,
            localVideos = c.localVideos,
            localAudio = c.localAudio,
            currentEpgPrograms = d.epgPrograms,
            history = c.history,
            customStreams = d.customStreams
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

    // Aliases used by the phone/TV browsing screens.
    fun updateSearchQuery(query: String) = onSearchQueryChanged(query)
    fun selectCategory(category: String?) = onCategorySelected(category)
    fun toggleShowFavoritesOnly() = onToggleFavorites()

    fun playChannel(channel: ChannelEntity, subtitleUri: Uri? = null) {
        _currentChannel.value = channel
        playerManager.playStream(channel.url, subtitleUri)
        viewModelScope.launch {
            repository.recordChannelPlayed(channel)
            repository.recordHistory(channel.name, channel.url)
        }
    }

    /** Selects a channel for TV-style browsing (drives the EPG overlay) and starts playback. */
    fun selectChannel(channel: ChannelEntity) {
        _selectedChannel.value = channel
        playChannel(channel)
    }

    /** Records an arbitrary played item (local file, custom stream, etc.) into history. */
    fun recordHistory(title: String, mediaUrl: String) {
        viewModelScope.launch { repository.recordHistory(title, mediaUrl) }
    }

    fun refreshLocalVideos() {
        viewModelScope.launch(Dispatchers.IO) {
            _localVideos.value = localVideoScanner.scanDeviceVideos()
        }
    }

    fun refreshLocalAudio() {
        viewModelScope.launch(Dispatchers.IO) {
            _localAudio.value = localAudioScanner.scanDeviceAudio()
        }
    }

    fun addCustomStream(name: String, url: String) {
        viewModelScope.launch { repository.addCustomStream(name, url) }
    }

    fun deleteCustomStream(stream: StreamEntity) {
        viewModelScope.launch { repository.deleteCustomStream(stream) }
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
