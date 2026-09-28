package com.omnidroid.metadata.libretrodb.detect

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ArcadeSet::class, ArcadeMarker::class, BinCart::class],
    version = 1,
    exportSchema = false,
)
abstract class LibretroDetectDatabase : RoomDatabase() {
    abstract fun arcadeSets(): ArcadeSetDao

    abstract fun arcadeMarkers(): ArcadeMarkerDao

    abstract fun binCarts(): BinCartDao
}
