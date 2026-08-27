package com.stream.tvplayer.ui

import com.stream.tvplayer.data.local.HistoryEntity
import com.stream.tvplayer.data.local.LocalVideoItem
import com.stream.tvplayer.data.local.StreamEntity
import com.stream.tvplayer.data.local.TvChannelEntity
import com.stream.tvplayer.data.local.TvEpgProgramEntity

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
    val searchQuery: String = "",
    val isOverlayVisible: Boolean = true
)
