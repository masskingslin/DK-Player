package com.dk.tvplayer.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TvDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Update
    suspend fun updateChannel(channel: ChannelEntity)

    @Delete
    suspend fun deleteChannel(channel: ChannelEntity)

    @Query("DELETE FROM channels WHERE id IN (:channelIds)")
    suspend fun deleteChannelsByIds(channelIds: List<Long>)

    @Query("UPDATE channels SET groupTitle = :targetGroup WHERE id IN (:channelIds)")
    suspend fun moveChannelsToGroup(channelIds: List<Long>, targetGroup: String)

    @Query("""
        SELECT * FROM channels 
        WHERE (:query IS NULL OR name LIKE '%' || :query || '%' OR groupTitle LIKE '%' || :query || '%')
        AND (:category IS NULL OR groupTitle = :category)
        AND (:onlyFavorites = 0 OR isFavorite = 1)
        ORDER BY 
            CASE WHEN :sortBy = 'NAME_ASC' THEN name END ASC,
            CASE WHEN :sortBy = 'NAME_DESC' THEN name END DESC,
            CASE WHEN :sortBy = 'RECENT' THEN lastPlayedTimestamp END DESC,
            orderIndex ASC
    """)
    fun getFilteredChannels(
        query: String?,
        category: String?,
        onlyFavorites: Boolean,
        sortBy: String
    ): Flow<List<ChannelEntity>>

    @Query("SELECT DISTINCT groupTitle FROM channels WHERE groupTitle IS NOT NULL AND groupTitle != ''")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT * FROM channels")
    suspend fun getAllChannelsSync(): List<ChannelEntity>

    // Playlists
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("SELECT * FROM playlists ORDER BY lastUpdated DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylistsSync(): List<PlaylistEntity>

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteChannelsForPlaylist(playlistId: Long)
}
