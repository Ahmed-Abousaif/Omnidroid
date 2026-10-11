package com.omnidroid.metadata.libretrodb

import com.omnidroid.lib.library.GameSystem
import com.omnidroid.metadata.libretrodb.db.LibretroDBManager
import java.net.URLEncoder

data class CoverSearchResult(
    val title: String,
    val coverUrl: String,
    val score: Int,
)

class CoverSearcher(private val dbManager: LibretroDBManager) {
    suspend fun search(
        systemId: String,
        query: String,
    ): List<CoverSearchResult> {
        val system = GameSystem.findById(systemId) ?: return emptyList()
        val cleaned = query.trim().lowercase()
        if (cleaned.length < 2) return emptyList()

        val results = dbManager.dbInstance.gameDao().searchGamesByQuery(systemId, cleaned)
        return results
            .mapNotNull { rom ->
                val rawName = rom.rawName ?: rom.name ?: return@mapNotNull null
                val url = computeCoverUrl(system, rawName) ?: return@mapNotNull null
                val score = TokenMatcher.bestScore(cleaned, rom.normalizedName ?: "")
                CoverSearchResult(rawName, url, score)
            }
            .sortedByDescending { it.score }
            .distinctBy { it.coverUrl }
    }

    companion object {
        private val THUMB_REPLACE = Regex("[&*/:`<>?\\\\|\"#%^~;\\[\\]{}@+=!\$]")
        private val CONTROL_CHARS = Regex("[\\p{Cntrl}\\u0000-\\u001F\\u007F-\\u009F]")
        private val PATH_TRAVERSAL = Regex("\\.{2,}")
        private const val MAX_TITLE_LENGTH = 200
        private const val BASE_THUMBNAIL_URL = "https://thumbnails.libretro.com"
        private const val IMAGE_TYPE = "Named_Boxarts"

        fun computeCoverUrl(
            system: GameSystem?,
            name: String?,
        ): String? {
            if (system == null || name.isNullOrBlank()) return null
            var systemName = system.libretroFullName
            if (system.libretroFullName == "FBNeo - Arcade Games") {
                systemName = "MAME"
            }
            if (!systemName.all { it.isLetterOrDigit() || it == ' ' || it == '-' }) return null
            val cleanName =
                name
                    .replace(CONTROL_CHARS, "")
                    .replace(PATH_TRAVERSAL, "_")
                    .replace(THUMB_REPLACE, "_")
                    .trim()
                    .trim('.', '_', ' ')
            if (cleanName.isBlank() || cleanName.length > MAX_TITLE_LENGTH) return null
            return "$BASE_THUMBNAIL_URL/${encode(systemName)}/$IMAGE_TYPE/${encode(cleanName)}.png"
        }

        private fun encode(value: String): String =
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
