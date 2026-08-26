package com.stream.tvplayer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TvDao {

    @Query("SELECT * FROM channels ORDER BY channelNumber ASC")
    fun getAllChannels(): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels")
    suspend fun clearChannels()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpgPrograms(programs: List<EpgEntity>)

    @Query("DELETE FROM epg_programs WHERE endEpochMs < :nowEpochMs")
    suspend fun clearExpiredEpg(nowEpochMs: Long)

    @Query("DELETE FROM epg_programs")
    suspend fun clearAllEpg()

    @Query(
        """
        SELECT * FROM epg_programs 
        WHERE channelId = :channelTvgId 
          AND startEpochMs <= :nowEpochMs 
          AND endEpochMs > :nowEpochMs 
        LIMIT 1
        """
    )
    suspend fun getCurrentProgram(channelTvgId: String, nowEpochMs: Long): EpgEntity?

    @Query(
        """
        SELECT * FROM epg_programs 
        WHERE channelId = :channelTvgId 
          AND startEpochMs >= :fromEpochMs 
        ORDER BY startEpochMs ASC 
        LIMIT 1
        """
    )
    suspend fun getNextProgram(channelTvgId: String, fromEpochMs: Long): EpgEntity?

    @Transaction
    suspend fun refreshPlaylist(channels: List<ChannelEntity>) {
        clearChannels()
        insertChannels(channels)
    }
}
