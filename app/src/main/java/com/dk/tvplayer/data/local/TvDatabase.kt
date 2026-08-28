package com.dk.tvplayer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChannelEntity::class,
        PlaylistEntity::class,
        EpgProgramEntity::class,
        HistoryEntity::class,
        StreamEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class TvDatabase : RoomDatabase() {
    abstract fun tvDao(): TvDao
}
