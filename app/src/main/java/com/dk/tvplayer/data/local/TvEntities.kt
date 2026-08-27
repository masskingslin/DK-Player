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
