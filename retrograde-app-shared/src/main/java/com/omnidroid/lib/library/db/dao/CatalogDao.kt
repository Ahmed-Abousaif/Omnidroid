package com.omnidroid.lib.library.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.omnidroid.lib.library.db.entity.ArtworkAttempt
import com.omnidroid.lib.library.db.entity.CatalogLookup
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.library.db.entity.GameArtwork
import com.omnidroid.lib.library.db.entity.GameIdentity

@Dao
abstract class CatalogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putIdentity(identity: GameIdentity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun backfillIdentity(identity: GameIdentity): Long

    @Query("SELECT * FROM game_identities WHERE gameId = :gameId")
    abstract suspend fun identity(gameId: Int): GameIdentity?

    @Query(
        "SELECT * FROM games WHERE isAvailable = 1 AND isManualMatch = 0 AND (customCoverPath IS NULL OR customCoverOrigin = 'embedded') AND id > :afterId ORDER BY id LIMIT :limit",
    )
    abstract suspend fun automaticGames(
        limit: Int,
        afterId: Int,
    ): List<Game>

    @Query("SELECT * FROM catalog_lookups WHERE lookupKey = :key")
    abstract suspend fun lookup(key: String): CatalogLookup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putLookup(lookup: CatalogLookup)

    @Query("SELECT * FROM artwork_attempts WHERE url = :url")
    abstract suspend fun attempt(url: String): ArtworkAttempt?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putAttempt(attempt: ArtworkAttempt)

    @Query("SELECT * FROM game_artwork WHERE gameId = :gameId")
    abstract suspend fun artwork(gameId: Int): GameArtwork?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putArtwork(artwork: GameArtwork)

    @Query("SELECT * FROM games WHERE id = :id")
    protected abstract suspend fun game(id: Int): Game?

    @Query("UPDATE games SET coverFrontUrl = :url WHERE id = :id")
    protected abstract suspend fun setCover(
        id: Int,
        url: String,
    )

    @Query(
        "UPDATE games SET developer = :developer WHERE id = :id AND (developer IS NULL OR developer = '') AND isManualMatch = 0 AND EXISTS (SELECT 1 FROM game_identities WHERE gameId = :id AND identityKey = :identityKey)",
    )
    abstract suspend fun fillDeveloper(
        id: Int,
        identityKey: String,
        developer: String,
    )

    @Transaction
    open suspend fun applyArtwork(selection: GameArtwork): Boolean {
        val identity = identity(selection.gameId) ?: return false
        val current = game(selection.gameId) ?: return false
        if (identity.identityKey != selection.identityKey ||
            identity.system != current.systemId ||
            (identity.fileSize != null && identity.fileSize != current.fileSize) ||
            (identity.fileLastModified != null && identity.fileLastModified != current.fileLastModified) ||
            !current.isAvailable ||
            current.isManualMatch ||
            (current.customCoverPath != null && current.customCoverOrigin != "embedded")
        ) {
            return false
        }
        setCover(current.id, selection.sourceUrl)
        putArtwork(selection)
        return true
    }
}
