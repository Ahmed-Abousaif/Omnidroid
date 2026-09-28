package com.omnidroid.metadata.libretrodb.detect

import androidx.room.Dao
import androidx.room.Query

@Dao
interface ArcadeSetDao {
    @Query("SELECT * FROM arcade_sets WHERE setHash = :setHash LIMIT 1")
    suspend fun find(setHash: Long): ArcadeSet?
}

@Dao
interface ArcadeMarkerDao {
    @Query("SELECT * FROM arcade_markers WHERE setHash = :setHash AND crc IN (:crcs)")
    suspend fun matching(
        setHash: Long,
        crcs: List<String>,
    ): List<ArcadeMarker>
}

@Dao
interface BinCartDao {
    @Query("SELECT * FROM bin_carts WHERE crc = :crc LIMIT 1")
    suspend fun find(crc: String): BinCart?
}
