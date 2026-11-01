package com.dk.tvplayer.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Real, non-destructive Room migrations for every schema version that existed before
 * this file did (v3 -> v7). Playlists, bookmarks, favourites and watch history are user
 * data; they must survive app updates instead of being wiped by a destructive fallback.
 *
 * Schema history (taken from the original TvDatabase notes):
 *  - v3: playlists + playlist_items tables added
 *  - v4: channels.userAgent / channels.referrer columns added
 *  - v5: playlist_items.isFavorite column added
 *  - v6: video_groups + local_video_meta tables added
 *  - v7: bookmarks table added
 *
 * Versions 1 and 2 pre-date any recorded schema, so those (very old) installs still fall
 * back to a destructive rebuild — see [TvDatabase.getDatabase].
 *
 * Every statement is written to be idempotent (IF NOT EXISTS / column-exists checks), and
 * the CREATE TABLE statements are byte-for-byte what Room generates for the entities in
 * TvEntities.kt, because Room validates the migrated schema against them at open time.
 *
 * From v8 onwards: bump the version, add a Migration here, and test it against the JSON
 * exported to app/schemas/.
 */
object TvMigrations {

    private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean =
        query("PRAGMA table_info(`$table`)").use { c ->
            val nameIdx = c.getColumnIndex("name")
            while (c.moveToNext()) if (c.getString(nameIdx) == column) return true
            false
        }

    private fun SupportSQLiteDatabase.addColumnIfMissing(table: String, column: String, definition: String) {
        if (!hasColumn(table, column)) execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $definition")
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addColumnIfMissing("channels", "userAgent", "TEXT")
            db.addColumnIfMissing("channels", "referrer", "TEXT")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addColumnIfMissing("playlist_items", "isFavorite", "INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `video_groups` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`createdDate` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `local_video_meta` (" +
                    "`filePath` TEXT NOT NULL, " +
                    "`groupId` INTEGER, " +
                    "`isPlayed` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`filePath`))"
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `bookmarks` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`mediaUrl` TEXT NOT NULL, " +
                    "`mediaTitle` TEXT NOT NULL, " +
                    "`positionMs` INTEGER NOT NULL, " +
                    "`label` TEXT NOT NULL, " +
                    "`createdDate` INTEGER NOT NULL)"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
}
