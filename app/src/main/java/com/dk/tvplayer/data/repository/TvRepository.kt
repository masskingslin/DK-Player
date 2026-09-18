package com.dk.tvplayer.data.repository

import com.dk.tvplayer.data.local.HistoryDao
import com.dk.tvplayer.data.local.HistoryEntity
import com.dk.tvplayer.data.local.LocalAudioItem
import com.dk.tvplayer.data.local.LocalAudioScanner
import com.dk.tvplayer.data.local.LocalVideoItem
import com.dk.tvplayer.data.local.LocalVideoScanner
import com.dk.tvplayer.data.local.PlaylistDao
import com.dk.tvplayer.data.local.PlaylistEntity
import com.dk.tvplayer.data.local.PlaylistItemEntity
import com.dk.tvplayer.data.local.StreamDao
import com.dk.tvplayer.data.local.StreamEntity
import com.dk.tvplayer.data.local.TvChannelDao
import com.dk.tvplayer.data.local.TvChannelEntity
import com.dk.tvplayer.data.local.TvEpgDao
import com.dk.tvplayer.data.local.TvEpgProgramEntity
import com.dk.tvplayer.data.parser.M3uEntry
import com.dk.tvplayer.data.parser.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.InputStream

class TvRepository(
    private val channelDao: TvChannelDao,
    private val epgDao: TvEpgDao,
    private val historyDao: HistoryDao,
    private val streamDao: StreamDao,
    private val playlistDao: PlaylistDao,
    private val videoScanner: LocalVideoScanner,
    private val audioScanner: LocalAudioScanner
) {
    fun getAllChannels(): Flow<List<TvChannelEntity>> = channelDao.getAllChannels()
    fun getAllGroups(): Flow<List<String>> = channelDao.getAllGroups()
    fun getPrograms(channelId: String): Flow<List<TvEpgProgramEntity>> =
        epgDao.getProgramsForChannel(channelId, System.currentTimeMillis())

    fun getHistory(): Flow<List<HistoryEntity>> = historyDao.getRecentHistory()

    suspend fun getHistoryEntryOnce(url: String): HistoryEntity? = withContext(Dispatchers.IO) {
        historyDao.getByUrl(url)
    }
    fun getCustomStreams(): Flow<List<StreamEntity>> = streamDao.getAllStreams()

    suspend fun insertCustomStream(name: String, url: String) = withContext(Dispatchers.IO) {
        streamDao.insertStream(StreamEntity(name = name, streamUrl = url))
    }

    suspend fun deleteCustomStream(stream: StreamEntity) = withContext(Dispatchers.IO) {
        streamDao.deleteStream(stream)
    }

    suspend fun deleteCustomStreams(streams: List<StreamEntity>) = withContext(Dispatchers.IO) {
        streamDao.deleteStreams(streams)
    }

    suspend fun saveHistory(url: String, title: String, position: Long, duration: Long) =
        withContext(Dispatchers.IO) {
            historyDao.insertOrUpdate(
                HistoryEntity(
                    mediaUrl = url,
                    title = title,
                    lastPositionMs = position,
                    durationMs = duration
                )
            )
        }

    suspend fun loadM3u(inputStream: InputStream) = withContext(Dispatchers.IO) {
        val parsed = M3uParser.parse(inputStream)
        channelDao.clearChannels()
        channelDao.insertChannels(parsed)
    }

    suspend fun scanLocalVideos(): List<LocalVideoItem> = withContext(Dispatchers.IO) {
        videoScanner.scanDeviceVideos()
    }

    suspend fun scanLocalAudio(): List<LocalAudioItem> = withContext(Dispatchers.IO) {
        audioScanner.scanDeviceAudio()
    }

    // ---- Playlist management (Playlists tab) ----

    fun getAllPlaylists(): Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()

    fun getPlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>> =
        playlistDao.getItemsForPlaylist(playlistId)

    fun getPlaylistItemCount(playlistId: Long): Flow<Int> = playlistDao.getItemCount(playlistId)

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylist(PlaylistEntity(name = name))
    }

    suspend fun renamePlaylist(playlist: PlaylistEntity, newName: String) = withContext(Dispatchers.IO) {
        playlistDao.updatePlaylist(playlist.copy(name = newName))
    }

    suspend fun deletePlaylist(playlist: PlaylistEntity) = withContext(Dispatchers.IO) {
        playlistDao.clearPlaylist(playlist.id)
        playlistDao.deletePlaylist(playlist)
    }

    suspend fun addItemToPlaylist(playlistId: Long, title: String, url: String, group: String? = null, logo: String? = null) =
        withContext(Dispatchers.IO) {
            val existing = playlistDao.getItemsForPlaylistOnce(playlistId)
            playlistDao.insertItem(
                PlaylistItemEntity(
                    playlistId = playlistId,
                    title = title,
                    mediaUrl = url,
                    groupTitle = group,
                    logoUrl = logo,
                    position = existing.size
                )
            )
        }

    /** Persists a channel's favorite flag directly (source of truth for Home's Favorite
     *  Channels row and the IPTV Channels tab's favorite filter — see
     *  TvPlayerViewModel.toggleFavorite). */
    suspend fun setChannelFavorite(channelId: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        channelDao.setFavorite(channelId, isFavorite)
    }

    /**
     * Toggles a playlist item's own favorite flag, and mirrors that onto a matching
     * entry in the "channels" table (matched by stream URL, since playlist items don't
     * carry a stable channelId) so the Home screen's Favorite Channels row and the
     * IPTV Channels tab both pick it up — favoriting a channel from either place ends
     * up meaning the same thing. If no channel with this stream URL exists yet (the
     * common case for an item added straight to a custom playlist rather than via the
     * IPTV import), one is created so there's something for "favorite" to attach to.
     */
    suspend fun setPlaylistItemFavorite(item: PlaylistItemEntity, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        playlistDao.setItemFavorite(item.id, isFavorite)

        val existingChannel = channelDao.getChannelByStreamUrl(item.mediaUrl)
        if (existingChannel != null) {
            channelDao.setFavorite(existingChannel.channelId, isFavorite)
        } else if (isFavorite) {
            // Same channelId derivation M3uParser uses for entries with no tvg-id, so a
            // later real M3U import of the same channel will land on the same row
            // instead of creating a duplicate.
            val channelId = item.title.lowercase().replace(" ", "_")
            channelDao.insertChannel(
                TvChannelEntity(
                    channelId = channelId,
                    name = item.title,
                    logoUrl = item.logoUrl,
                    groupTitle = item.groupTitle ?: "General",
                    streamUrl = item.mediaUrl,
                    isFavorite = true
                )
            )
        }
    }

    suspend fun removeItemFromPlaylist(item: PlaylistItemEntity) = withContext(Dispatchers.IO) {
        playlistDao.deleteItem(item)
    }

    suspend fun removeItemsFromPlaylist(items: List<PlaylistItemEntity>) = withContext(Dispatchers.IO) {
        playlistDao.deleteItems(items)
    }

    /** Moves items (typically a multi-selected batch) into a different playlist. */
    suspend fun moveItemsToPlaylist(items: List<PlaylistItemEntity>, targetPlaylistId: Long) =
        withContext(Dispatchers.IO) {
            val startIndex = playlistDao.getItemsForPlaylistOnce(targetPlaylistId).size
            val moved = items.mapIndexed { index, item ->
                item.copy(id = 0, playlistId = targetPlaylistId, position = startIndex + index)
            }
            playlistDao.deleteItems(items)
            playlistDao.insertItems(moved)
        }

    /** Persists a full reorder (e.g. after drag / move up-down) in one shot. */
    suspend fun reorderPlaylistItems(items: List<PlaylistItemEntity>) = withContext(Dispatchers.IO) {
        val reindexed = items.mapIndexed { index, item -> item.copy(position = index) }
        playlistDao.updateItems(reindexed)
    }

    /** Imports an M3U file directly into a specific user playlist (as opposed to the global channel list). */
    suspend fun importM3uIntoPlaylist(playlistId: Long, inputStream: InputStream) = withContext(Dispatchers.IO) {
        val entries: List<M3uEntry> = M3uParser.parseEntries(inputStream)
        val existingCount = playlistDao.getItemsForPlaylistOnce(playlistId).size
        val items = entries.mapIndexed { index, entry ->
            PlaylistItemEntity(
                playlistId = playlistId,
                title = entry.name,
                mediaUrl = entry.streamUrl,
                logoUrl = entry.logoUrl,
                groupTitle = entry.groupTitle,
                position = existingCount + index
            )
        }
        playlistDao.insertItems(items)
    }

    suspend fun getPlaylistItemsOnce(playlistId: Long): List<PlaylistItemEntity> = withContext(Dispatchers.IO) {
        playlistDao.getItemsForPlaylistOnce(playlistId)
    }

    suspend fun getAllPlaylistsOnce(): List<PlaylistEntity> = withContext(Dispatchers.IO) {
        playlistDao.getAllPlaylistsOnce()
    }

    suspend fun getAllStreamsOnce(): List<StreamEntity> = withContext(Dispatchers.IO) {
        streamDao.getAllStreamsOnce()
    }

    /** Used by settings-import restore: re-creates playlists (with fresh ids) from a backup. */
    suspend fun restorePlaylist(name: String, items: List<PlaylistItemEntity>) = withContext(Dispatchers.IO) {
        val newId = playlistDao.insertPlaylist(PlaylistEntity(name = name))
        playlistDao.insertItems(items.mapIndexed { index, item -> item.copy(id = 0, playlistId = newId, position = index) })
    }

    suspend fun restoreCustomStream(stream: StreamEntity) = withContext(Dispatchers.IO) {
        streamDao.insertStream(stream.copy(id = 0))
    }
}
