package com.dk.tvplayer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TvChannelEntity::class,
        TvEpgProgramEntity::class,
        HistoryEntity::class,
        StreamEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class TvDatabase : RoomDatabase() {
    abstract fun channelDao(): TvChannelDao
    abstract fun epgDao(): TvEpgDao
    abstract fun historyDao(): HistoryDao
    abstract fun streamDao(): StreamDao

    companion object {
        @Volatile
        private var INSTANCE: TvDatabase? = null

        fun getDatabase(context: Context): TvDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TvDatabase::class.java,
                    "dk_tvplayer_database.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
