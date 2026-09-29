package com.omnidroid.metadata.libretrodb.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.omnidroid.metadata.libretrodb.db.dao.GameDao
import com.omnidroid.metadata.libretrodb.db.dao.SliceDao
import com.omnidroid.metadata.libretrodb.db.entity.InstalledSlice
import com.omnidroid.metadata.libretrodb.db.entity.LibretroRom
import com.omnidroid.metadata.libretrodb.db.entity.VerifiedManifest

@Database(
    entities = [LibretroRom::class, InstalledSlice::class, VerifiedManifest::class],
    version = 12,
    exportSchema = false,
)
abstract class LibretroDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao

    abstract fun sliceDao(): SliceDao

    companion object {
        const val VERSION = 13
    }
}
