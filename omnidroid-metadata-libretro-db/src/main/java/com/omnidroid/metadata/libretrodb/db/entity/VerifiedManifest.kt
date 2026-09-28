package com.omnidroid.metadata.libretrodb.db.entity

import androidx.room.Entity

@Entity(tableName = "verified_manifests", primaryKeys = ["coreName", "coresVersion"])
data class VerifiedManifest(
    val coreName: String,
    val coresVersion: String,
    val manifestSha: String,
)
