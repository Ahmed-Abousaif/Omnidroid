package com.omnidroid.metadata.libretrodb.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "installed_slices")
data class InstalledSlice(
    @PrimaryKey val sliceId: String,
    val sha256: String,
    val schemaVersion: Int,
    val rows: Int,
    val sourceCore: String,
    val installedAt: Long,
    val systems: String,
)
