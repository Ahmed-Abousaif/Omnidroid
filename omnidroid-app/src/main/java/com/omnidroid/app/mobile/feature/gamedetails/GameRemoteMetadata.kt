package com.omnidroid.app.mobile.feature.gamedetails

import android.net.Uri
import com.omnidroid.lib.library.db.entity.RawgGameMetadata

data class GameRemoteMetadata(
    val description: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val publisher: String? = null,
    val rating: String? = null,
    val coverImageUrl: String? = null,
    val backgroundImageUrl: String? = null,
    val rawgId: Int? = null,
)

object GameMetadataMapper {
    fun fromRawg(row: RawgGameMetadata?): GameRemoteMetadata {
        if (row == null) return GameRemoteMetadata()
        val background = row.backgroundImageUrl?.takeIf { it.isNotBlank() }
        // Ignore cover when it was incorrectly stored as the same landscape background image.
        val cover =
            row.coverImageUrl
                ?.takeIf { it.isNotBlank() }
                ?.takeIf { it != background }
        return GameRemoteMetadata(
            description = row.description,
            genre = row.genres,
            releaseDate = row.released,
            publisher = row.publisher,
            rating = row.rating?.let { String.format("%.1f", it) },
            coverImageUrl = cover,
            backgroundImageUrl = background,
            rawgId = row.rawgId,
        )
    }

    fun trailerSearchUrl(
        title: String,
        systemName: String,
    ): String {
        val query = Uri.encode("$title $systemName trailer")
        return "https://m.youtube.com/results?search_query=$query"
    }
}
