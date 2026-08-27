package com.stream.tvplayer.data.local

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStream(stream: StreamEntity)

    @Delete
    suspend fun deleteStream(stream: StreamEntity)
}
