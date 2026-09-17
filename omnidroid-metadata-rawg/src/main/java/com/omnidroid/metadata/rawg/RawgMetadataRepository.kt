package com.omnidroid.metadata.rawg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.Locale
import kotlin.math.min

class RawgMetadataRepository(
    private val api: RawgApi,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    suspend fun fetchForGame(
        title: String,
        systemId: String?,
    ): RawgFetchedMetadata? =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanedTitle = sanitizeTitle(title)
                if (cleanedTitle.isBlank()) return@runCatching null

                val platformId = RawgPlatformIds.forSystemId(systemId)
                val searchBody =
                    api.searchGames(
                        key = RawgConfig.API_KEY,
                        search = cleanedTitle,
                        pageSize = 5,
                        platforms = platformId?.toString(),
                    ).string()

                val page = json.decodeFromString(RawgPagedResponse.serializer(), searchBody)
                val match = pickBestMatch(cleanedTitle, page.results) ?: return@runCatching null

                // Gentle rate limiting between search and detail.
                delay(150)

                val detailsBody =
                    api.getGameDetails(id = match.id, key = RawgConfig.API_KEY).string()
                val details = json.decodeFromString(RawgGameDetails.serializer(), detailsBody)

                val description =
                    details.descriptionRaw?.takeIf { it.isNotBlank() }
                        ?: stripHtml(details.description)

                val genres =
                    details.genres.map { it.name }.filter { it.isNotBlank() }
                        .take(5)
                        .joinToString(", ")
                        .ifBlank { null }

                val publisher =
                    details.publishers.map { it.name }.filter { it.isNotBlank() }
                        .take(3)
                        .joinToString(", ")
                        .ifBlank { null }

                // RAWG's background_image is a landscape promo shot — use it only as backdrop.
                val backgroundUrl = details.backgroundImage ?: match.backgroundImage

                // Prefer a portrait screenshot for cover art when available; otherwise leave null
                // so the UI keeps Libretro Named_Boxarts / custom covers.
                delay(150)
                val coverUrl = fetchPortraitCoverUrl(details.id)?.takeIf { it != backgroundUrl }

                RawgFetchedMetadata(
                    rawgId = details.id,
                    description = description,
                    genres = genres,
                    released = details.released ?: match.released,
                    backgroundImageUrl = backgroundUrl,
                    coverImageUrl = coverUrl,
                    rating = details.rating ?: match.rating,
                    publisher = publisher,
                )
            }.onFailure { Timber.w(it, "RAWG fetch failed for %s", title) }
                .getOrNull()
        }

    private suspend fun fetchPortraitCoverUrl(gameId: Int): String? {
        return runCatching {
            val body =
                api.getGameScreenshots(
                    id = gameId,
                    key = RawgConfig.API_KEY,
                    pageSize = 8,
                ).string()
            val shots = json.decodeFromString(RawgScreenshotsResponse.serializer(), body).results
            shots
                .filter { it.image.isNotBlank() && it.height > 0 && it.width > 0 }
                .filter { it.height.toFloat() / it.width.toFloat() >= 1.2f }
                .maxByOrNull { it.height.toFloat() / it.width.toFloat() }
                ?.image
        }.getOrNull()
    }

    private fun pickBestMatch(
        title: String,
        results: List<RawgGameSummary>,
    ): RawgGameSummary? {
        if (results.isEmpty()) return null
        val normalizedQuery = normalize(title)
        return results.maxByOrNull { candidate ->
            val name = normalize(candidate.name)
            when {
                name == normalizedQuery -> 100
                name.startsWith(normalizedQuery) || normalizedQuery.startsWith(name) -> 80
                name.contains(normalizedQuery) || normalizedQuery.contains(name) -> 60
                else -> tokenOverlapScore(normalizedQuery, name)
            }
        }
    }

    private fun tokenOverlapScore(
        a: String,
        b: String,
    ): Int {
        val aTokens = a.split(' ').filter { it.length > 1 }.toSet()
        val bTokens = b.split(' ').filter { it.length > 1 }.toSet()
        if (aTokens.isEmpty() || bTokens.isEmpty()) return 0
        val overlap = aTokens.intersect(bTokens).size
        return (overlap * 50) / min(aTokens.size, bTokens.size)
    }

    private fun sanitizeTitle(title: String): String {
        return title
            .replace(Regex("""\([^)]*\)"""), " ")
            .replace(Regex("""\[[^\]]*\]"""), " ")
            .replace(Regex("""[_./\\]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.US)
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun stripHtml(html: String?): String? {
        if (html.isNullOrBlank()) return null
        return html
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""</p>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<[^>]+>"""), "")
            .replace(Regex("""&nbsp;"""), " ")
            .replace(Regex("""&amp;"""), "&")
            .replace(Regex("""&quot;"""), "\"")
            .replace(Regex("""&#39;"""), "'")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
            .ifBlank { null }
    }
}
