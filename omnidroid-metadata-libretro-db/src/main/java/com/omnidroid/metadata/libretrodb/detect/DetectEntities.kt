package com.omnidroid.metadata.libretrodb.detect

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "arcade_sets",
    indices = [Index(value = ["setHash"], unique = true)],
)
data class ArcadeSet(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val setHash: Long,
    val systems: String,
)

@Entity(
    tableName = "arcade_markers",
    indices = [Index("setHash")],
)
data class ArcadeMarker(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val setHash: Long,
    val system: String,
    val crc: String,
)

@Entity(
    tableName = "bin_carts",
    indices = [Index("crc")],
)
data class BinCart(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val crc: String,
    val system: String,
)
