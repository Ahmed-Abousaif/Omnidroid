package com.omnidroid.lib.library.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "unrecognized_files",
    indices = [
        Index(value = ["fileUri"], unique = true),
        Index("lastIndexedAt"),
    ],
)
data class UnrecognizedFile(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val fileUri: String,
    val fileName: String,
    val reason: String,
    val lastIndexedAt: Long,
)
