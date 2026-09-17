package com.omnidroid.lib.library.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rawg_game_metadata",
    indices = [
        Index("gameId", unique = true),
        Index("rawgId"),
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
data class RawgGameMetadata(
    @PrimaryKey
    val gameId: Int,
    val rawgId: Int,
    val description: String?,
    val genres: String?,
    val released: String?,
    val backgroundImageUrl: String?,
    val coverImageUrl: String?,
    val rating: Double?,
    val publisher: String?,
    val updatedAt: Long,
)
