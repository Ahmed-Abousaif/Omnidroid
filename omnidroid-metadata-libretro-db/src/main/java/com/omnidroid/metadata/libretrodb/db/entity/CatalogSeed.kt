package com.omnidroid.metadata.libretrodb.db.entity

import androidx.room.Entity

@Entity(tableName = "catalog_seeds", primaryKeys = ["system", "lookupKey"])
data class CatalogSeed(val system: String, val lookupKey: String, val payload: String)
