package com.dk.tvplayer.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TvChannelDao {
    @Query("SELECT * FROM channels ORDER BY name ASC")
    fun getAllChannels(): Flow<List<TvChannelEntity>>

    @Query("SELECT * FROM channels WHERE groupTitle = :group ORDER BY name ASC")
    fun getChannelsByGroup(group: String): Flow<List<TvChannelEntity>>

    @Query("SELECT DISTINCT groupTitle FROM channels ORDER BY groupTitle ASC")
    fun getAllGroups(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<TvChannelEntity>)

    @Update
    suspend fun updateChannel(channel: TvChannelEntity)

    @Query("DELETE FROM channels")
    suspend fun clearChannels()
}

@Dao
interface TvEpgDao {
    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND endTime >= :currentTime ORDER BY startTime ASC")
    fun getProgramsForChannel(channelId: String, currentTime: Long): Flow<List<TvEpgProgramEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<TvEpgProgramEntity>)

    @Query("DELETE FROM epg_programs WHERE endTime < :cutoffTime")
    suspend fun purgeOldPrograms(cutoffTime: Long)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY lastWatchedTimestamp DESC LIMIT 30")
    fun getRecentHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(history: HistoryEntity)

    @Query("DELETE FROM playback_history WHERE mediaUrl = :url")
    suspend fun deleteByUrl(url: String)
}

@Dao
interface StreamDao {
    @Query("SELECT * FROM custom_streams ORDER BY addedDate DESC")
    fun getAllStreams(): Flow<List<StreamEntity>>

    @Query("SELECT * FROM custom_streams ORDER BY addedDate DESC")
    suspend fun getAllStreamsOnce(): List<StreamEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStream(stream: StreamEntity)

    @Delete
    suspend fun deleteStream(stream: StreamEntity)

    @Delete
    suspend fun deleteStreams(streams: List<StreamEntity>)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdDate DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY createdDate DESC")
    suspend fun getAllPlaylistsOnce(): List<PlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getItemsForPlaylist(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getItemsForPlaylistOnce(playlistId: Long): List<PlaylistItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: PlaylistItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<PlaylistItemEntity>)

    @Update
    suspend fun updateItems(items: List<PlaylistItemEntity>)

    @Delete
    suspend fun deleteItem(item: PlaylistItemEntity)

    @Delete
    suspend fun deleteItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId")
    fun getItemCount(playlistId: Long): Flow<Int>
}
