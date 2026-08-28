package com.dk.tvplayer.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppBackupData(
    val version: Int = 1,
    val exportDate: Long = System.currentTimeMillis(),
    val settings: Map<String, String> = emptyMap(),
    val customStreams: List<StreamBackupDto> = emptyList(),
    val playlists: List<PlaylistBackupDto> = emptyList()
)

@Serializable
data class StreamBackupDto(
    val name: String,
    val url: String,
    val groupTitle: String,
    val logoUrl: String? = null,
    val isFavorite: Boolean = false
)

@Serializable
data class PlaylistBackupDto(
    val title: String,
    val urlOrPath: String,
    val isLocalFile: Boolean
)
