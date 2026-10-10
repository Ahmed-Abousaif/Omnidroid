package com.omnidroid.app.shared.covers

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.Locale

object LibretroThumbnailFallbackInterceptor : Interceptor {
    private const val THUMBNAIL_HOST = "thumbnails.libretro.com"
    private const val PNG_EXTENSION = ".png"
    private const val MAX_POSSESSIVE_VARIANTS = 3
    private val ALTERNATE_CATEGORIES = listOf("Named_Titles", "Named_Snaps")
    private val WORD_ENDING_IN_S = Regex("(?<=[A-Za-z])s(?=\\s|\\(|$)")

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (!isLibretroThumbnail(url)) {
            return chain.proceed(request)
        }

        // 1. Primary Request (Usually Named_Boxarts)
        val primary = runCatching { chain.proceed(request) }.getOrNull()
        if (primary?.isSuccessful == true) {
            return primary
        }

        val categoryIndex = url.pathSegments.indexOfFirst { it.startsWith("Named_") }
        if (categoryIndex == -1) {
            return primary ?: throw IOException("Failed to load thumbnail: $url")
        }

        // 2. Try Alternate Categories (Named_Titles, then Named_Snaps)
        for (altCategory in ALTERNATE_CATEGORIES) {
            if (url.pathSegments[categoryIndex] == altCategory) continue
            val candidateUrl = url.newBuilder().setPathSegment(categoryIndex, altCategory).build()
            val candidateResponse = runCatching {
                chain.proceed(request.newBuilder().url(candidateUrl).build())
            }.getOrNull() ?: continue

            if (candidateResponse.isSuccessful) {
                primary?.close()
                return candidateResponse
            }
            candidateResponse.close()
        }

        // 3. Try Possessive Apostrophe Variations on Named_Boxarts
        val possessiveCandidates = possessiveVariants(url).take(MAX_POSSESSIVE_VARIANTS)
        for (candidateUrl in possessiveCandidates) {
            val response = runCatching {
                chain.proceed(request.newBuilder().url(candidateUrl).build())
            }.getOrNull() ?: continue

            if (response.isSuccessful) {
                primary?.close()
                return response
            }
            response.close()
        }

        return primary ?: throw IOException("Libretro thumbnail request failed: $url")
    }

    private fun isLibretroThumbnail(url: HttpUrl): Boolean {
        val host = url.host.lowercase(Locale.ROOT)
        return host == THUMBNAIL_HOST && url.pathSegments.any { it.startsWith("Named_") }
    }

    private fun possessiveVariants(url: HttpUrl): List<HttpUrl> {
        val fileName = url.pathSegments.lastOrNull() ?: return emptyList()
        val stem = pngStem(fileName) ?: return emptyList()
        val occurrences = wordsEndingInS(stem)
        if (occurrences.isEmpty()) return emptyList()

        return occurrences.keys.take(MAX_POSSESSIVE_VARIANTS).map { word ->
            val updatedName = insertApostrophes(stem, listOf(word), occurrences)
            url.newBuilder()
                .setPathSegment(url.pathSize - 1, updatedName)
                .build()
        }
    }

    private fun pngStem(fileName: String): String? {
        if (!fileName.endsWith(PNG_EXTENSION, ignoreCase = true)) return null
        val stem = fileName.dropLast(PNG_EXTENSION.length)
        if ('\'' in stem) return null
        return stem
    }

    private fun wordsEndingInS(stem: String): Map<String, List<Int>> {
        val occurrences = linkedMapOf<String, MutableList<Int>>()
        for (match in WORD_ENDING_IN_S.findAll(stem)) {
            val sIndex = match.range.first
            var start = sIndex
            while (start > 0 && stem[start - 1].isLetter()) start--
            val word = stem.substring(start, sIndex + 1)
            occurrences.getOrPut(word) { mutableListOf() }.add(sIndex)
        }
        return occurrences
    }

    private fun insertApostrophes(
        stem: String,
        words: List<String>,
        occurrences: Map<String, List<Int>>,
    ): String {
        val indexes = words.flatMap { occurrences[it] ?: emptyList() }.sortedDescending()
        var updated = stem
        for (idx in indexes) {
            updated = updated.replaceRange(idx, idx, "'")
        }
        return updated + PNG_EXTENSION
    }
}
