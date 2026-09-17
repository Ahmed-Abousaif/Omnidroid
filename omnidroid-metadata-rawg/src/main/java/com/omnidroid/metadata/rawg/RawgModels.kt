package com.omnidroid.metadata.rawg

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawgPagedResponse(
    val count: Int = 0,
    val results: List<RawgGameSummary> = emptyList(),
)

@Serializable
data class RawgGameSummary(
    val id: Int,
    val name: String = "",
    val slug: String? = null,
    val released: String? = null,
    @SerialName("background_image") val backgroundImage: String? = null,
    val rating: Double? = null,
)

@Serializable
data class RawgGameDetails(
    val id: Int,
    val name: String = "",
    val slug: String? = null,
    val description: String? = null,
    @SerialName("description_raw") val descriptionRaw: String? = null,
    val released: String? = null,
    @SerialName("background_image") val backgroundImage: String? = null,
    val rating: Double? = null,
    val genres: List<RawgNamedEntity> = emptyList(),
    val publishers: List<RawgNamedEntity> = emptyList(),
)

@Serializable
data class RawgScreenshotsResponse(
    val results: List<RawgScreenshot> = emptyList(),
)

@Serializable
data class RawgScreenshot(
    val id: Int = 0,
    val image: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class RawgNamedEntity(
    val id: Int = 0,
    val name: String = "",
)

/** Local DTO produced after a successful RAWG match (before Room persistence). */
data class RawgFetchedMetadata(
    val rawgId: Int,
    val description: String?,
    val genres: String?,
    val released: String?,
    val backgroundImageUrl: String?,
    val coverImageUrl: String?,
    val rating: Double?,
    val publisher: String?,
)
