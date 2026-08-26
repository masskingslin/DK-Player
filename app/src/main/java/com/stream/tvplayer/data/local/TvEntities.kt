package com.stream.tvplayer.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "channels",
    indices = [Index(value = ["tvgId"]), Index(value = ["groupName"])]
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelNumber: Int,
    val name: String,
    val streamUrl: String,
    val tvgId: String?,
    val logoUrl: String?,
    val groupName: String?,
    val licenseServerUrl: String? = null // For Widevine DRM
)

@Entity(
    tableName = "epg_programs",
    indices = [
        Index(value = ["channelId", "startEpochMs", "endEpochMs"]),
        Index(value = ["startEpochMs"])
    ]
)
data class EpgEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val title: String,
    val description: String?,
    val startEpochMs: Long,
    val endEpochMs: Long
)
