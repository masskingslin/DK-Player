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
        StreamEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        VideoGroupEntity::class,
        LocalVideoMetaEntity::class,
        BookmarkEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class TvDatabase : RoomDatabase() {
    abstract fun channelDao(): TvChannelDao
    abstract fun epgDao(): TvEpgDao
    abstract fun historyDao(): HistoryDao
    abstract fun streamDao(): StreamDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun videoGroupDao(): VideoGroupDao
    abstract fun bookmarkDao(): BookmarkDao

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
                    // Real migrations for v3 -> v7 (see TvMigrations): playlists, bookmarks,
                    // favourites and history are kept across app updates.
                    .addMigrations(*TvMigrations.ALL)
                    // v1/v2 pre-date any recorded schema, so there is nothing to migrate
                    // from; rebuild those (and any downgrade) rather than crash.
                    .fallbackToDestructiveMigrationFrom(true, 1, 2)
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
