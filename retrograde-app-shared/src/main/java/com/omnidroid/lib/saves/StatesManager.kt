package com.omnidroid.lib.saves

import com.omnidroid.common.kotlin.readBytesUncompressedAtomic
import com.omnidroid.common.kotlin.readTextAtomic
import com.omnidroid.common.kotlin.runCatchingWithRetry
import com.omnidroid.common.kotlin.writeBytesCompressedAtomic
import com.omnidroid.common.kotlin.writeTextAtomic
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

class StatesManager(private val directoriesManager: DirectoriesManager) {
    private val writes = Mutex()

    data class SlotInfo(val index: Int, val saveInfo: SaveInfo)

    suspend fun getSlotSave(
        game: Game,
        coreID: CoreID,
        index: Int,
    ): SaveState? =
        withContext(Dispatchers.IO) {
            require(index >= 0 && index < Int.MAX_VALUE)
            writes.withLock { getSaveState(getSlotSaveFileName(game, index), coreID.coreName) }
        }

    suspend fun setSlotSave(
        game: Game,
        saveState: SaveState,
        coreID: CoreID,
        index: Int,
    ) = withContext(Dispatchers.IO) {
        require(index >= 0 && index < Int.MAX_VALUE)
        writes.withLock {
            setSaveState(getSlotSaveFileName(game, index), coreID.coreName, saveState)
        }
    }

    /** Allocate only after capture, and serialize allocation with writes to avoid replacing another slot. */
    suspend fun setNewSlotSave(
        game: Game,
        coreID: CoreID,
        saveState: SaveState,
    ): Int =
        withContext(Dispatchers.IO) {
            writes.withLock {
                val index = getSlotIndices(game, coreID.coreName).last() + 1
                require(index < Int.MAX_VALUE) { "No more save slots available" }
                setSaveState(getSlotSaveFileName(game, index), coreID.coreName, saveState)
                index
            }
        }

    suspend fun getAutoSaveInfo(
        game: Game,
        coreID: CoreID,
    ): SaveInfo =
        withContext(Dispatchers.IO) {
            val autoSaveFile = getStateFile(getAutoSaveFileName(game), coreID.coreName)
            val autoSaveHasData = autoSaveFile.length() > 0
            SaveInfo(autoSaveFile.exists() && autoSaveHasData, autoSaveFile.lastModified())
        }

    suspend fun getAutoSave(
        game: Game,
        coreID: CoreID,
    ) = withContext(Dispatchers.IO) {
        writes.withLock { getSaveState(getAutoSaveFileName(game), coreID.coreName) }
    }

    suspend fun setAutoSave(
        game: Game,
        coreID: CoreID,
        saveState: SaveState,
    ) = withContext(Dispatchers.IO) {
        writes.withLock {
            setSaveState(getAutoSaveFileName(game), coreID.coreName, saveState)
        }
    }

    suspend fun getSavedSlotsInfo(
        game: Game,
        coreID: CoreID,
    ): List<SlotInfo> =
        withContext(Dispatchers.IO) {
            writes.withLock {
                getSlotIndices(game, coreID.coreName).map { index ->
                    val file = getStateFile(getSlotSaveFileName(game, index), coreID.coreName)
                    SlotInfo(index, SaveInfo(file.isFile && file.length() > 0, file.lastModified()))
                }
            }
        }

    private fun getSlotIndices(
        game: Game,
        coreName: String,
    ): List<Int> {
        val directory = getStateFile(getAutoSaveFileName(game), coreName).parentFile!!
        val files = directory.listFiles() ?: throw IOException("Cannot list save states")
        val pattern = Regex("${Regex.escape(game.fileName)}\\.slot([1-9][0-9]*)")
        val savedIndices =
            files.mapNotNull { file ->
                val number = pattern.matchEntire(file.name)?.groupValues?.get(1)?.toIntOrNull()
                if (file.isFile && number != null) number - 1 else null
            }
        return ((0 until DEFAULT_STATES) + savedIndices).distinct().sorted()
    }

    private suspend fun getSaveState(
        fileName: String,
        coreName: String,
    ): SaveState? {
        return runCatchingWithRetry(FILE_ACCESS_RETRIES) {
            val saveFile = getStateFile(fileName, coreName)
            val metadataFile = getMetadataStateFile(fileName, coreName)
            if (saveFile.exists()) {
                val byteArray = saveFile.readBytesUncompressedAtomic()
                if (byteArray.isEmpty()) throw IOException("Save state is empty")
                val stateMetadata =
                    if (metadataFile.exists()) {
                        Json.decodeFromString(
                            SaveState.Metadata.serializer(),
                            metadataFile.readTextAtomic(),
                        )
                    } else {
                        // Older states did not include metadata.
                        SaveState.Metadata()
                    }
                SaveState(byteArray, stateMetadata)
            } else {
                null
            }
        }.getOrThrow()
    }

    private suspend fun setSaveState(
        fileName: String,
        coreName: String,
        saveState: SaveState,
    ) {
        require(saveState.state.isNotEmpty()) { "Cannot write an empty save state" }
        runCatchingWithRetry(FILE_ACCESS_RETRIES) {
            writeStateToDisk(fileName, coreName, saveState.state)
            writeMetadataToDisk(fileName, coreName, saveState.metadata)
        }.getOrThrow()
    }

    private fun writeMetadataToDisk(
        fileName: String,
        coreName: String,
        metadata: SaveState.Metadata,
    ) {
        val metadataFile = getMetadataStateFile(fileName, coreName)
        metadataFile.writeTextAtomic(Json.encodeToString(SaveState.Metadata.serializer(), metadata))
    }

    private fun writeStateToDisk(
        fileName: String,
        coreName: String,
        stateArray: ByteArray,
    ) {
        val saveFile = getStateFile(fileName, coreName)
        saveFile.writeBytesCompressedAtomic(stateArray)
    }

    private fun getStateFile(
        fileName: String,
        coreName: String,
    ): File {
        return File(getCoreStatesDirectory(coreName), fileName)
    }

    private fun getMetadataStateFile(
        stateFileName: String,
        coreName: String,
    ): File {
        return File(getCoreStatesDirectory(coreName), "$stateFileName.metadata")
    }

    private fun getCoreStatesDirectory(coreName: String): File {
        val directory = File(directoriesManager.getStatesDirectory(), coreName)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot access save state directory")
        return directory
    }

    private fun getAutoSaveFileName(game: Game) = "${game.fileName}.state"

    private fun getSlotSaveFileName(
        game: Game,
        index: Int,
    ) = "${game.fileName}.slot${index + 1}"

    companion object {
        const val DEFAULT_STATES = 4
        private const val FILE_ACCESS_RETRIES = 3
    }
}
