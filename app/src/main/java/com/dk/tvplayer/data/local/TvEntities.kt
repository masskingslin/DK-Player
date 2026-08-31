package com.dk.tvplayer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class TvChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val name: String,
    val logoUrl: String? = null,
    val groupTitle: String = "General",
    val streamUrl: String,
    val isFavorite: Boolean = false
)

@Entity(tableName = "epg_programs")
data class TvEpgProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val title: String,
    val description: String? = null,
    val startTime: Long,
    val endTime: Long
)

@Entity(tableName = "playback_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaUrl: String,
    val title: String,
    val lastPositionMs: Long,
    val durationMs: Long,
    val lastWatchedTimestamp: Long = System.currentTimeMillis(),
    val isLiveStream: Boolean = false
)

@Entity(tableName = "custom_streams")
data class StreamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val streamUrl: String,
    val groupTitle: String = "Custom Streams",
    val logoUrl: String? = null,
    val addedDate: Long = System.currentTimeMillis()
)

/**
 * A user-managed playlist (custom M3U/XSPF list). Distinct from the auto-imported
 * IPTV channel list — playlists are user curated collections of arbitrary media
 * items (local files, streams or imported channels) that can be reordered,
 * renamed and exported.
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdDate: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_items")
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val title: String,
    val mediaUrl: String,
    val logoUrl: String? = null,
    val groupTitle: String? = null,
    val position: Int = 0
)
