package com.omnidroid.metadata.libretrodb.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "games",
    indices = [
        Index(value = ["system", "crc32"]),
        Index(value = ["system", "serial"]),
        Index(value = ["system", "code"]),
        Index(value = ["system", "romHash"]),
        Index(value = ["system", "normalizedName"]),
    ],
)
data class LibretroRom(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,
    @ColumnInfo(name = "name")
    val name: String?,
    @ColumnInfo(name = "system")
    val system: String?,
    @ColumnInfo(name = "crc32")
    val crc32: String?,
    @ColumnInfo(name = "serial")
    val serial: String?,
    @ColumnInfo(name = "code")
    val code: String?,
    @ColumnInfo(name = "size")
    val size: Long?,
    @ColumnInfo(name = "romHash")
    val romHash: Long?,
    @ColumnInfo(name = "normalizedName")
    val normalizedName: String? = null,
    @ColumnInfo(name = "rawName")
    val rawName: String? = null,
)
