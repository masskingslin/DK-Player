package com.dk.tvplayer.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dk.tvplayer.data.backup.BackupBundle
import com.dk.tvplayer.data.backup.BackupPlaylist
import com.dk.tvplayer.data.backup.SettingsBackupManager
import com.dk.tvplayer.data.local.AppLanguage
import com.dk.tvplayer.data.local.AppThemeMode
import com.dk.tvplayer.data.local.PlaylistEntity
import com.dk.tvplayer.data.local.PlaylistItemEntity
import com.dk.tvplayer.data.local.SettingsDataStore
import com.dk.tvplayer.data.local.SortOption
import com.dk.tvplayer.data.local.StreamEntity
import com.dk.tvplayer.data.local.SubtitleColorPreset
import com.dk.tvplayer.data.local.SubtitleTextSize
import com.dk.tvplayer.data.local.TvChannelEntity
import com.dk.tvplayer.data.local.VideoResolutionCap
import com.dk.tvplayer.data.parser.PlaylistExporter
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
    val playerManager: TvExoPlayerManager,
    private val settingsDataStore: SettingsDataStore
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
                        filteredChannels = filterAndSortChannels(
                            list,
                            current.selectedCategory,
                            current.searchQuery,
                            current.favoriteChannelIds,
                            current.showFavoritesOnly,
                            current.sortOption
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

        viewModelScope.launch {
            repository.getAllPlaylists().collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }

        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                _uiState.update { it.copy(sortOption = settings.sortOption, appSettings = settings) }
                // Keep the live player in sync with settings that can change at runtime
                // (hardware acceleration is the one exception — see TvExoPlayerManager).
                playerManager.setPlaybackSpeed(settings.defaultPlaybackSpeed)
                playerManager.setFastSeekEnabled(settings.fastSeekEnabled)
                playerManager.setMaxVideoResolution(settings.maxVideoResolution.width, settings.maxVideoResolution.height)
                playerManager.setBackgroundPlaybackEnabled(settings.backgroundAudioPlayback)
                applyLocaleIfNeeded(settings.appLanguage)
            }
        }
    }

    // ---- Search / filter / sort ----

    fun selectCategory(category: String) {
        _uiState.update { current ->
            current.copy(
                selectedCategory = category,
                filteredChannels = filterAndSortChannels(
                    current.channels, category, current.searchQuery,
                    current.favoriteChannelIds, current.showFavoritesOnly, current.sortOption
                )
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { current ->
            current.copy(
                searchQuery = query,
                filteredChannels = filterAndSortChannels(
                    current.channels, current.selectedCategory, query,
                    current.favoriteChannelIds, current.showFavoritesOnly, current.sortOption
                )
            )
        }
    }

    fun setSortOption(option: SortOption) {
        viewModelScope.launch { settingsDataStore.setSortOption(option) }
        _uiState.update { current ->
            current.copy(
                sortOption = option,
                filteredChannels = filterAndSortChannels(
                    current.channels, current.selectedCategory, current.searchQuery,
                    current.favoriteChannelIds, current.showFavoritesOnly, option
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
                filteredChannels = filterAndSortChannels(
                    current.channels, current.selectedCategory, current.searchQuery,
                    updated, current.showFavoritesOnly, current.sortOption
                )
            )
        }
    }

    fun toggleShowFavoritesOnly() {
        _uiState.update { current ->
            val newValue = !current.showFavoritesOnly
            current.copy(
                showFavoritesOnly = newValue,
                filteredChannels = filterAndSortChannels(
                    current.channels, current.selectedCategory, current.searchQuery,
                    current.favoriteChannelIds, newValue, current.sortOption
                )
            )
        }
    }

    private fun filterAndSortChannels(
        channels: List<TvChannelEntity>,
        category: String,
        query: String,
        favoriteIds: Set<String>,
        showFavoritesOnly: Boolean,
        sortOption: SortOption
    ): List<TvChannelEntity> {
        val trimmedQuery = query.trim()
        val filtered = channels.filter { channel ->
            val matchesCategory = (category == "All" || channel.groupTitle.equals(category, ignoreCase = true))
            // Search across name, group and channel id — not just name — for better discoverability.
            val matchesQuery = trimmedQuery.isEmpty() ||
                channel.name.contains(trimmedQuery, ignoreCase = true) ||
                channel.groupTitle.contains(trimmedQuery, ignoreCase = true) ||
                channel.channelId.contains(trimmedQuery, ignoreCase = true)
            val matchesFavorite = !showFavoritesOnly || favoriteIds.contains(channel.channelId)
            matchesCategory && matchesQuery && matchesFavorite
        }
        return when (sortOption) {
            SortOption.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            SortOption.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            SortOption.RECENTLY_ADDED -> filtered.sortedByDescending { it.id }
            SortOption.FAVORITES_FIRST -> filtered.sortedWith(
                compareByDescending<TvChannelEntity> { favoriteIds.contains(it.channelId) }
                    .thenBy { it.name.lowercase() }
            )
        }
    }

    // ---- Playback ----

    fun selectChannel(channel: TvChannelEntity) {
        _uiState.update { it.copy(selectedChannel = channel) }
        playerManager.play(channel.streamUrl, title = channel.name)
        observeEpg(channel.channelId)
    }

    /**
     * Plays a piece of media, resuming from the last saved position when "Auto Resume
     * Playback" is enabled and there's a meaningful saved position (not right at the
     * start, and not already at/near the end).
     */
    /**
     * Plays a piece of media, resuming from the last saved position when "Auto Resume
     * Playback" is enabled and there's a meaningful saved position (not right at the
     * start, and not already at/near the end). Skipped entirely in Incognito Mode.
     */
    fun playMedia(url: String, title: String) {
        viewModelScope.launch {
            val settings = _uiState.value.appSettings
            val startPositionMs = if (settings.autoResumePlayback && !settings.incognitoMode) {
                val entry = repository.getHistoryEntryOnce(url)
                val savedPosition = entry?.lastPositionMs ?: 0L
                val savedDuration = entry?.durationMs ?: 0L
                val nearEnd = savedDuration > 0 && savedPosition >= savedDuration - 5_000
                if (savedPosition > 5_000 && !nearEnd) savedPosition else 0L
            } else {
                0L
            }
            playerManager.play(url, startPositionMs = startPositionMs, title = title)
        }
    }

    /** No-op in Incognito Mode — nothing gets written to playback history. */
    fun savePlaybackProgress(url: String, title: String, position: Long, duration: Long) {
        if (_uiState.value.appSettings.incognitoMode) return
        viewModelScope.launch {
            repository.saveHistory(url, title, position, duration)
        }
    }

    private fun observeEpg(channelId: String) {
        viewModelScope.launch {
            repository.getPrograms(channelId).collect { programs ->
                _uiState.update { it.copy(currentEpgPrograms = programs) }
            }
        }
    }

    // ---- Custom streams ----

    fun addCustomStream(name: String, url: String) {
        viewModelScope.launch { repository.insertCustomStream(name, url) }
    }

    fun deleteCustomStream(stream: StreamEntity) {
        viewModelScope.launch { repository.deleteCustomStream(stream) }
    }

    fun deleteCustomStreams(streams: List<StreamEntity>) {
        viewModelScope.launch { repository.deleteCustomStreams(streams) }
    }

    // ---- Local media ----

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
        viewModelScope.launch { repository.loadM3u(inputStream) }
    }

    // ---- Playlist management ----

    fun createPlaylist(name: String) {
        viewModelScope.launch { repository.createPlaylist(name.ifBlank { "New Playlist" }) }
    }

    fun renamePlaylist(playlist: PlaylistEntity, newName: String) {
        viewModelScope.launch { repository.renamePlaylist(playlist, newName) }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
            _uiState.update {
                if (it.selectedPlaylist?.id == playlist.id) {
                    it.copy(selectedPlaylist = null, selectedPlaylistItems = emptyList(), selectedPlaylistItemIds = emptySet())
                } else it
            }
        }
    }

    fun selectPlaylist(playlist: PlaylistEntity) {
        _uiState.update { it.copy(selectedPlaylist = playlist, selectedPlaylistItemIds = emptySet()) }
        viewModelScope.launch {
            repository.getPlaylistItems(playlist.id).collect { items ->
                _uiState.update { it.copy(selectedPlaylistItems = items) }
            }
        }
    }

    fun addChannelToPlaylist(playlistId: Long, channel: TvChannelEntity) {
        viewModelScope.launch {
            repository.addItemToPlaylist(playlistId, channel.name, channel.streamUrl, channel.groupTitle, channel.logoUrl)
        }
    }

    fun addStreamToPlaylist(playlistId: Long, stream: StreamEntity) {
        viewModelScope.launch {
            repository.addItemToPlaylist(playlistId, stream.name, stream.streamUrl, stream.groupTitle, stream.logoUrl)
        }
    }

    fun addCustomItemToPlaylist(playlistId: Long, title: String, url: String) {
        viewModelScope.launch { repository.addItemToPlaylist(playlistId, title, url) }
    }

    fun removeItemFromPlaylist(item: PlaylistItemEntity) {
        viewModelScope.launch { repository.removeItemFromPlaylist(item) }
    }

    fun importM3uIntoSelectedPlaylist(inputStream: InputStream) {
        val playlistId = _uiState.value.selectedPlaylist?.id ?: return
        viewModelScope.launch { repository.importM3uIntoPlaylist(playlistId, inputStream) }
    }

    /** Moves an item one slot up/down within the currently viewed playlist and persists the new order. */
    fun movePlaylistItem(item: PlaylistItemEntity, moveUp: Boolean) {
        val current = _uiState.value.selectedPlaylistItems.toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        if (index < 0) return
        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex < 0 || targetIndex >= current.size) return
        val a = current[index]
        val b = current[targetIndex]
        current[index] = b
        current[targetIndex] = a
        _uiState.update { it.copy(selectedPlaylistItems = current) }
        viewModelScope.launch { repository.reorderPlaylistItems(current) }
    }

    suspend fun exportSelectedPlaylistAsM3u(): String {
        val items = _uiState.value.selectedPlaylistItems
        return PlaylistExporter.toM3u(items)
    }

    suspend fun exportSelectedPlaylistAsXspf(): String {
        val playlist = _uiState.value.selectedPlaylist
        val items = _uiState.value.selectedPlaylistItems
        return PlaylistExporter.toXspf(playlist?.name ?: "Playlist", items)
    }

    // ---- Batch operations (multi-select) ----

    fun toggleItemSelected(itemId: Long) {
        _uiState.update { current ->
            val updated = current.selectedPlaylistItemIds.toMutableSet()
            if (!updated.add(itemId)) updated.remove(itemId)
            current.copy(selectedPlaylistItemIds = updated)
        }
    }

    fun selectAllPlaylistItems() {
        _uiState.update { it.copy(selectedPlaylistItemIds = it.selectedPlaylistItems.map { item -> item.id }.toSet()) }
    }

    fun clearPlaylistItemSelection() {
        _uiState.update { it.copy(selectedPlaylistItemIds = emptySet()) }
    }

    fun deleteSelectedPlaylistItems() {
        val selectedIds = _uiState.value.selectedPlaylistItemIds
        val items = _uiState.value.selectedPlaylistItems.filter { selectedIds.contains(it.id) }
        if (items.isEmpty()) return
        viewModelScope.launch {
            repository.removeItemsFromPlaylist(items)
            _uiState.update { it.copy(selectedPlaylistItemIds = emptySet()) }
        }
    }

    fun moveSelectedPlaylistItemsTo(targetPlaylistId: Long) {
        val selectedIds = _uiState.value.selectedPlaylistItemIds
        val items = _uiState.value.selectedPlaylistItems.filter { selectedIds.contains(it.id) }
        if (items.isEmpty()) return
        viewModelScope.launch {
            repository.moveItemsToPlaylist(items, targetPlaylistId)
            _uiState.update { it.copy(selectedPlaylistItemIds = emptySet()) }
        }
    }

    // ---- Theme customization ----

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { settingsDataStore.setThemeMode(mode) }
    }

    fun setThemeSeedColor(colorArgb: Long) {
        viewModelScope.launch { settingsDataStore.setThemeSeedColor(colorArgb) }
    }

    // ---- Player configuration settings ----

    fun setHwAcceleration(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setHwAcceleration(enabled) }
    }

    fun setBackgroundAudioPlayback(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setBackgroundAudio(enabled) }
    }

    fun setAutoResumePlayback(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAutoResume(enabled) }
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        viewModelScope.launch { settingsDataStore.setDefaultPlaybackSpeed(speed) }
        playerManager.setPlaybackSpeed(speed)
    }

    fun setFastSeekEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setFastSeekEnabled(enabled) }
        playerManager.setFastSeekEnabled(enabled)
    }

    fun setMatchDisplayFrameRate(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setMatchDisplayFrameRate(enabled) }
    }

    fun setMaxVideoResolution(cap: VideoResolutionCap) {
        viewModelScope.launch { settingsDataStore.setMaxVideoResolution(cap) }
        playerManager.setMaxVideoResolution(cap.width, cap.height)
    }

    fun setVideoThumbnailsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setVideoThumbnailsEnabled(enabled) }
    }

    fun setIncognitoMode(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setIncognitoMode(enabled) }
    }

    fun setSubtitleTextSize(size: SubtitleTextSize) {
        viewModelScope.launch { settingsDataStore.setSubtitleTextSize(size) }
    }

    fun setSubtitleColor(color: SubtitleColorPreset) {
        viewModelScope.launch { settingsDataStore.setSubtitleColor(color) }
    }

    fun setShowListHeaders(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setShowListHeaders(enabled) }
    }

    /**
     * Switches the app's language. Note: this changes the system-level locale (date/number
     * formatting and any actual string resources), but most of this app's UI text is
     * hardcoded English in Compose code rather than pulled from strings.xml, so it won't
     * retranslate that text on its own — see the AppLanguage doc comment.
     */
    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsDataStore.setAppLanguage(language) }
        applyLocaleIfNeeded(language)
    }

    private fun applyLocaleIfNeeded(language: AppLanguage) {
        val target = localeListFor(language)
        val current = AppCompatDelegate.getApplicationLocales()
        if (current.toLanguageTags() != target.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(target)
        }
    }

    private fun localeListFor(language: AppLanguage): LocaleListCompat =
        if (language.localeTag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.localeTag)
        }

    // ---- Export / Import full settings backup ----

    suspend fun exportSettingsBackup(): String {
        val settings = _uiState.value.appSettings
        val streams = repository.getAllStreamsOnce()
        val playlists = repository.getAllPlaylistsOnce().map { playlist ->
            BackupPlaylist(playlist.name, repository.getPlaylistItemsOnce(playlist.id))
        }
        return SettingsBackupManager.serialize(BackupBundle(settings, streams, playlists))
    }

    /** Additive restore: applies settings wholesale, and re-adds streams/playlists from the backup. */
    fun importSettingsBackup(json: String, onComplete: (success: Boolean) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val bundle = SettingsBackupManager.deserialize(json)
                settingsDataStore.applyAll(bundle.settings)
                bundle.customStreams.forEach { repository.restoreCustomStream(it) }
                bundle.playlists.forEach { repository.restorePlaylist(it.name, it.items) }
            }
            onComplete(result.isSuccess)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // playerManager is now owned by DkPlayerApplication (not this ViewModel), so it
        // must survive this ViewModel being cleared — background playback and Cast
        // sessions need to keep running independent of the Activity/ViewModel lifecycle.
        // Do NOT release it here.
    }
}
