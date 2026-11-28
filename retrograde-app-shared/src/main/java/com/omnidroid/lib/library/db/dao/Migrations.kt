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
            }
        }

    val VERSION_13_14: Migration =
        object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
            }
        }

    val VERSION_14_15: Migration =
        object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `games` ADD COLUMN `fileSize` INTEGER")
                database.execSQL("ALTER TABLE `games` ADD COLUMN `fileLastModified` INTEGER")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `unrecognized_files` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `fileUri` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `reason` TEXT NOT NULL,
                        `lastIndexedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_unrecognized_files_fileUri` ON `unrecognized_files` (`fileUri`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_unrecognized_files_lastIndexedAt` ON `unrecognized_files` (`lastIndexedAt`)",
                )
            }
        }

    val VERSION_15_16: Migration =
        object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val legacyMetadataTable =
                    listOf(
                        'r',
                        'a',
                        'w',
                        'g',
                        '_',
                        'g',
                        'a',
                        'm',
                        'e',
                        '_',
                        'm',
                        'e',
                        't',
                        'a',
                        'd',
                        'a',
                        't',
                        'a',
                    ).joinToString("")
                database.execSQL("DROP TABLE IF EXISTS `$legacyMetadataTable`")
            }
        }

    val VERSION_16_17: Migration =
        object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `games` ADD COLUMN `isAvailable` INTEGER NOT NULL DEFAULT 1",
                )
                database.execSQL("ALTER TABLE `games` ADD COLUMN `partialHash` TEXT")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `scan_directories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `treeUri` TEXT NOT NULL,
                        `documentId` TEXT NOT NULL,
                        `lastModified` INTEGER NOT NULL,
                        `probedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_scan_directories_treeUri_documentId`
                    ON `scan_directories` (`treeUri`, `documentId`)
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_scan_directories_treeUri`
                    ON `scan_directories` (`treeUri`)
                    """.trimIndent(),
                )
            }
        }

    val VERSION_18_19: Migration =
        object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `games` ADD COLUMN `customCoverOrigin` TEXT NOT NULL DEFAULT 'user'")
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `game_identities` (
                    `gameId` INTEGER NOT NULL PRIMARY KEY, `identityKey` TEXT NOT NULL, `lookupKey` TEXT NOT NULL,
                    `system` TEXT NOT NULL, `title` TEXT NOT NULL, `alternateTitle` TEXT, `region` TEXT,
                    `keyType` TEXT, `keyValue` TEXT, `matchMethod` TEXT NOT NULL, `libretroUrl` TEXT,
                    `thumbnailIndexed` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `fileSize` INTEGER, `fileLastModified` INTEGER,
                    FOREIGN KEY(`gameId`) REFERENCES `games`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_game_identities_lookupKey` ON `game_identities` (`lookupKey`)",
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `catalog_lookups` (
                    `lookupKey` TEXT NOT NULL PRIMARY KEY, `state` TEXT NOT NULL, `payload` TEXT NOT NULL,
                    `retryAt` INTEGER NOT NULL, `fetchedAt` INTEGER NOT NULL, `assetRetryAt` INTEGER NOT NULL DEFAULT 0)""",
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `game_artwork` (
                    `gameId` INTEGER NOT NULL PRIMARY KEY, `identityKey` TEXT NOT NULL, `source` TEXT NOT NULL, `sourceUrl` TEXT NOT NULL,
                    FOREIGN KEY(`gameId`) REFERENCES `games`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""",
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `artwork_attempts` (
                    `url` TEXT NOT NULL PRIMARY KEY, `state` TEXT NOT NULL, `retryAt` INTEGER NOT NULL)""",
                )
            }
        }

    val VERSION_17_18: Migration =
        object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `games` ADD COLUMN `isManualMatch` INTEGER NOT NULL DEFAULT 0",
                )
                // Older versions did not record which matches were user-selected.
                // Preserve existing metadata rather than risk overwriting those choices.
                database.execSQL("UPDATE `games` SET `isManualMatch` = 1")
            }
        }

    val VERSION_19_20: Migration =
        object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ra_game_cache` (
                        `gameId` INTEGER NOT NULL PRIMARY KEY,
                        `raGameId` INTEGER NOT NULL,
                        `consoleId` INTEGER NOT NULL,
                        `raHash` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `badgeUrl` TEXT NOT NULL,
                        `numCoreAchievements` INTEGER NOT NULL,
                        `numUnlockedAchievements` INTEGER NOT NULL,
                        `pointsCore` INTEGER NOT NULL,
                        `pointsUnlocked` INTEGER NOT NULL,
                        `beatenTime` INTEGER NOT NULL,
                        `completedTime` INTEGER NOT NULL,
                        `richPresence` TEXT NOT NULL,
                        `achievementsJson` TEXT NOT NULL,
                        `leaderboardsJson` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`gameId`) REFERENCES `games`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
            }
        }
}
