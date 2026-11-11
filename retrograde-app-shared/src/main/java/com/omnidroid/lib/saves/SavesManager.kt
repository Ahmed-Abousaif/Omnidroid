package com.omnidroid.lib.saves

import com.omnidroid.common.kotlin.runCatchingWithRetry
import com.omnidroid.common.kotlin.writeBytesAtomic
import com.omnidroid.lib.library.SystemCoreConfig
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.saves.migrators.getSavesMigrator
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SavesManager(private val directoriesManager: DirectoriesManager) {
    suspend fun getSaveRAM(
        game: Game,
        systemCoreConfig: SystemCoreConfig,
    ): ByteArray? {
        return withContext(Dispatchers.IO) {
            val result =
                runCatchingWithRetry(FILE_ACCESS_RETRIES) {
                    val saveFile = getSaveFile(getSaveRAMFileName(game))
                    if (saveFile.exists() && saveFile.length() > 0) {
                        saveFile.readBytes()
                    } else {
                        // Standalone emulators (PizzaBoy, My Boy!, mGBA, …) often use .sav for
                        // the same SRAM payload RetroArch/Omnidroid store as .srm. Promote on
                        // first load so cloud sync and later launches use the canonical name.
                        val legacySav = getSaveFile(getLegacySaveRAMFileName(game))
                        if (legacySav.exists() && legacySav.length() > 0) {
                            val bytes = legacySav.readBytes()
                            saveFile.writeBytesAtomic(bytes)
                            bytes
                        } else {
                            val savesMigrator = systemCoreConfig.getSavesMigrator()
                            savesMigrator?.loadPreviousSaveForGame(game, directoriesManager)
                        }
                    }
                }
            result.getOrThrow()
        }
    }

    suspend fun setSaveRAM(
        game: Game,
        data: ByteArray,
    ) {
        withContext(Dispatchers.IO) {
            val result =
                runCatchingWithRetry(FILE_ACCESS_RETRIES) {
                    if (data.isEmpty()) {
                        return@runCatchingWithRetry
                    }

                    val saveFile = getSaveFile(getSaveRAMFileName(game))
                    saveFile.writeBytesAtomic(data)
                }
            result.getOrThrow()
        }
    }

    suspend fun getSaveRAMInfo(game: Game): SaveInfo {
        return withContext(Dispatchers.IO) {
            val saveFile = getSaveFile(getSaveRAMFileName(game))
            if (saveFile.exists() && saveFile.length() > 0) {
                SaveInfo(true, saveFile.lastModified())
            } else {
                val legacySav = getSaveFile(getLegacySaveRAMFileName(game))
                val fileExists = legacySav.exists() && legacySav.length() > 0
                SaveInfo(fileExists, legacySav.lastModified())
            }
        }
    }

    private suspend fun getSaveFile(fileName: String): File {
        return withContext(Dispatchers.IO) {
            val savesDirectory = directoriesManager.getSavesDirectory()
            File(savesDirectory, fileName)
        }
    }

    /** This name should make it compatible with RetroArch so that users can freely sync saves across the two application. */
    private fun getSaveRAMFileName(game: Game) = "${game.fileName.substringBeforeLast(".")}.srm"

    /** Battery-save naming used by many standalone GBA/GB emulators. */
    private fun getLegacySaveRAMFileName(game: Game) = "${game.fileName.substringBeforeLast(".")}.sav"

    companion object {
        private const val FILE_ACCESS_RETRIES = 3
    }
}
