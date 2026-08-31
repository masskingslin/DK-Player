package com.dk.tvplayer.ui

import com.dk.tvplayer.data.local.AppSettings
import com.dk.tvplayer.data.local.HistoryEntity
import com.dk.tvplayer.data.local.LocalAudioItem
import com.dk.tvplayer.data.local.LocalVideoItem
import com.dk.tvplayer.data.local.PlaylistEntity
import com.dk.tvplayer.data.local.PlaylistItemEntity
import com.dk.tvplayer.data.local.SortOption
import com.dk.tvplayer.data.local.StreamEntity
import com.dk.tvplayer.data.local.TvChannelEntity
import com.dk.tvplayer.data.local.TvEpgProgramEntity

data class TvUiState(
    val channels: List<TvChannelEntity> = emptyList(),
    val filteredChannels: List<TvChannelEntity> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String = "All",
    val selectedChannel: TvChannelEntity? = null,
    val currentEpgPrograms: List<TvEpgProgramEntity> = emptyList(),
    val history: List<HistoryEntity> = emptyList(),
    val customStreams: List<StreamEntity> = emptyList(),
    val localVideos: List<LocalVideoItem> = emptyList(),
    val localAudio: List<LocalAudioItem> = emptyList(),
    val searchQuery: String = "",
    val isOverlayVisible: Boolean = true,
    val favoriteChannelIds: Set<String> = emptySet(),
    val showFavoritesOnly: Boolean = false,

    // Search/Filter enhancements
    val sortOption: SortOption = SortOption.NAME_ASC,

    // Playlist management
    val playlists: List<PlaylistEntity> = emptyList(),
    val selectedPlaylist: PlaylistEntity? = null,
    val selectedPlaylistItems: List<PlaylistItemEntity> = emptyList(),
    val selectedPlaylistItemIds: Set<Long> = emptySet(),

    // App settings (theme, player config, default speed) — persisted via DataStore.
    // Live player feature state (playback speed / error / cast / sleep timer) is
    // observed directly from TvPlayerViewModel.playerManager's own flows by the
    // composables that need it, the same way isPlaying already is.
    val appSettings: AppSettings = AppSettings()
)
