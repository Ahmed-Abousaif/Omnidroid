package com.omnidroid.lib.saves

import com.omnidroid.common.kotlin.runCatchingWithRetry
import com.omnidroid.lib.library.SystemCoreConfig
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.saves.migrators.getSavesMigrator
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class SavesManager(
    private val directoriesManager: DirectoriesManager,
    private val customSaveStorage: CustomSaveStorage = CustomSaveStorage(directoriesManager.context, directoriesManager),
) {
    suspend fun getSaveRAM(
        game: Game,
        systemCoreConfig: SystemCoreConfig,
    ): ByteArray? =
        withContext(Dispatchers.IO) {
            val result =
                runCatchingWithRetry(FILE_ACCESS_RETRIES) {
                    val srmFileName = getSaveRAMFileName(game)
                    val savFileName = getLegacySaveRAMFileName(game)

                    // 1. Sync any stashed offline saves if custom storage is now accessible
                    customSaveStorage.syncOfflineStash()

                    // 2. Try reading .srm or .sav from active storage
                    val data =
                        customSaveStorage.readSave("saves/$srmFileName")
                            ?: customSaveStorage.readSave(srmFileName)
                            ?: customSaveStorage.readSave("saves/$savFileName")
                            ?: customSaveStorage.readSave(savFileName)

                    if (data != null && data.isNotEmpty()) {
                        return@runCatchingWithRetry data
                    }

                    // 3. Check legacy migrators if available
                    val savesMigrator = systemCoreConfig.getSavesMigrator()
                    val migrated = savesMigrator?.loadPreviousSaveForGame(game, directoriesManager)
                    if (migrated != null) {
                        customSaveStorage.writeSave("saves/$srmFileName", migrated)
                    }
                    migrated
                }
            result.getOrThrow()
        }

    suspend fun setSaveRAM(
        game: Game,
        data: ByteArray,
    ) = withContext(Dispatchers.IO) {
        if (data.isEmpty()) return@withContext
        val result =
            runCatchingWithRetry(FILE_ACCESS_RETRIES) {
                val srmFileName = getSaveRAMFileName(game)
                val success = customSaveStorage.writeSave("saves/$srmFileName", data)
                if (!success) {
                    throw java.io.IOException("Failed to write save $srmFileName to storage")
                }
                customSaveStorage.syncOfflineStash()
            }
        result.getOrThrow()
    }

    suspend fun getSaveRAMInfo(game: Game): SaveInfo =
        withContext(Dispatchers.IO) {
            val srmFileName = getSaveRAMFileName(game)
            val savFileName = getLegacySaveRAMFileName(game)

            val info =
                customSaveStorage.getSaveInfo("saves/$srmFileName")
                    ?: customSaveStorage.getSaveInfo(srmFileName)
                    ?: customSaveStorage.getSaveInfo("saves/$savFileName")
                    ?: customSaveStorage.getSaveInfo(savFileName)

            info ?: SaveInfo(false, 0L)
        }

    /** RetroArch-compatible save file naming */
    private fun getSaveRAMFileName(game: Game): String {
        if (game.systemId == "ps3") {
            val info = com.omnidroid.lib.library.scan.Ps3Content.read(directoriesManager.context, android.net.Uri.parse(game.fileUri))
                ?: error("Could not identify this PS3 game's save directory.")
            return "ps3-${info.titleId}.srm"
        }
        return "${game.fileName.substringBeforeLast(".")}.srm"
    }

    /** Battery-save naming used by standalone emulators */
    private fun getLegacySaveRAMFileName(game: Game) = "${game.fileName.substringBeforeLast(".")}.sav"

    companion object {
        private const val FILE_ACCESS_RETRIES = 3
    }
}
