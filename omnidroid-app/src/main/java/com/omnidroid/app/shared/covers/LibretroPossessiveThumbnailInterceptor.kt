package com.omnidroid.app.shared.covers

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.Locale

/**
 * Libretro boxart filenames keep possessive apostrophes ("Blue's Clues - Blue's Big Musical"),
 * but a scanned ROM name often drops them ("Blues Clues - Blues Big Musical"). When the direct
 * name misses, retry by putting the apostrophe back on every copy of one word, so both "Blues"
 * become "Blue's" while a different word such as "Clues" is left alone.
 */
object LibretroPossessiveThumbnailInterceptor : Interceptor {
    private const val THUMBNAIL_HOST = "thumbnails.libretro.com"
    private const val BOXART_PATH = "/Named_Boxarts/"
    private const val PNG_EXTENSION = ".png"
    private const val MAX_VARIANTS = 8
    private val WORD_ENDING_IN_S = Regex("(?<=[A-Za-z])s(?=\\s|\\(|$)")

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isLibretroBoxart(request.url)) {
            return chain.proceed(request)
        }

        val primary = runCatching { chain.proceed(request) }.getOrNull()
        if (primary?.isSuccessful == true) {
            return primary
        }

        val recovered = firstSuccessfulVariant(chain, request.url)
        if (recovered != null) {
            primary?.close()
            return recovered
        }

        return primary ?: throw IOException("Libretro thumbnail request failed: ${request.url}")
    }

    private fun firstSuccessfulVariant(
        chain: Interceptor.Chain,
        url: HttpUrl,
    ): Response? {
        for (candidate in possessiveVariants(url)) {
            val response = runCatching {
                chain.proceed(chain.request().newBuilder().url(candidate).build())
            }.getOrNull() ?: continue
            if (response.isSuccessful) {
                return response
            }
            response.close()
        }
        return null
    }

    private fun isLibretroBoxart(url: HttpUrl): Boolean {
        val host = url.host.lowercase(Locale.ROOT)
        return host == THUMBNAIL_HOST && url.encodedPath.contains(BOXART_PATH)
    }

    private fun possessiveVariants(url: HttpUrl): List<HttpUrl> {
        val fileName = url.pathSegments.lastOrNull() ?: return emptyList()
        return possessiveFileNames(fileName).map { updatedName ->
            url.newBuilder()
                .removePathSegment(url.pathSize - 1)
                .addPathSegment(updatedName)
                .build()
        }
    }

    private fun possessiveFileNames(fileName: String): List<String> {
        val stem = pngStem(fileName) ?: return emptyList()
        val occurrences = wordsEndingInS(stem)
        if (occurrences.isEmpty()) return emptyList()

        return wordCombinations(occurrences.keys.toList()).map { words ->
            insertApostrophes(stem, words, occurrences)
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
            val word = wordEndingAt(stem, sIndex)
            occurrences.getOrPut(word) { mutableListOf() }.add(sIndex)
        }
        return occurrences
    }

    private fun wordEndingAt(stem: String, sIndex: Int): String {
        var start = sIndex
        while (start > 0 && stem[start - 1].isLetter()) {
            start--
        }
        return stem.substring(start, sIndex + 1)
    }

    /**
     * Shortest combinations first, in left-to-right word order.
     * "Blues" is tried on its own before it is combined with "Clues".
     */
    private fun wordCombinations(words: List<String>): List<List<String>> {
        val combinations = mutableListOf<List<String>>()
        var current = listOf(emptyList<String>())
        for (word in words) {
            val withWord = current.map { combination -> combination + word }
            combinations += withWord
            if (combinations.size >= MAX_VARIANTS) {
                return combinations.take(MAX_VARIANTS)
            }
            current = current + withWord
        }
        return combinations
    }

    private fun insertApostrophes(
        stem: String,
        words: List<String>,
        occurrences: Map<String, List<Int>>,
    ): String {
        val indexes = words
            .flatMap { word -> occurrences.getValue(word) }
            .sortedDescending()
        var updated = stem
        for (index in indexes) {
            updated = updated.replaceRange(index, index, "'")
        }
        return updated + PNG_EXTENSION
    }
}
