package com.omnidroid.metadata.libretrodb.db.dao

import androidx.room.Dao
import androidx.room.Query

@Dao
interface CatalogSeedDao {
    @Query("SELECT payload FROM catalog_seeds WHERE system = :system AND lookupKey = :key")
    suspend fun payload(
        system: String,
        key: String,
    ): String?
}
