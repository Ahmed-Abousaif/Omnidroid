package com.omnidroid.lib.savesync

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class ConflictStore(context: Context) {
    private val file = File(File(context.filesDir, "save-sync").apply { mkdirs() }, "conflicts.json")
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun load(): List<SaveConflict> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(SaveConflict.serializer()), file.readText())
        }.getOrDefault(emptyList())
    }

    fun save(conflicts: List<SaveConflict>) {
        file.writeText(json.encodeToString(ListSerializer(SaveConflict.serializer()), conflicts))
    }

    fun add(conflict: SaveConflict) {
        val current = load().filterNot { it.id == conflict.id }.toMutableList()
        current.add(conflict)
        save(current)
    }

    fun remove(conflictId: String) {
        save(load().filterNot { it.id == conflictId })
    }

    fun find(conflictId: String): SaveConflict? = load().firstOrNull { it.id == conflictId }
}
