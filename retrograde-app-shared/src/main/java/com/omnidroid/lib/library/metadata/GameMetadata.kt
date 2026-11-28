package com.omnidroid.lib.library.metadata

data class GameMetadata(
    val name: String?,
    val system: String?,
    val romName: String?,
    val developer: String?,
    val thumbnail: String?,
    val rawTitle: String? = null,
    val matchMethod: String = "filename",
    val thumbnailIndexed: Boolean = false,
)
