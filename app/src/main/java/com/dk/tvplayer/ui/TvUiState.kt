package com.dk.tvplayer.ui

import com.dk.tvplayer.data.local.HistoryEntity
import com.dk.tvplayer.data.local.LocalVideoItem
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
    val searchQuery: String = "",
    val isOverlayVisible: Boolean = true
)
