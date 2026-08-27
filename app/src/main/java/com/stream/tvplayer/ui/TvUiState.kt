package com.stream.tvplayer.ui

import com.stream.tvplayer.data.local.ChannelEntity
import com.stream.tvplayer.data.local.EpgEntity
import com.stream.tvplayer.data.local.HistoryEntity
import com.stream.tvplayer.data.local.StreamEntity

data class TvUiState(
    val channels: List<ChannelEntity> = emptyList(),
    val currentChannelIndex: Int = 0,
    val currentProgram: EpgEntity? = null,
    val nextProgram: EpgEntity? = null,
    val isSidebarOpen: Boolean = false,
    val isOverlayVisible: Boolean = true,
    val shuffleEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val showFavoritesOnly: Boolean = false,
    val favoriteChannelIds: Set<Long> = emptySet(),
    val history: List<HistoryEntity> = emptyList(),
    val streams: List<StreamEntity> = emptyList()
) {
    val currentChannel: ChannelEntity?
        get() = channels.getOrNull(currentChannelIndex)

    val categories: List<String>
        get() = channels.mapNotNull { it.groupName?.takeIf(String::isNotBlank) }
            .distinct()
            .sorted()

    val filteredChannels: List<ChannelEntity>
        get() = channels.filter { channel ->
            val matchesQuery = searchQuery.isBlank() ||
                channel.name.contains(searchQuery, ignoreCase = true) ||
                channel.channelNumber.toString().contains(searchQuery)
            val matchesCategory = selectedCategory == null || channel.groupName == selectedCategory
            val matchesFavorite = !showFavoritesOnly || favoriteChannelIds.contains(channel.id)
            matchesQuery && matchesCategory && matchesFavorite
        }
}