package com.stream.tvplayer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TvDao {
    // ... unchanged, exactly as you have it ...
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY lastPlayedAt DESC")
    fun getHistoryStream(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: HistoryEntity): Long

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}

@Dao
interface StreamDao {
    @Query("SELECT * FROM streams ORDER BY savedAt DESC")
    fun getStreamsFlow(): Flow<List<StreamEntity>>

    @Insert
    suspend fun insert(stream: StreamEntity): Long

    @Query("DELETE FROM streams WHERE id = :id")
    suspend fun delete(id: Long)
}