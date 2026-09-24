package com.dk.tvplayer.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoGroupDao {
    @Query("SELECT * FROM video_groups ORDER BY name ASC")
    fun getAllGroups(): Flow<List<VideoGroupEntity>>

    @Insert
    suspend fun insertGroup(group: VideoGroupEntity): Long

    @Query("UPDATE video_groups SET name = :name WHERE id = :groupId")
    suspend fun renameGroup(groupId: Long, name: String)

    @Query("DELETE FROM video_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: Long)

    // Ungrouping members is a separate statement (rather than an SQL cascade) since
    // "delete the group" and "clear every member's groupId" are two different tables
    // with no foreign key declared between them (LocalVideoMetaEntity.groupId is a
    // plain nullable Long, not a Room @ForeignKey, to avoid forcing every meta row to
    // reference a real group row).
    @Query("UPDATE local_video_meta SET groupId = NULL WHERE groupId = :groupId")
    suspend fun clearGroupMembers(groupId: Long)

    @Query("SELECT * FROM local_video_meta")
    fun getAllMeta(): Flow<List<LocalVideoMetaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(meta: LocalVideoMetaEntity)

    @Query("SELECT * FROM local_video_meta WHERE filePath = :filePath")
    suspend fun getMeta(filePath: String): LocalVideoMetaEntity?

    @Query("UPDATE local_video_meta SET isPlayed = :isPlayed WHERE filePath = :filePath")
    suspend fun setPlayed(filePath: String, isPlayed: Boolean)

    @Query("UPDATE local_video_meta SET groupId = :groupId WHERE filePath IN (:filePaths)")
    suspend fun assignGroup(filePaths: List<String>, groupId: Long?)

    @Delete
    suspend fun deleteMeta(meta: LocalVideoMetaEntity)
}
