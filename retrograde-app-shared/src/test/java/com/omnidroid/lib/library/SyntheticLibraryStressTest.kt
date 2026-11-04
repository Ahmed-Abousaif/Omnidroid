package com.omnidroid.lib.library

import com.omnidroid.lib.library.db.entity.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Builds synthetic Game rows used for large-library stress planning.
 * Targets from the plan: 1000 and 5000 games with stable ids for paging keys.
 */
class SyntheticLibraryStressTest {
    @Test
    fun buildsOneThousandGamesWithStableIds() {
        val games = syntheticLibrary(1_000)
        assertEquals(1_000, games.size)
        assertEquals(1, games.first().id)
        assertEquals(1_000, games.last().id)
        assertTrue(games.all { it.coverFrontUrl != null })
    }

    @Test
    fun buildsFiveThousandGames() {
        val games = syntheticLibrary(5_000)
        assertEquals(5_000, games.size)
        assertTrue(games.map { it.id }.toSet().size == 5_000)
    }

    companion object {
        fun syntheticLibrary(count: Int): List<Game> =
            (1..count).map { index ->
                Game(
                    id = index,
                    fileName = "Game %05d.nes".format(index),
                    fileUri = "content://synthetic/doc/game_$index",
                    title = "Synthetic Game %05d".format(index),
                    systemId = if (index % 2 == 0) "nes" else "snes",
                    developer = "Omnidroid",
                    coverFrontUrl = "https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%20Entertainment%20System/Named_Boxarts/Synthetic%20$index.png",
                    lastIndexedAt = index.toLong(),
                    fileSize = 32_768L + index,
                    fileLastModified = 1_700_000_000_000L + index,
                    isAvailable = true,
                )
            }
    }
}
