package com.omnidroid.lib.library.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.omnidroid.lib.library.db.entity.GameSession
import kotlinx.coroutines.flow.Flow

@Dao
interface GameSessionDao {
    @Insert
    suspend fun insert(session: GameSession): Long

    /** All sessions, newest first. */
    @Query("SELECT * FROM game_sessions ORDER BY playedAt DESC")
    fun observeAll(): Flow<List<GameSession>>

    /** Most recent N sessions, newest first. */
    @Query("SELECT * FROM game_sessions ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<GameSession>>

    /** Running total of all session durations in milliseconds. */
    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM game_sessions")
    fun observeTotalPlayTime(): Flow<Long>

    /** Running total of all XP earned across all sessions. */
    @Query("SELECT COALESCE(SUM(xpEarned), 0) FROM game_sessions")
    fun observeTotalXP(): Flow<Long>

    /** Total play time for a specific game in milliseconds. */
    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM game_sessions WHERE gameId = :gameId")
    fun observePlayTimeForGame(gameId: Int): Flow<Long>

    /** Total XP earned while playing a specific game. */
    @Query("SELECT COALESCE(SUM(xpEarned), 0) FROM game_sessions WHERE gameId = :gameId")
    fun observeXPForGame(gameId: Int): Flow<Long>

    /** Number of distinct calendar days (local time) on which any session occurred. */
    @Query(
        """
        SELECT COUNT(DISTINCT date(playedAt / 1000, 'unixepoch', 'localtime'))
        FROM game_sessions
        """,
    )
    suspend fun countDistinctPlayDays(): Int

    /** All sessions for a specific game, newest first. */
    @Query("SELECT * FROM game_sessions WHERE gameId = :gameId ORDER BY playedAt DESC")
    fun observeSessionsForGame(gameId: Int): Flow<List<GameSession>>
}
