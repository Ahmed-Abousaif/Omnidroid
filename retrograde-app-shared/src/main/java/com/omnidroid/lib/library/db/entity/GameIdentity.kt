package com.omnidroid.lib.library.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.omnidroid.lib.library.metadata.catalog.CatalogIdentity

@Entity(
    tableName = "game_identities",
    foreignKeys = [
        ForeignKey(
            entity = Game::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("lookupKey")],
)
data class GameIdentity(
    @PrimaryKey val gameId: Int,
    val identityKey: String,
    val lookupKey: String,
    val system: String,
    val title: String,
    val alternateTitle: String?,
    val region: String?,
    val keyType: String?,
    val keyValue: String?,
    val matchMethod: String,
    val libretroUrl: String?,
    val thumbnailIndexed: Boolean,
    val updatedAt: Long,
    val fileSize: Long? = null,
    val fileLastModified: Long? = null,
) {
    fun request(details: Boolean = false) = CatalogIdentity(lookupKey, system, title, alternateTitle, region, details)
}

/** Accepted matches have retryAt=0 and no expiry. Failed lookups retain their retry policy. */
@Entity(tableName = "catalog_lookups")
data class CatalogLookup(
    @PrimaryKey val lookupKey: String,
    val state: String,
    val payload: String,
    val retryAt: Long,
    val fetchedAt: Long,
    @ColumnInfo(defaultValue = "0") val assetRetryAt: Long = 0,
)

@Entity(
    tableName = "game_artwork",
    foreignKeys = [
        ForeignKey(
            entity = Game::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class GameArtwork(
    @PrimaryKey val gameId: Int,
    val identityKey: String,
    val source: String,
    val sourceUrl: String,
)

@Entity(tableName = "artwork_attempts")
data class ArtworkAttempt(
    @PrimaryKey val url: String,
    val state: String,
    val retryAt: Long,
)
