package com.dk.tvplayer.ui

import com.dk.tvplayer.data.local.ChannelEntity
import com.dk.tvplayer.data.local.EpgProgramEntity
import com.dk.tvplayer.data.local.HistoryEntity
import com.dk.tvplayer.data.local.LocalAudioItem
import com.dk.tvplayer.data.local.LocalVideoItem
import com.dk.tvplayer.data.local.PlaylistEntity
import com.dk.tvplayer.data.local.StreamEntity
import com.dk.tvplayer.player.SubtitleTrackInfo
import com.dk.tvplayer.ui.components.SortOption
import com.dk.tvplayer.ui.theme.ThemeMode

data class TvUiState(
    // IPTV channel browsing (search/category filtered, backed by Room)
    val filteredChannels: List<ChannelEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val searchQuery: String = "",
    val showFavoritesOnly: Boolean = false,
    val selectedSort: SortOption = SortOption.DEFAULT,
    val selectedChannel: ChannelEntity? = null,

    // Playback
    val currentlyPlayingChannel: ChannelEntity? = null,
    val isPlaying: Boolean = false,
    val isInPipMode: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val remainingSleepSeconds: Long? = null,
    val playerError: String? = null,
    val subtitleTracks: List<SubtitleTrackInfo> = emptyList(),
    val isCasting: Boolean = false,

    // Multi-select (batch actions on the channel list)
    val selectedChannelIds: Set<Long> = emptySet(),

    // Appearance
    val themeMode: ThemeMode = ThemeMode.DARK,

    // Local media libraries
    val localVideos: List<LocalVideoItem> = emptyList(),
    val localAudio: List<LocalAudioItem> = emptyList(),

    // EPG for the currently selected channel
    val currentEpgPrograms: List<EpgProgramEntity> = emptyList(),

    // Home hub: recently played + user-added direct streams
    val history: List<HistoryEntity> = emptyList(),
    val customStreams: List<StreamEntity> = emptyList()
) {
    val isMultiSelectActive: Boolean
        get() = selectedChannelIds.isNotEmpty()
}
