package com.omnidroid.metadata.libretrodb.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.omnidroid.metadata.libretrodb.db.dao.GameDao
import com.omnidroid.metadata.libretrodb.db.entity.LibretroRom

@Database(
    entities = [LibretroRom::class],
    version = 8,
    exportSchema = false,
)
abstract class LibretroDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
}
