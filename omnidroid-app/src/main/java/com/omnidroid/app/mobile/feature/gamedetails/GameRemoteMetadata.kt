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
        return GameRemoteMetadata(
            description = row.description,
            genre = row.genres,
            releaseDate = row.released,
            publisher = row.publisher,
            rating = row.rating?.let { String.format("%.1f", it) },
            coverImageUrl = row.coverImageUrl,
            backgroundImageUrl = row.backgroundImageUrl,
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
