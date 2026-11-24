package com.omnidroid.metadata.libretrodb.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.omnidroid.metadata.libretrodb.db.dao.GameDao
import com.omnidroid.metadata.libretrodb.db.dao.SliceDao
import com.omnidroid.metadata.libretrodb.db.entity.InstalledSlice
import com.omnidroid.metadata.libretrodb.db.entity.LibretroRom
import com.omnidroid.metadata.libretrodb.db.entity.VerifiedManifest
import com.omnidroid.metadata.libretrodb.db.entity.CatalogSeed
import com.omnidroid.metadata.libretrodb.db.dao.CatalogSeedDao

@Database(
    entities = [LibretroRom::class, InstalledSlice::class, VerifiedManifest::class, CatalogSeed::class],
    version = LibretroDatabase.VERSION,
    exportSchema = false,
)
abstract class LibretroDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao

    abstract fun sliceDao(): SliceDao

    abstract fun catalogSeedDao(): CatalogSeedDao

    companion object {
        const val VERSION = 15
        val CATALOG_SEED_MIGRATION = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `catalog_seeds` (`system` TEXT NOT NULL, `lookupKey` TEXT NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`system`, `lookupKey`))")
            }
        }
    }
}
