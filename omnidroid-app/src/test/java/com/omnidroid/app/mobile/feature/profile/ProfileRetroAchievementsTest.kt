package com.omnidroid.app.mobile.feature.profile

import com.omnidroid.lib.library.db.entity.RaGameCache
import com.omnidroid.lib.ra.RaAchievement
import com.omnidroid.lib.ra.RaAchievementBucket
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRetroAchievementsTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `aggregates games, totals, filters warnings, deduplicates, and sorts newest unlock first`() {
        val achOld =
            achievement(
                id = 10,
                title = "First Badges",
                points = 5,
                unlocked = 1,
                unlockTime = 1_700_000_000L,
            )
        val achNewest =
            achievement(
                id = 20,
                title = "Master Sword",
                points = 25,
                unlocked = 1,
                unlockTime = 1_800_000_000L,
            )
        val achLocked =
            achievement(
                id = 30,
                title = "Not Earned Yet",
                points = 10,
                unlocked = 0,
                unlockTime = 0L,
            )
        val warning =
            achievement(
                id = RA_WARNING_ACHIEVEMENT_ID,
                title = "Warning: Unknown Emulator",
                points = 0,
                unlocked = 1,
                unlockTime = 1_900_000_000L,
            )

        val entries =
            listOf(
                cacheEntry(
                    gameId = 1,
                    raGameId = 668,
                    title = "Pokemon Emerald Version",
                    numCore = 197,
                    numUnlocked = 1,
                    pointsCore = 1000,
                    pointsUnlocked = 5,
                    beatenTime = 0L,
                    completedTime = 0L,
                    buckets =
                        listOf(
                            bucket(5, "Recently Unlocked", listOf(warning, achOld)),
                            bucket(1, "Unlocked", listOf(achOld)),
                            bucket(2, "Locked", listOf(achLocked)),
                        ),
                ),
                cacheEntry(
                    gameId = 2,
                    raGameId = 777,
                    title = "Zelda Minish Cap",
                    numCore = 50,
                    numUnlocked = 50,
                    pointsCore = 500,
                    pointsUnlocked = 500,
                    beatenTime = 1_800_000_000L,
                    completedTime = 1_800_000_100L,
                    buckets = listOf(bucket(1, "Unlocked", listOf(achNewest))),
                ),
            )

        val result = aggregateProfileRetroAchievements(entries)

        assertEquals(2, result.games.size)
        assertEquals(51, result.totalUnlocked)
        assertEquals(247, result.totalCore)
        assertEquals(505, result.totalPointsUnlocked)
        assertEquals(1500, result.totalPointsCore)
        assertEquals(1, result.masteredGames)
        assertTrue(result.games[1].mastered)

        assertEquals(listOf(20, 10), result.unlockedAchievements.map { it.achievement.id })
        assertEquals("Zelda Minish Cap", result.unlockedAchievements.first().gameTitle)
    }

    @Test
    fun `handles empty cache list and malformed achievement json safely`() {
        assertEquals(ProfileRaLibraryAggregate(), aggregateProfileRetroAchievements(emptyList()))

        val corrupted =
            RaGameCache(
                gameId = 9,
                raGameId = 999,
                consoleId = 5,
                raHash = "abc",
                title = "",
                badgeUrl = "",
                numCoreAchievements = 10,
                numUnlockedAchievements = 0,
                pointsCore = 100,
                pointsUnlocked = 0,
                beatenTime = 0L,
                completedTime = 0L,
                richPresence = "",
                achievementsJson = "{not-valid-json",
                leaderboardsJson = "[]",
                updatedAt = 1234L,
            )

        val result = aggregateProfileRetroAchievements(listOf(corrupted))
        assertEquals(1, result.games.size)
        assertEquals("RA #999", result.games.single().title)
        assertEquals(0, result.unlockedAchievements.size)
    }

    private fun cacheEntry(
        gameId: Int,
        raGameId: Int,
        title: String,
        numCore: Int,
        numUnlocked: Int,
        pointsCore: Int,
        pointsUnlocked: Int,
        beatenTime: Long,
        completedTime: Long,
        buckets: List<RaAchievementBucket>,
    ) = RaGameCache(
        gameId = gameId,
        raGameId = raGameId,
        consoleId = 5,
        raHash = "hash_$gameId",
        title = title,
        badgeUrl = "https://media.retroachievements.org/Images/$raGameId.png",
        numCoreAchievements = numCore,
        numUnlockedAchievements = numUnlocked,
        pointsCore = pointsCore,
        pointsUnlocked = pointsUnlocked,
        beatenTime = beatenTime,
        completedTime = completedTime,
        richPresence = "",
        achievementsJson = json.encodeToString(buckets),
        leaderboardsJson = "[]",
        updatedAt = 1_800_000_000L,
    )

    private fun bucket(
        type: Int,
        label: String,
        items: List<RaAchievement>,
    ) = RaAchievementBucket(
        subsetId = 0,
        bucketType = type,
        label = label,
        achievements = items,
    )

    private fun achievement(
        id: Int,
        title: String,
        points: Int,
        unlocked: Int,
        unlockTime: Long,
    ) = RaAchievement(
        id = id,
        title = title,
        description = "Desc for $title",
        points = points,
        unlockTime = unlockTime,
        measuredPercent = 0f,
        measuredProgress = "",
        rarity = 12.5f,
        rarityHardcore = 6.25f,
        state = 2,
        category = 1,
        bucket = 1,
        unlocked = unlocked,
        type = 0,
        badgeName = "$id",
        badgeUrl = "https://media.retroachievements.org/Badge/$id.png",
        badgeLockedUrl = "https://media.retroachievements.org/Badge/${id}_lock.png",
    )
}
