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
        LocalVideoMetaEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class TvDatabase : RoomDatabase() {
    abstract fun channelDao(): TvChannelDao
    abstract fun epgDao(): TvEpgDao
    abstract fun historyDao(): HistoryDao
    abstract fun streamDao(): StreamDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun videoGroupDao(): VideoGroupDao

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
                    // Playlist tables were new in v3, the channels table's
                    // userAgent/referrer columns were new in v4, playlist_items'
                    // isFavorite column was new in v5, and the video_groups /
                    // local_video_meta tables are new in v6; destructive fallback is
                    // acceptable here since channels/streams/history are all re-derived
                    // or re-added by the user (e.g. by reloading their M3U) rather than
                    // being irreplaceable data.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
