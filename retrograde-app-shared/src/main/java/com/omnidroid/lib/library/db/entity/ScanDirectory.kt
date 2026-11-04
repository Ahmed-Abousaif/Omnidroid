package com.omnidroid.lib.library.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_directories",
    indices = [
        Index(value = ["treeUri", "documentId"], unique = true),
        Index("treeUri"),
    ],
)
data class ScanDirectory(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val treeUri: String,
    val documentId: String,
    val lastModified: Long,
    val probedAt: Long,
)
