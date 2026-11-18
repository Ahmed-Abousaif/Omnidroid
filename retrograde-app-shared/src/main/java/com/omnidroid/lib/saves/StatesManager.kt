package com.omnidroid.lib.saves

import com.omnidroid.common.kotlin.runCatchingWithRetry
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class StatesManager(
    private val directoriesManager: DirectoriesManager,
    private val customSaveStorage: CustomSaveStorage = CustomSaveStorage(directoriesManager.context, directoriesManager),
) {
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
            val autoSaveFileName = getAutoSaveFileName(game)
            findStateInfo(autoSaveFileName, coreID.coreName)
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
                    val slotFileName = getSlotSaveFileName(game, index)
                    val info = findStateInfo(slotFileName, coreID.coreName)
                    SlotInfo(index, info)
                }
            }
        }

    private fun getSlotIndices(
        game: Game,
        coreName: String,
    ): List<Int> {
        val pattern = Regex("${Regex.escape(game.fileName)}\\.slot([1-9][0-9]*)")
        val savedIndices = mutableListOf<Int>()

        val files =
            customSaveStorage.listSaveFiles("states/$coreName") +
                customSaveStorage.listSaveFiles("states") +
                customSaveStorage.listSaveFiles()
        files.forEach { name ->
            val number = pattern.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()
            if (number != null) {
                savedIndices.add(number - 1)
            }
        }
        return ((0 until DEFAULT_STATES) + savedIndices).distinct().sorted()
    }

    private suspend fun getSaveState(
        fileName: String,
        coreName: String,
    ): SaveState? {
        return runCatchingWithRetry(FILE_ACCESS_RETRIES) {
            customSaveStorage.syncOfflineStash()

            val paths =
                listOf(
                    "states/$coreName/$fileName",
                    "states/$fileName",
                    fileName,
                )
            var foundPath: String? = null
            var rawBytes: ByteArray? = null
            for (path in paths) {
                val bytes = customSaveStorage.readSave(path)
                if (bytes != null && bytes.isNotEmpty()) {
                    foundPath = path
                    rawBytes = bytes
                    break
                }
            }
            if (rawBytes == null) return@runCatchingWithRetry null

            val uncompressed = rawBytes.decompressGzipIfNeeded()
            if (uncompressed.isEmpty()) throw IOException("Save state is empty")

            val metaBytes =
                customSaveStorage.readSave("$foundPath.metadata")
                    ?: customSaveStorage.readSave("states/$coreName/$fileName.metadata")
            val metadata =
                if (metaBytes != null && metaBytes.isNotEmpty()) {
                    Json.decodeFromString(SaveState.Metadata.serializer(), metaBytes.decodeToString())
                } else {
                    SaveState.Metadata()
                }
            SaveState(uncompressed, metadata)
        }.getOrThrow()
    }

    private suspend fun setSaveState(
        fileName: String,
        coreName: String,
        saveState: SaveState,
    ) {
        require(saveState.state.isNotEmpty()) { "Cannot write an empty save state" }
        runCatchingWithRetry(FILE_ACCESS_RETRIES) {
            val compressed = saveState.state.compressGzip()
            val metadataJson = Json.encodeToString(SaveState.Metadata.serializer(), saveState.metadata)
            val statePath = "states/$coreName/$fileName"
            val stateOk = customSaveStorage.writeSave(statePath, compressed)
            val metaOk = customSaveStorage.writeSave("$statePath.metadata", metadataJson.encodeToByteArray())
            if (!stateOk || !metaOk) {
                throw IOException("Failed to write save state $fileName")
            }
            customSaveStorage.syncOfflineStash()
        }.getOrThrow()
    }

    private fun findStateInfo(
        fileName: String,
        coreName: String,
    ): SaveInfo {
        val paths =
            listOf(
                "states/$coreName/$fileName",
                "states/$fileName",
                fileName,
            )
        for (path in paths) {
            val info = customSaveStorage.getSaveInfo(path)
            if (info != null && info.exists) {
                return info
            }
        }
        return SaveInfo(false, 0L)
    }

    private fun ByteArray.compressGzip(): ByteArray =
        ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { it.write(this) }
            baos.toByteArray()
        }

    private fun ByteArray.decompressGzipIfNeeded(): ByteArray {
        if (size >= 2 && this[0] == 0x1f.toByte() && this[1] == 0x8b.toByte()) {
            return GZIPInputStream(ByteArrayInputStream(this)).use { it.readBytes() }
        }
        return this
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
