package com.omnidroid.metadata.libretrodb.db.entity

data class CandidateRom(
    val name: String?,
    val system: String?,
    val romHash: Long? = null,
    val normalizedName: String?,
    val rawName: String?,
)
