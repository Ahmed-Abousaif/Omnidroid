package com.omnidroid.lib.saves

import com.omnidroid.common.kotlin.runCatchingWithRetry
import com.omnidroid.lib.library.SystemCoreConfig
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.saves.migrators.getSavesMigrator
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SavesManager(
    private val directoriesManager: DirectoriesManager,
    private val customSaveStorage: CustomSaveStorage =
        CustomSaveStorage(directoriesManager.context, directoriesManager),
) {
    suspend fun getSaveRAM(
        game: Game,
        systemCoreConfig: SystemCoreConfig,
    ): ByteArray? =
        withContext(Dispatchers.IO) {
            val result =
                runCatchingWithRetry(FILE_ACCESS_RETRIES) {
                    val srmFileName = getSaveRAMFileName(game)

                    // 1. Sync any stashed offline saves if custom storage is now accessible
                    customSaveStorage.syncOfflineStash()

                    // 2. Try reading .srm or .sav from active storage (console subfolder first, then legacy paths)
                    val data =
                        saveSearchPaths(game, srmFileName)
                            .firstNotNullOfOrNull { path ->
                                customSaveStorage.readSave(path)?.takeIf { it.isNotEmpty() }
                            }

                    if (data != null && data.isNotEmpty()) {
                        return@runCatchingWithRetry data
                    }

                    // 3. Check legacy migrators if available
                    val savesMigrator = systemCoreConfig.getSavesMigrator()
                    val migrated = savesMigrator?.loadPreviousSaveForGame(game, directoriesManager)
                    if (migrated != null) {
                        customSaveStorage.writeSave(writeSavePath(game, srmFileName), migrated)
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
                val targetPath = writeSavePath(game, srmFileName)
                val success = customSaveStorage.writeSave(targetPath, data)
                if (!success) {
                    throw java.io.IOException("Failed to write save $srmFileName to storage")
                }
                cleanupLegacySaveIfSafe(game, srmFileName, targetPath)
                customSaveStorage.syncOfflineStash()
            }
        result.getOrThrow()
    }

    suspend fun getSaveRAMInfo(game: Game): SaveInfo =
        withContext(Dispatchers.IO) {
            val srmFileName = getSaveRAMFileName(game)
            val info =
                saveSearchPaths(game, srmFileName)
                    .firstNotNullOfOrNull { path ->
                        customSaveStorage.getSaveInfo(path)?.takeIf { it.exists }
                    }

            info ?: SaveInfo(false, 0L)
        }

    private fun saveSearchPaths(
        game: Game,
        srmFileName: String,
    ): List<String> {
        val consoleFolder = SaveConsoleFolders.forSystemId(game.systemId)
        val savFileName = getLegacySaveRAMFileName(game)
        return buildList {
            if (customSaveStorage.isConfigured()) {
                add("saves/$consoleFolder/$srmFileName")
                if (savFileName != null) add("saves/$consoleFolder/$savFileName")
                add("saves/$srmFileName")
                add(srmFileName)
                if (savFileName != null) {
                    add("saves/$savFileName")
                    add(savFileName)
                }
            } else {
                add("saves/$consoleFolder/$srmFileName")
                add("saves/$srmFileName")
                add(srmFileName)
                if (savFileName != null) {
                    add("saves/$consoleFolder/$savFileName")
                    add("saves/$savFileName")
                    add(savFileName)
                }
            }
        }
    }

    private fun writeSavePath(
        game: Game,
        srmFileName: String,
    ): String {
        val consolePath = "saves/${SaveConsoleFolders.forSystemId(game.systemId)}/$srmFileName"
        if (customSaveStorage.isConfigured()) {
            return consolePath
        }
        if (customSaveStorage.getSaveInfo(consolePath)?.exists == true) {
            return consolePath
        }
        return "saves/$srmFileName"
    }

    private suspend fun cleanupLegacySaveIfSafe(
        game: Game,
        srmFileName: String,
        writtenPath: String,
    ) {
        if (!customSaveStorage.isConfigured()) return
        if (writtenPath == "saves/$srmFileName") return
        if (customSaveStorage.isStashedLocally(writtenPath)) return
        if (customSaveStorage.getSaveInfo(writtenPath)?.exists != true) return
        if (customSaveStorage.getSaveInfo("saves/$srmFileName")?.exists != true) return
        if (customSaveStorage.canSafelyDeleteLegacySave(srmFileName, game.systemId)) {
            customSaveStorage.deleteSave("saves/$srmFileName")
        }
    }

    /** RetroArch-compatible save file naming */
    private fun getSaveRAMFileName(game: Game): String {
        if (game.systemId == "ps3") {
            val info =
                com.omnidroid.lib.library.scan.Ps3Content.read(
                    directoriesManager.context,
                    android.net.Uri.parse(game.fileUri),
                )
                    ?: error("Could not identify this PS3 game's save directory.")
            return "ps3-${info.titleId}.srm"
        }
        return "${game.fileName.substringBeforeLast(".")}.srm"
    }

    /** Battery-save naming used by standalone emulators */
    private fun getLegacySaveRAMFileName(game: Game): String? =
        if (game.systemId == "ps3") {
            null
        } else {
            "${game.fileName.substringBeforeLast(".")}.sav"
        }

    companion object {
        private const val FILE_ACCESS_RETRIES = 3
    }
}
