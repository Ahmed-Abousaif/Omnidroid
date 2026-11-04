package com.omnidroid.lib.saves

import timber.log.Timber
import java.io.File

/**
 * Saves and states are keyed by [com.omnidroid.lib.library.db.entity.Game.fileName].
 * When a ROM is renamed in-place, rename on-disk save artifacts to keep continuity.
 */
object SaveFileRenamer {
    fun renameArtifacts(
        savesDirectory: File,
        statesDirectory: File,
        statesPreviewDirectory: File,
        oldFileName: String,
        newFileName: String,
    ): Boolean {
        if (oldFileName == newFileName) return true
        val oldStem = oldFileName.substringBeforeLast('.')
        val newStem = newFileName.substringBeforeLast('.')

        val moves = mutableListOf<Pair<File, File>>()

        moves += File(savesDirectory, "$oldStem.srm") to File(savesDirectory, "$newStem.srm")

        statesDirectory.listFiles()?.filter { it.isDirectory }?.forEach { coreDir ->
            coreDir.listFiles()?.forEach { file ->
                val name = file.name
                val renamed =
                    when {
                        name == "$oldFileName.state" -> "$newFileName.state"
                        name == "$oldFileName.state.metadata" -> "$newFileName.state.metadata"
                        name.startsWith("$oldFileName.slot") ->
                            name.replaceFirst(oldFileName, newFileName)
                        else -> null
                    }
                if (renamed != null) {
                    moves += file to File(coreDir, renamed)
                }
            }
        }

        statesPreviewDirectory.listFiles()?.filter { it.isDirectory }?.forEach { coreDir ->
            coreDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("$oldFileName.slot")) {
                    val renamed = file.name.replaceFirst(oldFileName, newFileName)
                    moves += file to File(coreDir, renamed)
                }
            }
        }

        for ((from, to) in moves) {
            if (!from.exists()) continue
            if (to.exists()) {
                Timber.w("Save rename collision: ${to.absolutePath}")
                return false
            }
            if (!from.renameTo(to)) {
                Timber.w("Failed to rename save artifact ${from.name} -> ${to.name}")
                return false
            }
        }
        return true
    }
}
