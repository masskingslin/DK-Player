package com.dk.tvplayer.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dk.tvplayer.data.backup.BackupBundle
import com.dk.tvplayer.data.backup.BackupPlaylist
import com.dk.tvplayer.data.backup.SettingsBackupManager
import com.dk.tvplayer.data.local.AppLanguage
import com.dk.tvplayer.data.local.BookmarkEntity
import com.dk.tvplayer.data.local.AppThemeMode
import com.dk.tvplayer.data.local.LocalAudioItem
import com.dk.tvplayer.data.local.LocalVideoItem
import com.dk.tvplayer.data.local.PlaylistEntity
import com.dk.tvplayer.data.local.PlaylistItemEntity
import com.dk.tvplayer.data.local.QueueFormat
import com.dk.tvplayer.data.local.QueueInfoPosition
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream

class TvPlayerViewModel(
    private val repository: TvRepository,
    val playerManager: TvExoPlayerManager,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(TvUiState())
    val uiState: StateFlow<TvUiState> = _uiState.asStateFlow()

    // Raw filter inputs, separate from _uiState, so the search box can update instantly
    // on every keystroke (bound to _uiState.searchQuery) while the actual filtering —
    // expensive once a playlist has tens of thousands of channels — runs debounced and
    // off the main thread. Without this split, either the text field lags behind what
    // you type, or every keystroke re-filters/re-sorts the full channel list inline.
    private val _channelsInput = MutableStateFlow<List<TvChannelEntity>>(emptyList())
    private val _categoryInput = MutableStateFlow("All")
    private val _searchQueryInput = MutableStateFlow("")
    private val _showFavoritesOnlyInput = MutableStateFlow(false)
    private val _sortOptionInput = MutableStateFlow(SortOption.NAME_ASC)

    init {
        observeData()
        observeChannelFiltering()
    }

    private fun observeData() {
        viewModelScope.launch {
            repository.getAllChannels().collect { list ->
                _channelsInput.value = list
                // isFavorite now lives on TvChannelEntity itself (persisted in the DB)
                // rather than an in-memory-only set, so favoriteChannelIds just mirrors
                // whatever the DB says — which means it also survives app restarts, and
                // picks up favorites toggled from a Playlist item (see
                // TvRepository.setPlaylistItemFavorite) the same way it picks up ones
                // toggled from the IPTV Channels tab.
                _uiState.update {
                    it.copy(
                        channels = list,
                        favoriteChannelIds = list.filter { channel -> channel.isFavorite }
                            .map { channel -> channel.channelId }
                            .toSet()
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
            repository.getVideoGroups().collect { groups ->
                _uiState.update { it.copy(videoGroups = groups) }
            }
        }

        viewModelScope.launch {
            repository.getLocalVideoMeta().collect { meta ->
                _uiState.update { it.copy(localVideoMeta = meta) }
            }
        }

        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                _uiState.update { it.copy(sortOption = settings.sortOption, appSettings = settings) }
                _sortOptionInput.value = settings.sortOption
                // Keep the live player in sync with settings that can change at runtime
                // (hardware acceleration is the one exception — see TvExoPlayerManager).
                playerManager.setPlaybackSpeed(settings.defaultPlaybackSpeed)
                playerManager.setFastSeekEnabled(settings.fastSeekEnabled)
                playerManager.setMaxVideoResolution(settings.maxVideoResolution.width, settings.maxVideoResolution.height)
                playerManager.setBackgroundPlaybackEnabled(effectiveBackgroundPlayback(settings.backgroundAudioPlayback))
                playerManager.setCastAudioOnly(settings.castAudioOnly)
                playerManager.setWirelessCastingEnabled(settings.wirelessCastingEnabled)
                playerManager.setDefaultPlaybackSpeedSetting(settings.defaultPlaybackSpeed)
                playerManager.setAndroidAutoTitleTextScale(settings.androidAutoTitleTextScale)
                playerManager.setAndroidAutoSubtitleTextScale(settings.androidAutoSubtitleTextScale)
                playerManager.setAndroidAutoQueueInfoPosition(settings.androidAutoQueueInfoPosition)
                playerManager.setAndroidAutoQueueFormat(settings.androidAutoQueueFormat)
                playerManager.setAndroidAutoUseGlobalPlaybackSpeed(settings.androidAutoUseGlobalPlaybackSpeed)
                playerManager.setAndroidAutoPlaybackSpeedControlEnabled(settings.androidAutoPlaybackSpeedControlEnabled)
                playerManager.setAndroidAutoSeekButtonsEnabled(
                    settings.androidAutoSeekButtonsEnabled || com.dk.tvplayer.util.UiPrefs.seekButtonsInNotification.value
                )
                applyLocaleIfNeeded(settings.appLanguage)
            }
        }
    }

    /**
     * Reactive channel filter/sort pipeline. Debounces the search query (so typing
     * doesn't re-filter on every keystroke) and runs the actual filter+sort work on
     * Dispatchers.Default (background) rather than inline on whatever thread called a
     * setter — both of which matter once a loaded playlist has tens of thousands of
     * channels rather than a few hundred.
     */
    private fun observeChannelFiltering() {
        viewModelScope.launch {
            combine(
                _channelsInput,
                _categoryInput,
                _searchQueryInput.debounce(250),
                _showFavoritesOnlyInput
            ) { channels, category, query, showFavoritesOnly ->
                val favoriteIds = channels.filter { it.isFavorite }.map { it.channelId }.toSet()
                ChannelFilterInputs(channels, category, query, favoriteIds, showFavoritesOnly, _sortOptionInput.value)
            }.combine(_sortOptionInput) { inputs, sortOption ->
                inputs.copy(sortOption = sortOption)
            }.distinctUntilChanged().collect { inputs ->
                val result = withContext(Dispatchers.Default) {
                    filterAndSortChannels(
                        inputs.channels, inputs.category, inputs.query,
                        inputs.favoriteIds, inputs.showFavoritesOnly, inputs.sortOption
                    )
                }
                _uiState.update { it.copy(filteredChannels = result) }
            }
        }
    }

    private data class ChannelFilterInputs(
        val channels: List<TvChannelEntity>,
        val category: String,
        val query: String,
        val favoriteIds: Set<String>,
        val showFavoritesOnly: Boolean,
        val sortOption: SortOption
    )

    // ---- Search / filter / sort ----

    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
        _categoryInput.value = category
    }

    fun updateSearchQuery(query: String) {
        // Updates the visible text field immediately; the expensive filtering that
        // results from it is debounced separately in observeChannelFiltering().
        _uiState.update { it.copy(searchQuery = query) }
        _searchQueryInput.value = query
    }

    fun setSortOption(option: SortOption) {
        viewModelScope.launch { settingsDataStore.setSortOption(option) }
        _uiState.update { it.copy(sortOption = option) }
        _sortOptionInput.value = option
    }

    fun toggleFavorite(channelId: String) {
        // Persist to the DB (channels.isFavorite is the single source of truth now —
        // see observeData()); the channels Flow re-emitting is what actually updates
        // favoriteChannelIds and filteredChannels, so no local uiState mutation here.
        viewModelScope.launch {
            val current = _uiState.value.channels.firstOrNull { it.channelId == channelId }?.isFavorite ?: false
            repository.setChannelFavorite(channelId, !current)
        }
    }

    /** Removes a single channel from the IPTV Channels list (context-menu "Delete"). */
    fun deleteChannel(channel: TvChannelEntity) {
        viewModelScope.launch { repository.deleteChannel(channel) }
    }

    /** Favorites/unfavorites a Playlists-tab item — also mirrors onto the matching
     *  channel (by stream URL) so it shows up in Home's Favorite Channels row and the
     *  IPTV Channels tab too. See TvRepository.setPlaylistItemFavorite. */
    fun toggleFavoritePlaylistItem(item: PlaylistItemEntity) {
        viewModelScope.launch { repository.setPlaylistItemFavorite(item, !item.isFavorite) }
    }

    fun toggleShowFavoritesOnly() {
        _uiState.update { it.copy(showFavoritesOnly = !it.showFavoritesOnly) }
        _showFavoritesOnlyInput.value = _uiState.value.showFavoritesOnly
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
        playerManager.play(
            channel.streamUrl,
            title = channel.name,
            userAgent = channel.userAgent,
            referrer = channel.referrer,
            subtitle = channel.groupTitle.takeIf { it.isNotBlank() }
        )
        // Reflects this channel's place within the currently browsed/filtered channel
        // list in Android Auto's "Queue information" — e.g. "12/48" — since IPTV
        // channel surfing is this app's closest thing to a queue.
        val channelList = _uiState.value.filteredChannels
        val index = channelList.indexOfFirst { it.channelId == channel.channelId }
        if (index >= 0) {
            playerManager.updateAndroidAutoQueueContext(index + 1, channelList.size)
        } else {
            playerManager.updateAndroidAutoQueueContext(null, null)
        }
        observeEpg(channel.channelId)
    }

    // ---- One-shot play options & play queue (local videos) ----

    /** Set by "Play from start" so the very next playMedia() for this URL ignores the
     *  saved resume position. */
    private var skipResumeUrl: String? = null

    /** Items that play (in order) after the current one ends. */
    private val upcomingQueue = ArrayDeque<Pair<String, String>>() // url to title
    private val _queueSize = MutableStateFlow(0)
    val queueSize: StateFlow<Int> = _queueSize.asStateFlow()

    private val _playbackEnded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits whenever the active item finishes; PhoneAppRoot decides whether to advance. */
    val playbackEnded: SharedFlow<Unit> = _playbackEnded.asSharedFlow()

    /** (filePath, title) requested from outside the app UI, e.g. a launcher shortcut. */
    val externalPlayRequest = MutableStateFlow<Pair<String, String>?>(null)

    private var rawLocalVideos: List<LocalVideoItem> = emptyList()
    private var rawLocalAudio: List<LocalAudioItem> = emptyList()
    private var firstVideoLoad = true
    private var firstAudioLoad = true

    /** Set once the "resume last played" tip has been offered in this app session. */
    var resumeTipShown = false

    private val _appForegrounded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits each time the activity becomes visible again (used to restore video from background). */
    val appForegrounded: SharedFlow<Unit> = _appForegrounded.asSharedFlow()

    /** Combines the old "Background Audio Playback" switch with the Background/PiP mode choice. */
    fun effectiveBackgroundPlayback(legacySetting: Boolean): Boolean =
        when (com.dk.tvplayer.util.PlaybackPrefs.backgroundMode.value) {
            com.dk.tvplayer.util.BackgroundMode.STOP -> false
            com.dk.tvplayer.util.BackgroundMode.BACKGROUND -> true
            com.dk.tvplayer.util.BackgroundMode.PIP -> legacySetting
        }

    fun refreshBackgroundPlayback() {
        playerManager.setBackgroundPlaybackEnabled(
            effectiveBackgroundPlayback(_uiState.value.appSettings.backgroundAudioPlayback)
        )
    }

    fun notifyAppForegrounded() {
        _appForegrounded.tryEmit(Unit)
    }

    init {
        playerManager.onPlaybackEnded = {
            _playbackEnded.tryEmit(Unit)
            // "Show seen video marker": a local video played to the end counts as seen.
            val path = playerManager.currentMediaUrl
            val state = _uiState.value
            if (com.dk.tvplayer.util.UiPrefs.showSeenMarker.value &&
                !state.appSettings.incognitoMode &&
                path != null && state.localVideos.any { it.filePath == path }
            ) {
                viewModelScope.launch { repository.setVideoPlayed(path, true) }
            }
        }

        // Non-persistent incognito: start every app launch with it switched off.
        viewModelScope.launch {
            if (!com.dk.tvplayer.util.UiPrefs.persistentIncognito.value &&
                settingsDataStore.settingsFlow.first().incognitoMode
            ) {
                settingsDataStore.setIncognitoMode(false)
            }
        }

        // Media library folders: re-filter the lists whenever the selection changes.
        viewModelScope.launch {
            com.dk.tvplayer.util.PlaybackPrefs.excludedFolders.flow.collect { publishLocalMedia() }
        }

        // Preferred audio language (Audio settings) → track selection.
        viewModelScope.launch {
            com.dk.tvplayer.util.PlaybackPrefs.preferredAudioLanguage.flow.collect {
                playerManager.setPreferredAudioLanguage(it)
            }
        }

        // Detect headset / replay gain settings → live player.
        viewModelScope.launch {
            com.dk.tvplayer.util.PlaybackPrefs.detectHeadset.flow.collect { playerManager.setDetectHeadset(it) }
        }

        restoreSavedQueue()

        // Preferred subtitle language (Subtitles settings) → track selection.
        viewModelScope.launch {
            com.dk.tvplayer.util.SubtitlePrefs.language.collect { playerManager.setPreferredSubtitleLanguage(it) }
        }
    }

    fun requestPlayFromStart(url: String) {
        skipResumeUrl = url
    }

    /** Replaces the whole upcoming queue (used by "Play all"). */
    fun replaceQueue(videos: List<LocalVideoItem>) {
        upcomingQueue.clear()
        videos.forEach { upcomingQueue.addLast(it.filePath to it.name) }
        _queueSize.value = upcomingQueue.size
        persistQueue()
    }

    fun enqueue(video: LocalVideoItem) {
        upcomingQueue.addLast(video.filePath to video.name)
        _queueSize.value = upcomingQueue.size
        persistQueue()
    }

    fun enqueueAll(videos: List<LocalVideoItem>) {
        videos.forEach { upcomingQueue.addLast(it.filePath to it.name) }
        _queueSize.value = upcomingQueue.size
        persistQueue()
    }

    /** "Insert next": plays right after whatever is playing now. */
    fun enqueueNext(video: LocalVideoItem) {
        upcomingQueue.addFirst(video.filePath to video.name)
        _queueSize.value = upcomingQueue.size
        persistQueue()
    }

    // ---- Play-queue history ("Video/Audio play queue history" in Settings) ----

    private fun persistQueue() {
        val keepVideo = com.dk.tvplayer.util.PlaybackPrefs.savePlaybackHistory.value &&
            com.dk.tvplayer.util.PlaybackPrefs.videoQueueHistory.value
        val keepAudio = com.dk.tvplayer.util.PlaybackPrefs.savePlaybackHistory.value &&
            com.dk.tvplayer.util.PlaybackPrefs.audioQueueHistory.value
        val keep = upcomingQueue.filter { (path, _) ->
            if (path.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS) keepAudio else keepVideo
        }
        com.dk.tvplayer.util.MediaListCache.saveQueue(keep)
    }

    private fun restoreSavedQueue() {
        val saved = com.dk.tvplayer.util.MediaListCache.loadQueue()
        saved.forEach { upcomingQueue.addLast(it) }
        _queueSize.value = upcomingQueue.size
    }

    fun popNextQueued(): Pair<String, String>? {
        val next = upcomingQueue.removeFirstOrNull()
        _queueSize.value = upcomingQueue.size
        persistQueue()
        return next
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
            val skipResume = skipResumeUrl == url
            if (skipResume) skipResumeUrl = null
            val startPositionMs = if (settings.autoResumePlayback && !settings.incognitoMode && !skipResume) {
                val entry = repository.getHistoryEntryOnce(url)
                val savedPosition = entry?.lastPositionMs ?: 0L
                val savedDuration = entry?.durationMs ?: 0L
                val nearEnd = savedDuration > 0 && savedPosition >= savedDuration - 5_000
                // "Resume played audio": audio files can be set to always, only long ones, or never resume.
                val isAudio = url.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
                val audioAllows = !isAudio || when (com.dk.tvplayer.util.PlaybackPrefs.resumeAudio.value) {
                    com.dk.tvplayer.util.ResumeAudio.ALWAYS -> true
                    com.dk.tvplayer.util.ResumeAudio.LONG -> savedDuration >= 10 * 60_000L
                    com.dk.tvplayer.util.ResumeAudio.NEVER -> false
                }
                if (audioAllows && savedPosition > 5_000 && !nearEnd) savedPosition else 0L
            } else {
                0L
            }
            // PhonePlayerScreen calls this right after navigation regardless of media
            // type, so for an IPTV channel it re-issues the play() call selectChannel()
            // already made — matching it back up by URL here (rather than requiring
            // every call site to thread headers through) keeps any per-channel
            // User-Agent/Referer override from getting silently dropped on that second call.
            val matchingChannel = _uiState.value.channels.firstOrNull { it.streamUrl == url }
            playerManager.play(
                url,
                startPositionMs = startPositionMs,
                title = title,
                userAgent = matchingChannel?.userAgent,
                referrer = matchingChannel?.referrer,
                subtitle = matchingChannel?.groupTitle?.takeIf { it.isNotBlank() }
            )
            if (matchingChannel != null) {
                val channelList = _uiState.value.filteredChannels
                val index = channelList.indexOfFirst { it.channelId == matchingChannel.channelId }
                if (index >= 0) {
                    playerManager.updateAndroidAutoQueueContext(index + 1, channelList.size)
                }
            } else {
                // A plain video/custom stream has no known "queue" — clear any leftover
                // position from whatever was playing before.
                playerManager.updateAndroidAutoQueueContext(null, null)
            }
        }
    }

    /** No-op in Incognito Mode — nothing gets written to playback history. */
    fun savePlaybackProgress(url: String, title: String, position: Long, duration: Long) {
        if (_uiState.value.appSettings.incognitoMode || !com.dk.tvplayer.util.PlaybackPrefs.savePlaybackHistory.value) return
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

    private fun isExcluded(path: String): Boolean {
        val excluded = com.dk.tvplayer.util.PlaybackPrefs.excludedFolders.value
        if (excluded.isEmpty()) return false
        val parent = java.io.File(path).parent ?: return false
        return excluded.any { parent == it || parent.startsWith("$it/") }
    }

    private fun publishLocalMedia() {
        _mediaFolders.value = (rawLocalVideos.map { it.filePath } + rawLocalAudio.map { it.filePath })
            .mapNotNull { java.io.File(it).parent }
            .groupingBy { it }.eachCount()
            .toList().sortedBy { it.first.lowercase() }
        _uiState.update {
            it.copy(
                localVideos = rawLocalVideos.filterNot { v -> isExcluded(v.filePath) },
                localAudio = rawLocalAudio.filterNot { a -> isExcluded(a.filePath) }
            )
        }
    }

    private fun publishLocalMedia() {
        _mediaFolders.value = (rawLocalVideos.map { it.filePath } + rawLocalAudio.map { it.filePath })
            .mapNotNull { java.io.File(it).parent }
            .groupingBy { it }.eachCount()
            .toList().sortedBy { it.first.lowercase() }
        _uiState.update {
            it.copy(
                localVideos = rawLocalVideos.filterNot { v -> isExcluded(v.filePath) },
                localAudio = rawLocalAudio.filterNot { a -> isExcluded(a.filePath) }
            )
        }
    }

    private val _mediaFolders = MutableStateFlow<List<Pair<String, Int>>>(emptyList())
    /** Folders that contain videos or audio, with item counts (for "Media library folders"). */
    val mediaFolders: StateFlow<List<Pair<String, Int>>> = _mediaFolders.asStateFlow()

    /**
     * Scans the device for videos. With "Auto rescan" off, the very first load after app start
     * reuses the list saved by the last scan instead; [manual] always rescans.
     */
    fun refreshLocalVideos(manual: Boolean = false) {
        viewModelScope.launch {
            val useCache = firstVideoLoad && !manual && !com.dk.tvplayer.util.PlaybackPrefs.autoRescan.value
            firstVideoLoad = false
            val cached = if (useCache) com.dk.tvplayer.util.MediaListCache.loadVideos() else null
            rawLocalVideos = cached ?: repository.scanLocalVideos().also {
                com.dk.tvplayer.util.MediaListCache.saveVideos(it)
            }
            publishLocalMedia()
        }
    }

    // ---- Local video groups & played state ----

    fun clearPlaybackHistory() {
        viewModelScope.launch { repository.clearPlaybackHistory() }
    }

    fun clearLocalVideoData() {
        viewModelScope.launch { repository.clearLocalVideoData() }
    }

    fun setVideoPlayed(video: LocalVideoItem, isPlayed: Boolean) {
        viewModelScope.launch { repository.setVideoPlayed(video.filePath, isPlayed) }
    }

    fun setVideosPlayed(videos: List<LocalVideoItem>, isPlayed: Boolean) {
        viewModelScope.launch { repository.setVideosPlayed(videos.map { it.filePath }, isPlayed) }
    }

    /** Confirms an auto-suggested group (or creates a fresh one for a single video via
     *  "Add to Video Group") as a real, renameable/ungroupable [VideoGroupEntity]. */
    fun createVideoGroup(name: String, videos: List<LocalVideoItem>) {
        viewModelScope.launch { repository.createVideoGroup(name, videos.map { it.filePath }) }
    }

    fun addVideosToGroup(groupId: Long, videos: List<LocalVideoItem>) {
        viewModelScope.launch { repository.addFilesToGroup(groupId, videos.map { it.filePath }) }
    }

    fun renameVideoGroup(groupId: Long, name: String) {
        viewModelScope.launch { repository.renameVideoGroup(groupId, name) }
    }

    fun ungroupVideos(groupId: Long) {
        viewModelScope.launch { repository.ungroupVideos(groupId) }
    }

    fun removeVideoFromGroup(video: LocalVideoItem) {
        viewModelScope.launch { repository.removeFileFromGroup(video.filePath) }
    }

    fun refreshLocalAudio(manual: Boolean = false) {
        viewModelScope.launch {
            val useCache = firstAudioLoad && !manual && !com.dk.tvplayer.util.PlaybackPrefs.autoRescan.value
            firstAudioLoad = false
            val cached = if (useCache) com.dk.tvplayer.util.MediaListCache.loadAudio() else null
            rawLocalAudio = cached ?: repository.scanLocalAudio().also {
                com.dk.tvplayer.util.MediaListCache.saveAudio(it)
            }
            publishLocalMedia()
        }
    }

    /** Rescans both video and audio right now (Settings → Media library). */
    fun rescanMediaLibrary() {
        refreshLocalVideos(manual = true)
        refreshLocalAudio(manual = true)
    }

    fun importM3u(inputStream: InputStream) {
        viewModelScope.launch { repository.loadM3u(inputStream) }
    }

    /**
     * Shared network fetch used by both the IPTV Channels import and the Playlists
     * "Import as Playlist" flow — a 15k-40k channel M3U can be several MB of text, so
     * this uses generous timeouts (see importM3uFromUrl) rather than OkHttp's 10s
     * defaults, which are tuned for small API responses.
     */
    private suspend fun fetchM3uStream(url: String, onStream: suspend (InputStream) -> Unit) {
        withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Server returned HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Empty response from server")
                body.byteStream().use { stream -> onStream(stream) }
            }
        }
    }

    fun importM3uFromUrl(url: String, onComplete: (success: Boolean, errorMessage: String?) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { fetchM3uStream(url) { stream -> repository.loadM3u(stream) } }
            onComplete(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    /**
     * Same idea as [importM3uFromUrl] but for the Playlists feature: fetches an M3U
     * channel-list URL and imports its entries as items into the currently selected
     * playlist, instead of trying to hand the raw list URL to the player as if it were
     * one playable stream (which always fails with a manifest-parsing error, since a
     * channel list isn't a valid single-stream HLS/DASH manifest).
     */
    fun importM3uFromUrlIntoSelectedPlaylist(url: String, onComplete: (success: Boolean, errorMessage: String?) -> Unit) {
        if (com.dk.tvplayer.util.ParentalControl.interceptForSafeMode("Safe mode is on — enter your PIN to change playlists") { importM3uFromUrlIntoSelectedPlaylist(url, onComplete) }) return
        val playlistId = _uiState.value.selectedPlaylist?.id
        if (playlistId == null) {
            onComplete(false, "No playlist selected")
            return
        }
        viewModelScope.launch {
            val result = runCatching {
                fetchM3uStream(url) { stream -> repository.importM3uIntoPlaylist(playlistId, stream) }
            }
            onComplete(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    // ---- Playlist management ----

    fun createPlaylist(name: String) {
        if (com.dk.tvplayer.util.ParentalControl.interceptForSafeMode("Safe mode is on — enter your PIN to change playlists") { createPlaylist(name) }) return
        viewModelScope.launch { repository.createPlaylist(name.ifBlank { "New Playlist" }) }
    }

    /** Used by the "Add to Playlist" context-menu action's "New Playlist" option — needs
     *  the new playlist's id back so the item can go straight into it. */
    fun createPlaylistAndAddItem(name: String, title: String, url: String) {
        if (com.dk.tvplayer.util.ParentalControl.interceptForSafeMode("Safe mode is on — enter your PIN to change playlists") { createPlaylistAndAddItem(name, title, url) }) return
        viewModelScope.launch {
            val playlistId = repository.createPlaylist(name.ifBlank { "New Playlist" })
            repository.addItemToPlaylist(playlistId, title, url)
        }
    }

    /** Same idea, for adding a whole video group to a brand-new playlist in one go
     *  (see VideoLibraryScreen's group "Add to Playlist" action) — avoids only the
     *  first member landing in the new playlist and the rest silently being dropped. */
    fun createPlaylistAndAddItems(name: String, items: List<Pair<String, String>>) {
        if (com.dk.tvplayer.util.ParentalControl.interceptForSafeMode("Safe mode is on — enter your PIN to change playlists") { createPlaylistAndAddItems(name, items) }) return
        viewModelScope.launch {
            val playlistId = repository.createPlaylist(name.ifBlank { "New Playlist" })
            items.forEach { (title, url) -> repository.addItemToPlaylist(playlistId, title, url) }
        }
    }

    /** Used by the player's "Save Playlist" menu item to add the currently playing item
     *  to a playlist the person already has, as opposed to createPlaylistAndAddItem's
     *  "make a brand new one" path. */
    fun addToExistingPlaylist(playlistId: Long, title: String, url: String) {
        if (com.dk.tvplayer.util.ParentalControl.interceptForSafeMode("Safe mode is on — enter your PIN to change playlists") { addToExistingPlaylist(playlistId, title, url) }) return
        viewModelScope.launch { repository.addItemToPlaylist(playlistId, title, url) }
    }

    // ---- Bookmarks (player's "..." menu) ----

    fun getBookmarksForMedia(mediaUrl: String) = repository.getBookmarksForMedia(mediaUrl)

    fun addBookmark(mediaUrl: String, mediaTitle: String, positionMs: Long, label: String) {
        viewModelScope.launch { repository.addBookmark(mediaUrl, mediaTitle, positionMs, label) }
    }

    fun deleteBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch { repository.deleteBookmark(bookmark) }
    }

    // ---- Player gesture controls ("Control settings" menu item) ----

    fun setGestureSeekEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setGestureSeekEnabled(enabled) }
    }

    fun setGestureBrightnessVolumeEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setGestureBrightnessVolumeEnabled(enabled) }
    }

    fun setDoubleTapSeekEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setDoubleTapSeekEnabled(enabled) }
    }

    fun renamePlaylist(playlist: PlaylistEntity, newName: String) {
        if (com.dk.tvplayer.util.ParentalControl.interceptForSafeMode("Safe mode is on — enter your PIN to change playlists") { renamePlaylist(playlist, newName) }) return
        viewModelScope.launch { repository.renamePlaylist(playlist, newName) }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        if (com.dk.tvplayer.util.ParentalControl.interceptForSafeMode("Safe mode is on — enter your PIN to change playlists") { deletePlaylist(playlist) }) return
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
        if (com.dk.tvplayer.util.ParentalControl.interceptForSafeMode("Safe mode is on — enter your PIN to change playlists") { addChannelToPlaylist(playlistId, channel) }) return
        viewModelScope.launch {
            repository.addItemToPlaylist(playlistId, channel.name, channel.streamUrl, channel.groupTitle, channel.logoUrl)
        }
    }

    fun addStreamToPlaylist(playlistId: Long, stream: StreamEntity) {
        if (com.dk.tvplayer.util.ParentalControl.interceptForSafeMode("Safe mode is on — enter your PIN to change playlists") { addStreamToPlaylist(playlistId, stream) }) return
        viewModelScope.launch {
            repository.addItemToPlaylist(playlistId, stream.name, stream.streamUrl, stream.groupTitle, stream.logoUrl)
        }
    }

    fun addCustomItemToPlaylist(playlistId: Long, title: String, url: String) {
        if (com.dk.tvplayer.util.ParentalControl.interceptForSafeMode("Safe mode is on — enter your PIN to change playlists") { addCustomItemToPlaylist(playlistId, title, url) }) return
        viewModelScope.launch { repository.addItemToPlaylist(playlistId, title, url) }
    }

    fun removeItemFromPlaylist(item: PlaylistItemEntity) {
        if (com.dk.tvplayer.util.ParentalControl.interceptForSafeMode("Safe mode is on — enter your PIN to change playlists") { removeItemFromPlaylist(item) }) return
        viewModelScope.launch { repository.removeItemFromPlaylist(item) }
    }

    fun importM3uIntoSelectedPlaylist(inputStream: InputStream) {
        if (com.dk.tvplayer.util.ParentalControl.interceptForSafeMode("Safe mode is on — enter your PIN to change playlists") { importM3uIntoSelectedPlaylist(inputStream) }) return
        val playlistId = _uiState.value.selectedPlaylist?.id ?: return
        viewModelScope.launch { repository.importM3uIntoPlaylist(playlistId, inputStream) }
    }

    /** Moves an item one slot up/down within the currently viewed playlist and persists the new order. */
    fun movePlaylistItem(item: PlaylistItemEntity, moveUp: Boolean) {
        if (com.dk.tvplayer.util.ParentalControl.interceptForSafeMode("Safe mode is on — enter your PIN to change playlists") { movePlaylistItem(item, moveUp) }) return
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
        if (com.dk.tvplayer.util.ParentalControl.interceptForSafeMode("Safe mode is on — enter your PIN to change playlists") { deleteSelectedPlaylistItems() }) return
        val selectedIds = _uiState.value.selectedPlaylistItemIds
        val items = _uiState.value.selectedPlaylistItems.filter { selectedIds.contains(it.id) }
        if (items.isEmpty()) return
        viewModelScope.launch {
            repository.removeItemsFromPlaylist(items)
            _uiState.update { it.copy(selectedPlaylistItemIds = emptySet()) }
        }
    }

    fun moveSelectedPlaylistItemsTo(targetPlaylistId: Long) {
        if (com.dk.tvplayer.util.ParentalControl.interceptForSafeMode("Safe mode is on — enter your PIN to change playlists") { moveSelectedPlaylistItemsTo(targetPlaylistId) }) return
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

    fun setWirelessCastingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setWirelessCastingEnabled(enabled) }
        playerManager.setWirelessCastingEnabled(enabled)
    }

    fun setCastAudioOnly(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setCastAudioOnly(enabled) }
    }

    // ---- Android Auto ----

    fun setAndroidAutoTitleTextScale(scale: Float) {
        viewModelScope.launch { settingsDataStore.setAndroidAutoTitleTextScale(scale) }
        playerManager.setAndroidAutoTitleTextScale(scale)
    }

    fun setAndroidAutoSubtitleTextScale(scale: Float) {
        viewModelScope.launch { settingsDataStore.setAndroidAutoSubtitleTextScale(scale) }
        playerManager.setAndroidAutoSubtitleTextScale(scale)
    }

    fun setAndroidAutoQueueInfoPosition(position: QueueInfoPosition) {
        viewModelScope.launch { settingsDataStore.setAndroidAutoQueueInfoPosition(position) }
        playerManager.setAndroidAutoQueueInfoPosition(position)
    }

    fun setAndroidAutoQueueFormat(format: QueueFormat) {
        viewModelScope.launch { settingsDataStore.setAndroidAutoQueueFormat(format) }
        playerManager.setAndroidAutoQueueFormat(format)
    }

    fun setAndroidAutoUseGlobalPlaybackSpeed(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAndroidAutoUseGlobalPlaybackSpeed(enabled) }
        playerManager.setAndroidAutoUseGlobalPlaybackSpeed(enabled)
    }

    fun setAndroidAutoPlaybackSpeedControlEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAndroidAutoPlaybackSpeedControlEnabled(enabled) }
        playerManager.setAndroidAutoPlaybackSpeedControlEnabled(enabled)
    }

    fun setAndroidAutoSeekButtonsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAndroidAutoSeekButtonsEnabled(enabled) }
        playerManager.setAndroidAutoSeekButtonsEnabled(enabled)
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

private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "flac", "ogg", "opus", "aac", "wav", "wma")
