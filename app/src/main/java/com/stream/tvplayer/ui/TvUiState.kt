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
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val selectedCategory: String? = null, // null = "All"
    val showFavoritesOnly: Boolean = false,
    val favoriteChannelIds: Set<Long> = emptySet()
) {
    val currentChannel: ChannelEntity?
        get() = channels.getOrNull(currentChannelIndex)

    /** Distinct category names for the filter chip row, e.g. "Movies", "News". */
    val categories: List<String>
        get() = channels.mapNotNull { it.groupName?.takeIf(String::isNotBlank) }
            .distinct()
            .sorted()

    /** Channels after search + category + favorites filters are applied, for the sidebar list. */
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