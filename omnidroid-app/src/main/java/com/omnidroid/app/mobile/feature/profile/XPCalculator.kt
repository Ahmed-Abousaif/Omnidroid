package com.omnidroid.app.mobile.feature.profile

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure utility object for XP / level calculations.
 *
 * Level curve: `level = floor(sqrt(totalXP / 100))`
 * — level 1 at 100 XP, level 5 at 2 500, level 10 at 10 000, level 20 at 40 000.
 *
 * No Android dependencies — fully unit-testable.
 */
object XPCalculator {

    /** Cumulative XP required to reach the start of [level]. Level 0 = 0 XP. */
    fun xpForLevel(level: Int): Long = (level.toLong() * level) * 100L

    /** Current level derived from total accumulated XP. */
    fun levelFromXP(totalXP: Long): Int = floor(sqrt(totalXP.toDouble() / 100.0)).toInt()

    /**
     * Progress fraction [0.0, 1.0) within the current level.
     * Returns exactly 0.0 at the level boundary, approaches 1.0 near the next level.
     */
    fun progressInLevel(totalXP: Long): Float {
        val currentLevel = levelFromXP(totalXP)
        val currentFloor = xpForLevel(currentLevel)
        val nextFloor = xpForLevel(currentLevel + 1)
        val span = nextFloor - currentFloor
        return if (span <= 0L) 0f else ((totalXP - currentFloor).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }

    /** XP needed to finish the current level (reach the next level). */
    fun xpToNextLevel(totalXP: Long): Long {
        val nextFloor = xpForLevel(levelFromXP(totalXP) + 1)
        return max(0L, nextFloor - totalXP)
    }

    /**
     * Streak multiplier applied to base XP.
     * - Streak 0 or 1 → ×1.0
     * - Streak 7+ → ×1.7 (capped)
     * - Linear +0.1 per day between 1 and 7.
     */
    fun streakMultiplier(streak: Int): Double = 1.0 + (min(max(streak - 1, 0), 7) * 0.1)

    /**
     * Base XP from a session: 1 XP per minute played, minimum 1.
     * Fast-forwarded sessions award XP based on wall-clock time (fair — it's the player's time).
     */
    fun baseXPFromSession(durationMs: Long): Long = max(1L, durationMs / 60_000L)
}
