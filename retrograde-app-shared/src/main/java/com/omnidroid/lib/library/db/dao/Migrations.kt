package com.omnidroid.lib.library.db.dao

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val VERSION_8_9: Migration =
        object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `datafiles`(
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `gameId` INTEGER NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `fileUri` TEXT NOT NULL,
                        `lastIndexedAt` INTEGER NOT NULL,
                        `path` TEXT, FOREIGN KEY(`gameId`
                    ) REFERENCES `games`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
                    """.trimIndent(),
                )

                database.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_datafiles_id` ON `datafiles` (`id`)
                    """.trimIndent(),
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_datafiles_fileUri` ON `datafiles` (`fileUri`)
                    """.trimIndent(),
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_datafiles_gameId` ON `datafiles` (`gameId`)
                    """.trimIndent(),
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_datafiles_lastIndexedAt` ON `datafiles` (`lastIndexedAt`)
                    """.trimIndent(),
                )
            }
        }

    val VERSION_9_10: Migration =
        object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `games` ADD COLUMN `customCoverPath` TEXT")
            }
        }

    val VERSION_10_11: Migration =
        object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `games` ADD COLUMN `customName` TEXT")
            }
        }

    val VERSION_11_12: Migration =
        object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `game_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `gameId` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `playedAt` INTEGER NOT NULL,
                        `xpEarned` INTEGER NOT NULL,
                        `streakDay` INTEGER NOT NULL,
                        FOREIGN KEY(`gameId`) REFERENCES `games`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_game_sessions_id` ON `game_sessions` (`id`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_game_sessions_gameId` ON `game_sessions` (`gameId`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_game_sessions_playedAt` ON `game_sessions` (`playedAt`)",
                )
            }
        }

    val VERSION_12_13: Migration =
        object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `rawg_game_metadata` (
                        `gameId` INTEGER NOT NULL,
                        `rawgId` INTEGER NOT NULL,
                        `description` TEXT,
                        `genres` TEXT,
                        `released` TEXT,
                        `backgroundImageUrl` TEXT,
                        `coverImageUrl` TEXT,
                        `rating` REAL,
                        `publisher` TEXT,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`gameId`),
                        FOREIGN KEY(`gameId`) REFERENCES `games`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_rawg_game_metadata_gameId` ON `rawg_game_metadata` (`gameId`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_rawg_game_metadata_rawgId` ON `rawg_game_metadata` (`rawgId`)",
                )
            }
        }
}
