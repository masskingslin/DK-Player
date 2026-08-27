package com.stream.tvplayer.ui

import com.stream.tvplayer.data.local.ChannelEntity
import com.stream.tvplayer.data.local.EpgEntity

data class TvUiState(
    val channels: List<ChannelEntity> = emptyList(),
    val currentChannelIndex: Int = 0,
    val currentProgram: EpgEntity? = null,
    val nextProgram: EpgEntity? = null,
    val isSidebarOpen: Boolean = false,
    val isOverlayVisible: Boolean = true,
    val shuffleEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val currentChannel: ChannelEntity?
        get() = channels.getOrNull(currentChannelIndex)
}
