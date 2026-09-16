package com.omnidroid.lib.library.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "game_sessions",
    indices = [
        Index("id", unique = true),
        Index("gameId"),
        Index("playedAt"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = Game::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class GameSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** FK to the game that was played. Cascade-deleted when the game is removed. */
    val gameId: Int,
    /** Wall-clock length of this session in milliseconds. */
    val durationMs: Long,
    /** Epoch ms timestamp when this session ended. */
    val playedAt: Long,
    /** XP awarded for this session (base XP × streak multiplier, rounded). */
    val xpEarned: Long,
    /** The player's active streak count at the time this session was recorded. */
    val streakDay: Int,
)
