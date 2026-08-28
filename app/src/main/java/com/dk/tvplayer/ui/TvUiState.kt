package com.dk.tvplayer.ui

import com.dk.tvplayer.data.local.ChannelEntity
import com.dk.tvplayer.data.local.PlaylistEntity
import com.dk.tvplayer.player.SubtitleTrackInfo
import com.dk.tvplayer.ui.components.SortOption
import com.dk.tvplayer.ui.theme.ThemeMode

data class TvUiState(
    val channels: List<ChannelEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val searchQuery: String = "",
    val showFavoritesOnly: Boolean = false,
    val selectedSort: SortOption = SortOption.DEFAULT,
    val currentlyPlayingChannel: ChannelEntity? = null,
    val isPlaying: Boolean = false,
    val isInPipMode: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val remainingSleepSeconds: Long? = null,
    val playerError: String? = null,
    val subtitleTracks: List<SubtitleTrackInfo> = emptyList(),
    val selectedChannelIds: Set<Long> = emptySet(),
    val themeMode: ThemeMode = ThemeMode.DARK,
    val isCasting: Boolean = false
) {
    val isMultiSelectActive: Boolean
        get() = selectedChannelIds.isNotEmpty()
}
