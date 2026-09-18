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
    val isFavorite: Boolean = false,
    // Per-channel HTTP overrides parsed from #EXTVLCOPT / piped-URL directives in the
    // source M3U (see M3uParser). Many channels in large aggregated playlists (e.g.
    // iptv-org's index.m3u) are behind CDNs that 403 requests missing these — VLC and
    // Kodi apply them per-stream, so we carry them through to playback the same way.
    val userAgent: String? = null,
    val referrer: String? = null
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
    val position: Int = 0,
    // Local to this playlist item. Toggling it also mirrors the channel into (or
    // updates it in) the "channels" table's own isFavorite flag so it shows up in the
    // Home screen's Favorite Channels row and the IPTV Channels tab too — see
    // TvPlayerViewModel.toggleFavoritePlaylistItem.
    val isFavorite: Boolean = false
)
