package com.stream.tvplayer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ChannelEntity::class, EpgEntity::class, HistoryEntity::class, StreamEntity::class],
    version = 2,
    exportSchema = false
)
abstract class TvDatabase : RoomDatabase() {
    abstract fun tvDao(): TvDao
    abstract fun historyDao(): HistoryDao
    abstract fun streamDao(): StreamDao

    companion object {
        @Volatile
        private var INSTANCE: TvDatabase? = null

        fun getInstance(context: Context): TvDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TvDatabase::class.java,
                    "tv_stream_master.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}