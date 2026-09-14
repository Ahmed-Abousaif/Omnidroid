package com.swordfish.lemuroid.app.mobile.feature.gamedetails

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

data class GameRemoteMetadata(
    val description: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val youtubeId: String? = null,
)

object GameMetadataRepository {
    private const val WIKI_USER_AGENT =
        "Omnidroid/1.0 (https://github.com/Swordfish90/Lemuroid; Android game launcher)"
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    private val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    suspend fun fetch(
        title: String,
        systemName: String,
    ): GameRemoteMetadata =
        withContext(Dispatchers.IO) {
            runCatching {
                val page = findWikipediaPage(title, systemName) ?: return@runCatching GameRemoteMetadata()
                val description = page.optString("extract").ifBlank { page.optString("description") }.ifBlank { null }
                val wikiItem = page.optJSONObject("pageprops")?.optString("wikibase_item").orEmpty().ifBlank { null }
                val extras = wikiItem?.let { fetchWikidata(it) } ?: GameRemoteMetadata()
                val youtubeId = extras.youtubeId ?: searchYoutubeId(title, systemName)
                extras.copy(
                    description = description ?: extras.description,
                    youtubeId = youtubeId,
                )
            }.onFailure { Timber.w(it, "Failed to fetch game metadata for %s", title) }
                .getOrDefault(GameRemoteMetadata())
        }

    fun trailerEmbedHtml(youtubeId: String): String {
        val src =
            "https://www.youtube.com/embed/$youtubeId?autoplay=1&playsinline=1&rel=0&modestbranding=1&origin=https://www.youtube.com"
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
              <meta name="referrer" content="strict-origin-when-cross-origin">
              <style>
                html,body{margin:0;padding:0;height:100%;width:100%;background:#000;overflow:hidden}
                iframe{position:absolute;inset:0;width:100%;height:100%;border:0}
              </style>
            </head>
            <body>
              <iframe
                src="$src"
                referrerpolicy="strict-origin-when-cross-origin"
                allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
                allowfullscreen></iframe>
            </body>
            </html>
            """.trimIndent()
    }

    fun trailerSearchUrl(
        title: String,
        systemName: String,
    ): String {
        val query = Uri.encode("$title $systemName trailer")
        return "https://m.youtube.com/results?search_query=$query"
    }

    private fun findWikipediaPage(
        title: String,
        systemName: String,
    ): JSONObject? {
        val queries =
            listOf(
                "\"$title\" $systemName video game",
                "$title $systemName video game",
                "\"$title\" video game",
                title,
            ).filter { it.isNotBlank() }.distinct()

        queries.forEach { query ->
            searchWikipediaTitles(query).forEach { pageTitle ->
                val page = fetchWikipediaPage(pageTitle) ?: return@forEach
                if (page.optJSONObject("pageprops")?.has("disambiguation") == true) return@forEach
                val extract = page.optString("extract")
                if (extract.contains("may refer to", ignoreCase = true)) return@forEach
                if (extract.isNotBlank() || page.optString("description").isNotBlank()) {
                    return page
                }
            }
        }
        return null
    }

    private fun searchWikipediaTitles(query: String): List<String> {
        val url =
            "https://en.wikipedia.org/w/api.php?action=query&list=search&srlimit=5&format=json&formatversion=2&srsearch=${Uri.encode(query)}"
        val results =
            fetchJson(url, WIKI_USER_AGENT)
                .optJSONObject("query")
                ?.optJSONArray("search")
                ?: return emptyList()
        return (0 until results.length()).mapNotNull { index ->
            results.optJSONObject(index)?.optString("title")?.ifBlank { null }
        }
    }

    private fun fetchWikipediaPage(title: String): JSONObject? {
        val url =
            "https://en.wikipedia.org/w/api.php?action=query&prop=extracts|pageprops|description&exintro=1&explaintext=1&redirects=1&format=json&formatversion=2&ppprop=wikibase_item&titles=${Uri.encode(title)}"
        val pages =
            fetchJson(url, WIKI_USER_AGENT)
                .optJSONObject("query")
                ?.optJSONArray("pages")
                ?: return null
        val page = pages.optJSONObject(0) ?: return null
        if (page.optBoolean("missing")) return null
        return page
    }

    private fun fetchWikidata(itemId: String): GameRemoteMetadata {
        val url =
            "https://www.wikidata.org/w/api.php?action=wbgetentities&props=claims&format=json&ids=${Uri.encode(itemId)}"
        val entity =
            fetchJson(url, WIKI_USER_AGENT)
                .optJSONObject("entities")
                ?.optJSONObject(itemId)
                ?: return GameRemoteMetadata()

        val genreIds = claimIds(entity, "P136")
        val releaseDate =
            claimTimes(entity, "P577").firstOrNull()?.let(::formatWikidataDate)
        val youtubeId = claimStrings(entity, "P1651").firstOrNull()
        return GameRemoteMetadata(
            genre = resolveLabels(genreIds).take(3).joinToString(", ").ifBlank { null },
            releaseDate = releaseDate,
            youtubeId = youtubeId,
        )
    }

    private fun claimSnaks(
        entity: JSONObject,
        property: String,
    ): List<JSONObject> {
        val claims = entity.optJSONObject("claims")?.optJSONArray(property) ?: return emptyList()
        return (0 until claims.length())
            .mapNotNull { claims.optJSONObject(it) }
            .filter { it.optString("rank") != "deprecated" }
            .sortedByDescending { it.optString("rank") == "preferred" }
            .mapNotNull { it.optJSONObject("mainsnak")?.optJSONObject("datavalue") }
    }

    private fun claimIds(
        entity: JSONObject,
        property: String,
    ): List<String> =
        claimSnaks(entity, property).mapNotNull { snak ->
            snak.optJSONObject("value")?.optString("id")?.ifBlank { null }
        }

    private fun claimStrings(
        entity: JSONObject,
        property: String,
    ): List<String> =
        claimSnaks(entity, property).mapNotNull { snak ->
            snak.optString("value").ifBlank { null }
        }

    private fun claimTimes(
        entity: JSONObject,
        property: String,
    ): List<String> =
        claimSnaks(entity, property).mapNotNull { snak ->
            snak.optJSONObject("value")?.optString("time")?.ifBlank { null }
        }

    private fun resolveLabels(ids: List<String>): List<String> {
        if (ids.isEmpty()) return emptyList()
        val url =
            "https://www.wikidata.org/w/api.php?action=wbgetentities&props=labels&languages=en&format=json&ids=${ids.joinToString("|")}"
        val entities = fetchJson(url, WIKI_USER_AGENT).optJSONObject("entities") ?: return emptyList()
        return ids.mapNotNull { id ->
            entities.optJSONObject(id)
                ?.optJSONObject("labels")
                ?.optJSONObject("en")
                ?.optString("value")
                ?.ifBlank { null }
        }
    }

    private fun searchYoutubeId(
        title: String,
        systemName: String,
    ): String? {
        val query = "$title $systemName trailer"
        return runCatching { searchYoutubeHtml(query) }.getOrNull()
            ?: runCatching { searchPiped(query) }.getOrNull()
    }

    private fun searchYoutubeHtml(query: String): String? {
        val url = "https://www.youtube.com/results?search_query=${Uri.encode(query)}&sp=EgIQAQ%3D%3D"
        val body = readUrl(url, BROWSER_USER_AGENT, "text/html")
        return Regex("\"videoId\":\"([a-zA-Z0-9_-]{11})\"").find(body)?.groupValues?.get(1)
    }

    private fun searchPiped(query: String): String? {
        val endpoints =
            listOf(
                "https://pipedapi.kavin.rocks/search?filter=videos&q=",
                "https://api.piped.private.coffee/search?filter=videos&q=",
            )
        endpoints.forEach { base ->
            val items =
                runCatching { fetchJson("$base${Uri.encode(query)}", BROWSER_USER_AGENT) }
                    .getOrNull()
                    ?.optJSONArray("items")
                    ?: return@forEach
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val directId = item.optString("id").ifBlank { item.optString("videoId") }
                if (directId.matches(Regex("[a-zA-Z0-9_-]{11}"))) return directId
                val url = item.optString("url")
                val id = Regex("(?:v=|/watch\\?v=)([a-zA-Z0-9_-]{11})").find(url)?.groupValues?.get(1)
                if (id != null) return id
            }
        }
        return null
    }

    private fun formatWikidataDate(value: String): String {
        return value.trimStart('+').take(10)
    }

    private fun fetchJson(
        url: String,
        userAgent: String,
    ): JSONObject {
        val body = readUrl(url, userAgent, "application/json")
        return JSONObject(body.ifBlank { "{}" })
    }

    private fun readUrl(
        url: String,
        userAgent: String,
        accept: String,
    ): String {
        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Api-User-Agent", WIKI_USER_AGENT)
                .header("Accept", accept)
                .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} for $url")
            }
            return body
        }
    }
}
