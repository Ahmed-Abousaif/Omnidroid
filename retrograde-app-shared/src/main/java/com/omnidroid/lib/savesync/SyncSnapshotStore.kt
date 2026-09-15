package com.omnidroid.lib.savesync

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SnapshotEntry(
    val localMd5: String,
    val localSize: Long,
    val localMtime: Long,
    val remoteHash: String? = null,
    val remoteSize: Long = 0L,
    val remoteMtime: Long = 0L,
)

class SyncSnapshotStore(context: Context) {
    private val root = File(context.filesDir, "save-sync/snapshots").apply { mkdirs() }
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun load(providerId: String): MutableMap<String, SnapshotEntry> {
        val file = fileFor(providerId)
        if (!file.exists()) return mutableMapOf()
        return runCatching {
            json.decodeFromString(serializer(), file.readText()).toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    fun save(
        providerId: String,
        snapshot: Map<String, SnapshotEntry>,
    ) {
        fileFor(providerId).writeText(json.encodeToString(serializer(), snapshot))
    }

    private fun fileFor(providerId: String) = File(root, "$providerId.json")

    private fun serializer() = MapSerializer(String.serializer(), SnapshotEntry.serializer())
}
