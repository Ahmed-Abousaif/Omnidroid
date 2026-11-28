package com.omnidroid.lib.library.db.entity

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "ra_game_cache",
    foreignKeys = [
        ForeignKey(
            entity = Game::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RaGameCache(
    @PrimaryKey val gameId: Int,
    val raGameId: Int,
    val consoleId: Int,
    val raHash: String,
    val title: String,
    val badgeUrl: String,
    val numCoreAchievements: Int,
    val numUnlockedAchievements: Int,
    val pointsCore: Int,
    val pointsUnlocked: Int,
    val beatenTime: Long,
    val completedTime: Long,
    val richPresence: String,
    val achievementsJson: String,
    val leaderboardsJson: String,
    val updatedAt: Long,
)

@Dao
interface RaGameCacheDao {
    @Query("SELECT * FROM ra_game_cache WHERE gameId = :gameId LIMIT 1")
    fun observeByGameId(gameId: Int): Flow<RaGameCache?>

    @Query("SELECT * FROM ra_game_cache ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<RaGameCache>>

    @Query("SELECT * FROM ra_game_cache WHERE gameId = :gameId LIMIT 1")
    suspend fun getByGameId(gameId: Int): RaGameCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: RaGameCache)

    @Query("DELETE FROM ra_game_cache")
    suspend fun clearAll()
}
