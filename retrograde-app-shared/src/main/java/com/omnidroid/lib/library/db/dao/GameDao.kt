/*
 * GameDao.kt
 *
 * Copyright (C) 2017 Retrograde Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.omnidroid.lib.library.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.omnidroid.lib.library.db.entity.Game
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun selectById(id: Int): Game?

    @Query("SELECT * FROM games WHERE id = :id")
    fun selectByIdBlocking(id: Int): Game?

    @Query("SELECT * FROM games WHERE id = :id")
    fun observeById(id: Int): Flow<Game?>

    @Query("SELECT * FROM games")
    suspend fun selectAll(): List<Game>

    @Query("SELECT * FROM games WHERE fileUri = :fileUri")
    fun selectByFileUri(fileUri: String): Game?

    @Query("SELECT * FROM games WHERE fileUri = :fileUri")
    suspend fun asyncSelectByFileUri(fileUri: String): Game?

    @Query("SELECT * FROM games WHERE fileName = :fileName")
    suspend fun selectByFileName(fileName: String): List<Game>

    @Query("SELECT * FROM games WHERE lastIndexedAt < :lastIndexedAt")
    fun selectByLastIndexedAtLessThan(lastIndexedAt: Long): List<Game>

    @Query(
        """
        SELECT * FROM games WHERE lastPlayedAt IS NOT NULL AND isFavorite = 0 ORDER BY lastPlayedAt DESC LIMIT :limit
        """,
    )
    fun selectFirstUnfavoriteRecents(limit: Int): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun selectFirstFavoritesRecents(limit: Int): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun asyncSelectFirstRecents(limit: Int): List<Game>

    @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun selectFirstFavorites(limit: Int): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE lastPlayedAt IS NULL LIMIT :limit")
    fun selectFirstNotPlayed(limit: Int): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT 1")
    fun selectLastPlayed(): Flow<List<Game>>

    @RawQuery(observedEntities = [Game::class])
    fun selectPagedRaw(query: SupportSQLiteQuery): PagingSource<Int, Game>

    @Query("SELECT * FROM games ORDER BY RANDOM() LIMIT 1")
    suspend fun selectRandom(): Game?

    @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY RANDOM() LIMIT 1")
    suspend fun selectRandomFavorite(): Game?

    @Query("SELECT * FROM games WHERE systemId IN (:systemIds) ORDER BY RANDOM() LIMIT 1")
    suspend fun selectRandomBySystems(systemIds: List<String>): Game?

    @Query("SELECT DISTINCT systemId FROM games ORDER BY systemId ASC")
    suspend fun selectSystems(): List<String>

    @Query("SELECT count(*) count, systemId systemId FROM games GROUP BY systemId")
    fun selectSystemsWithCount(): Flow<List<SystemCount>>

    @Insert
    fun insert(games: List<Game>): List<Long>

    @Delete
    fun delete(games: List<Game>)

    @Update
    suspend fun update(game: Game)

    @Update
    fun update(games: List<Game>)
}

data class SystemCount(val systemId: String, val count: Int)

enum class GameOrder(internal val sql: String) {
    TITLE("title ASC, id DESC"),
    RECENTLY_PLAYED("lastPlayedAt IS NULL, lastPlayedAt DESC, title ASC, id DESC"),
    FAVORITES_FIRST("isFavorite DESC, title ASC, id DESC"),
    CONSOLE("systemId ASC, title ASC, id DESC"),
}

/**
 * Paged games, optionally narrowed to systems, favorites and a title search.
 * [systemOrder] ranks systems for [GameOrder.CONSOLE]; unlisted systems follow by id.
 */
fun GameDao.selectPaged(
    systemIds: List<String>? = null,
    favoritesOnly: Boolean = false,
    query: String = "",
    order: GameOrder = GameOrder.TITLE,
    systemOrder: List<String> = emptyList(),
): PagingSource<Int, Game> {
    val conditions = mutableListOf<String>()
    val args = mutableListOf<Any>()
    if (favoritesOnly) conditions += "isFavorite = 1"
    if (systemIds != null) {
        conditions += "systemId IN (${systemIds.joinToString(",") { "?" }})"
        args.addAll(systemIds)
    }
    if (query.isNotEmpty()) {
        conditions += "(title LIKE '%' || ? || '%' OR customName LIKE '%' || ? || '%')"
        args += query
        args += query
    }
    val where = if (conditions.isEmpty()) "" else " WHERE " + conditions.joinToString(" AND ")
    val orderBy =
        if (order == GameOrder.CONSOLE && systemOrder.isNotEmpty()) {
            args.addAll(systemOrder)
            val ranks = systemOrder.indices.joinToString(" ") { "WHEN ? THEN $it" }
            "CASE systemId $ranks ELSE ${systemOrder.size} END, ${order.sql}"
        } else {
            order.sql
        }
    return selectPagedRaw(SimpleSQLiteQuery("SELECT * FROM games$where ORDER BY $orderBy", args.toTypedArray()))
}
