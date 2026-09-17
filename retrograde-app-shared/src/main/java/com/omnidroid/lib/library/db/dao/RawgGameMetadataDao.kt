package com.omnidroid.lib.library.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.omnidroid.lib.library.db.entity.RawgGameMetadata
import kotlinx.coroutines.flow.Flow

@Dao
interface RawgGameMetadataDao {
    @Query("SELECT * FROM rawg_game_metadata WHERE gameId = :gameId")
    suspend fun selectByGameId(gameId: Int): RawgGameMetadata?

    @Query("SELECT * FROM rawg_game_metadata WHERE gameId = :gameId")
    fun observeByGameId(gameId: Int): Flow<RawgGameMetadata?>

    @Query("SELECT * FROM rawg_game_metadata")
    suspend fun selectAll(): List<RawgGameMetadata>

    @Query(
        """
        SELECT * FROM rawg_game_metadata
        WHERE coverImageUrl IS NOT NULL
          AND backgroundImageUrl IS NOT NULL
          AND coverImageUrl = backgroundImageUrl
        """,
    )
    suspend fun selectRowsWithBackgroundUsedAsCover(): List<RawgGameMetadata>

    @Query(
        """
        SELECT g.id FROM games g
        LEFT JOIN rawg_game_metadata r ON r.gameId = g.id
        WHERE r.gameId IS NULL
        """,
    )
    suspend fun selectGameIdsMissingMetadata(): List<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: RawgGameMetadata)

    @Query("DELETE FROM rawg_game_metadata WHERE gameId = :gameId")
    suspend fun deleteByGameId(gameId: Int)

    @Query(
        """
        DELETE FROM rawg_game_metadata
        WHERE gameId NOT IN (SELECT id FROM games)
        """,
    )
    suspend fun deleteOrphans()
}
