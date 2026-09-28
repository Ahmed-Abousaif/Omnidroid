package com.omnidroid.lib.library.metadata

import com.omnidroid.lib.library.scan.MetadataKey

interface GameMetadataProvider {
    val databaseVersion: Int

    suspend fun sliceStamps(): Map<String, String>

    suspend fun describe(
        systemId: String,
        fallbackTitle: String,
        key: MetadataKey?,
        fileSize: Long,
    ): GameMetadata

    suspend fun resolveArcade(
        setName: String,
        memberCrcs: List<String>,
    ): String?

    suspend fun findCartridge(crc: String): GameMetadata?
}
