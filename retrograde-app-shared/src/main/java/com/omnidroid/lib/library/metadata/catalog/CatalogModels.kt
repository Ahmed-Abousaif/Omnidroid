package com.omnidroid.lib.library.metadata.catalog

import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

@Serializable
data class CatalogIdentity(
    val key: String,
    val system: String,
    val title: String,
    val alternateTitle: String? = null,
    val region: String? = null,
    val details: Boolean = false,
    val refreshAssets: Boolean = false,
)

@Serializable
data class CatalogArtwork(
    val kind: String,
    val url: String,
    val imageId: String,
    val region: String? = null,
    val provider: String? = null,
)

@Serializable
data class ProviderLink(
    val provider: String,
    val providerGameId: String,
)

@Serializable
data class CatalogGame(
    val catalogId: String? = null,
    val provider: String,
    val providerGameId: String,
    val title: String,
    val sourceUrl: String,
    val assets: List<CatalogArtwork> = emptyList(),
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val developer: String? = null,
    val publisher: String? = null,
    val releaseDate: String? = null,
    val rating: Double? = null,
    val detailsComplete: Boolean = false,
    val fetchedAt: Long = 0,
    val matchMethod: String = "",
    val providerLinks: List<ProviderLink> = emptyList(),
    val fieldSources: Map<String, String> = emptyMap(),
) {
    fun artwork(
        kind: String,
        region: String?,
    ): CatalogArtwork? =
        assets.filter { it.kind == kind }.minByOrNull { asset ->
            when {
                region != null && asset.region.equals(region, ignoreCase = true) -> 0
                asset.region.isNullOrBlank() -> 1
                asset.region.equals("worldwide", ignoreCase = true) -> 2
                else -> 3
            }
        }
}

@Serializable
data class CatalogResolution(
    val key: String,
    val state: String,
    val match: CatalogGame? = null,
    val candidates: List<CatalogGame> = emptyList(),
    val retryAt: Long? = null,
)

@Serializable
data class CatalogBatch(
    val identities: List<CatalogIdentity>,
)

@Serializable
data class CatalogSearch(
    val identity: CatalogIdentity,
)

@Serializable
data class CatalogResponse(
    val schemaVersion: Int = 1,
    val results: List<CatalogResolution> = emptyList(),
)

@Serializable
data class CatalogChanges(
    val revision: Long,
    val hasMore: Boolean = false,
    val changes: List<CatalogResolution> = emptyList(),
)

/** Local evidence is retained, but CRC/serial values and filesystem paths are not uploaded. */
object CatalogKeys {
    private val tags = Regex("\\(([^)]*)\\)|\\[([^]]*)]")
    private val noise =
        Regex(
            "(?:usa|u|europe|e|japan|j|world|w|asia|australia|korea|china|taiwan|brazil|canada|france|germany|spain|italy|uk|en|ja|fr|de|es|it|pt|ko|zh|en(?:,\\s*[a-z]{2})+|rev(?:ision)?\\s*[\\w.]+|v\\d[\\w.]*|disc\\s*\\d+|disk\\s*\\d+|track\\s*\\d+|\\d+\\s*of\\s*\\d+|!|[abfhopt]\\d*)",
            RegexOption.IGNORE_CASE,
        )

    fun cleanTitle(value: String): String =
        tags
            .replace(value) { match ->
                val tag = match.groupValues[1].ifBlank { match.groupValues[2] }.trim()
                if (noise.matches(tag)) " " else match.value
            }.replace(Regex("\\s+"), " ")
            .trim()

    fun normalize(value: String): String =
        Normalizer
            .normalize(
                Normalizer
                    .normalize(cleanTitle(value), Normalizer.Form.NFKD)
                    .replace(Regex("(\\p{IsLatin})\\p{M}+"), "$1"),
                Normalizer.Form.NFKC,
            ).lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    // MAME and FBNeo are backend choices for the same arcade catalog platform.
    fun lookup(
        system: String,
        title: String,
    ): String =
        digest("v1|${if (system == "fbneo" || system == "mame2003plus") "arcade" else system}|${normalize(title)}")

    fun fingerprint(
        system: String,
        title: String,
        keyType: String?,
        keyValue: String?,
    ): String = digest("$system|${normalize(title)}|${keyType.orEmpty()}|${keyValue.orEmpty()}")

    fun region(rawName: String): String? {
        val labels =
            tags
                .findAll(rawName)
                .flatMap { match ->
                    match.groupValues[1]
                        .ifBlank { match.groupValues[2] }
                        .lowercase(Locale.ROOT)
                        .split(',')
                        .asSequence()
                }.map { it.trim() }
                .toSet()
        return when {
            "usa" in labels || "u" in labels -> "North America"
            "europe" in labels || "e" in labels -> "Europe"
            "japan" in labels || "j" in labels -> "Japan"
            "world" in labels || "w" in labels -> "Worldwide"
            else -> null
        }
    }

    private fun digest(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
