package com.omnidroid.lib.library.db.dao

import androidx.room.Dao
import androidx.room.Embedded
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

    /** Resolve history in one reactive query, including renamed games and custom artwork. */
    @Query(
        """
        SELECT game_sessions.*,
            COALESCE(NULLIF(TRIM(games.customName), ''), games.title) AS gameTitle,
            COALESCE(games.customCoverPath, games.coverFrontUrl) AS coverArt,
            games.systemId AS systemId
        FROM game_sessions
        INNER JOIN games ON games.id = game_sessions.gameId
        ORDER BY game_sessions.playedAt DESC, game_sessions.id DESC
        LIMIT :limit
        """,
    )
    fun observeRecentWithGames(limit: Int): Flow<List<GameSessionWithGame>>

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

    /** Session count and total play duration for a specific game. */
    @Query(
        "SELECT COUNT(*) as sessionCount, COALESCE(SUM(durationMs), 0) as totalDurationMs " +
            "FROM game_sessions WHERE gameId = :gameId",
    )
    fun observeStatsForGame(gameId: Int): Flow<GamePlayStats>
}

data class GamePlayStats(
    val sessionCount: Int = 0,
    val totalDurationMs: Long = 0L,
)

data class GameSessionWithGame(
    @Embedded val session: GameSession,
    val gameTitle: String,
    val coverArt: String?,
    val systemId: String,
)
