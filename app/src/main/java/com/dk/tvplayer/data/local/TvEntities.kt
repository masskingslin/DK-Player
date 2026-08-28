package com.dk.tvplayer.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["url"], unique = true),
        Index(value = ["groupTitle"]),
        Index(value = ["isFavorite"])
    ]
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val groupTitle: String = "General",
    val epgChannelId: String? = null,
    val isFavorite: Boolean = false,
    val lastPlayedTimestamp: Long = 0L,
    val orderIndex: Int = 0,
    val playlistId: Long? = null
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val urlOrPath: String,
    val isLocalFile: Boolean,
    val channelCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "epg_programs")
data class EpgProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val title: String,
    val description: String? = null,
    val startTime: Long,
    val endTime: Long
)
