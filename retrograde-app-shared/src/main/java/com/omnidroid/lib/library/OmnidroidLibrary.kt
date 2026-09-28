package com.omnidroid.lib.library

import android.content.SharedPreferences
import com.omnidroid.lib.bios.BiosManager
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.DataFile
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.library.db.entity.UnrecognizedFile
import com.omnidroid.lib.library.metadata.GameMetadataProvider
import com.omnidroid.lib.library.scan.ChdOpener
import com.omnidroid.lib.library.scan.Detection
import com.omnidroid.lib.library.scan.DetectionRules
import com.omnidroid.lib.library.scan.FolderHints
import com.omnidroid.lib.library.scan.MetadataKey
import com.omnidroid.lib.library.scan.MetadataKeyType
import com.omnidroid.lib.library.scan.ScanInput
import com.omnidroid.lib.storage.BaseStorageFile
import com.omnidroid.lib.storage.GroupedStorageFiles
import com.omnidroid.lib.storage.RomFiles
import com.omnidroid.lib.storage.StorageFile
import com.omnidroid.lib.storage.StorageProvider
import com.omnidroid.lib.storage.StorageProviderRegistry
import dagger.Lazy
import kotlinx.coroutines.flow.collect
import timber.log.Timber
import java.io.File

class OmnidroidLibrary(
    private val retrogradedb: RetrogradeDatabase,
    private val storageProviderRegistry: Lazy<StorageProviderRegistry>,
    private val gameMetadataProvider: Lazy<GameMetadataProvider>,
    private val biosManager: BiosManager,
    private val sliceStamps: SliceStampStore,
) {
    suspend fun indexLibrary() {
        val startedAtMs = System.currentTimeMillis()
        val metadata = gameMetadataProvider.get()
        val currentStamps = metadata.sliceStamps()
        val savedStamps = sliceStamps.get()
        var completed = true
        try {
            val providers = storageProviderRegistry.get().enabledProviders
            for (provider in providers) {
                try {
                    indexProvider(provider, startedAtMs, metadata, currentStamps, savedStamps)
                } catch (error: Throwable) {
                    completed = false
                    Timber.e(error, "Library indexing stopped for a storage provider")
                }
            }
            if (completed) {
                cleanUp(startedAtMs)
                sliceStamps.set(currentStamps)
            }
        } catch (error: Throwable) {
            Timber.e(error, "Library indexing stopped due to exception")
        }
        Timber.i("Library indexing completed in: ${System.currentTimeMillis() - startedAtMs} ms")
    }

    private suspend fun indexProvider(
        provider: StorageProvider,
        startedAtMs: Long,
        metadata: GameMetadataProvider,
        currentStamps: Map<String, String>,
        savedStamps: Map<String, String>,
    ) {
        provider.listBaseStorageFiles().collect { files ->
            val grouped = StorageFilesMerger.mergeDataFiles(provider, files)
            grouped.forEach { group ->
                try {
                    indexGroup(provider, group, startedAtMs, metadata, currentStamps, savedStamps)
                } catch (error: Throwable) {
                    Timber.e(error, "Failed to index ${group.primaryFile.name}")
                    keepExisting(group.primaryFile, startedAtMs)
                }
            }
        }
    }

    private suspend fun indexGroup(
        provider: StorageProvider,
        group: GroupedStorageFiles,
        startedAtMs: Long,
        metadata: GameMetadataProvider,
        currentStamps: Map<String, String>,
        savedStamps: Map<String, String>,
    ) {
        val primary = group.primaryFile
        val existing = retrogradedb.gameDao().selectByFileUri(primary.uri.toString())
        val stampChanged =
            existing != null &&
                currentStamps[existing.systemId] != null &&
                currentStamps[existing.systemId] != savedStamps[existing.systemId]
        if (existing != null && !shouldRelookup(existing, primary, stampChanged)) {
            touch(existing, primary, startedAtMs, backfill = existing.fileSize == null)
            updateDataFiles(group, existing.id, startedAtMs)
            retrogradedb.unrecognizedFileDao().deleteByUri(primary.uri.toString())
            return
        }

        if (importBiosByName(provider, primary, startedAtMs)) {
            retrogradedb.unrecognizedFileDao().deleteByUri(primary.uri.toString())
            return
        }

        var insertedId = existing?.id
        val detection =
            DetectionRules.identify(scanInput(provider, group)) { system ->
                if (existing == null && insertedId == null) {
                    insertedId = insertGame(group, system, primary.extensionlessName, null, null, startedAtMs)
                }
            }
        when (detection) {
            is Detection.Ready ->
                saveGame(
                    provider,
                    group,
                    existing,
                    insertedId,
                    detection.system,
                    detection.key,
                    startedAtMs,
                    metadata,
                )
            is Detection.HashedBin -> saveHashedBin(provider, group, existing, detection.crc, startedAtMs, metadata)
            is Detection.Arcade -> saveArcade(provider, group, existing, insertedId, detection, startedAtMs, metadata)
            is Detection.Rejected ->
                reject(provider, group, existing, insertedId, detection.reason, startedAtMs)
        }
    }

    private fun shouldRelookup(
        existing: Game,
        primary: BaseStorageFile,
        stampChanged: Boolean,
    ): Boolean {
        if (stampChanged) return true
        if (existing.fileSize == null) return false
        return existing.fileSize != primary.size || existing.fileLastModified != primary.lastModified
    }

    private suspend fun saveGame(
        provider: StorageProvider,
        group: GroupedStorageFiles,
        existing: Game?,
        insertedId: Int?,
        system: SystemID,
        key: MetadataKey?,
        startedAtMs: Long,
        metadata: GameMetadataProvider,
    ) {
        val primary = group.primaryFile
        if (existing != null && existing.systemId != system.dbname) {
            Timber.w("Keeping system ${existing.systemId} for ${primary.name}; scan found ${system.dbname}")
            touch(existing, primary, startedAtMs, backfill = true)
            updateDataFiles(group, existing.id, startedAtMs)
            return
        }
        val described =
            metadata.describe(
                system.dbname,
                primary.extensionlessName,
                key,
                primary.size,
            )
        val gameId =
            upsert(
                group,
                existing,
                insertedId,
                system,
                described.name ?: primary.extensionlessName,
                described.developer,
                described.thumbnail,
                startedAtMs,
            )
        updateDataFiles(group, gameId, startedAtMs)
        retrogradedb.unrecognizedFileDao().deleteByUri(primary.uri.toString())
    }

    private suspend fun saveHashedBin(
        provider: StorageProvider,
        group: GroupedStorageFiles,
        existing: Game?,
        crc: String,
        startedAtMs: Long,
        metadata: GameMetadataProvider,
    ) {
        val primary = group.primaryFile
        if (biosManager.matchesBiosCrc(crc)) {
            provider.getInputStream(primary.uri)?.use { stream ->
                biosManager.tryAddBiosAfter(
                    StorageFile(primary.name, primary.size, crc, null, primary.uri, primary.path, null),
                    stream,
                    startedAtMs,
                )
            }
            retrogradedb.unrecognizedFileDao().deleteByUri(primary.uri.toString())
            return
        }
        val cartridge = metadata.findCartridge(crc)
        if (cartridge?.system != null) {
            saveGame(
                provider,
                group,
                existing,
                null,
                systemByName(cartridge.system),
                MetadataKey(MetadataKeyType.CRC, crc),
                startedAtMs,
                metadata,
            )
            return
        }
        val hinted =
            FolderHints.systemAmong(
                primary.path,
                setOf(SystemID.ATARI7800, SystemID.PC_ENGINE),
            )
        if (hinted != null) {
            saveGame(provider, group, existing, null, hinted, null, startedAtMs, metadata)
        } else {
            reject(
                provider,
                group,
                existing,
                null,
                "Headerless .bin was not in the database. Put Atari 7800 and PC Engine games in a console folder.",
                startedAtMs,
            )
        }
    }

    private suspend fun saveArcade(
        provider: StorageProvider,
        group: GroupedStorageFiles,
        existing: Game?,
        insertedId: Int?,
        detection: Detection.Arcade,
        startedAtMs: Long,
        metadata: GameMetadataProvider,
    ) {
        val systemId = metadata.resolveArcade(detection.setName, detection.memberCrcs)
        if (systemId == null) {
            reject(
                provider,
                group,
                existing,
                insertedId,
                "${detection.setName} is not an FBNeo or MAME 2003-Plus set",
                startedAtMs,
            )
            return
        }
        saveGame(
            provider,
            group,
            existing,
            insertedId,
            systemByName(systemId),
            MetadataKey(MetadataKeyType.ROM_NAME, detection.setName),
            startedAtMs,
            metadata,
        )
    }

    private fun reject(
        provider: StorageProvider,
        group: GroupedStorageFiles,
        existing: Game?,
        insertedId: Int?,
        reason: String,
        startedAtMs: Long,
    ) {
        val primary = group.primaryFile
        if (existing != null) {
            Timber.w("Leaving ${primary.name} as ${existing.systemId}: $reason")
            touch(existing, primary, startedAtMs, backfill = true)
            updateDataFiles(group, existing.id, startedAtMs)
            return
        }
        insertedId?.let { id ->
            retrogradedb.gameDao().selectByIdBlocking(id)?.let { game ->
                retrogradedb.gameDao().delete(listOf(game))
            }
        }
        retrogradedb.unrecognizedFileDao().insert(
            UnrecognizedFile(
                fileUri = primary.uri.toString(),
                fileName = primary.name,
                reason = reason,
                lastIndexedAt = startedAtMs,
            ),
        )
        provider.getInputStream(primary.uri)?.use { stream ->
            biosManager.tryAddBiosAfter(
                StorageFile(primary.name, primary.size, null, null, primary.uri, primary.path, null),
                stream,
                startedAtMs,
            )
        }
    }

    private fun keepExisting(
        primary: BaseStorageFile,
        startedAtMs: Long,
    ) {
        val existing = retrogradedb.gameDao().selectByFileUri(primary.uri.toString()) ?: return
        touch(existing, primary, startedAtMs, backfill = false)
    }

    private fun touch(
        existing: Game,
        primary: BaseStorageFile,
        startedAtMs: Long,
        backfill: Boolean,
    ) {
        val updated =
            existing.copy(
                lastIndexedAt = startedAtMs,
                fileSize = if (backfill || existing.fileSize == null) primary.size else existing.fileSize,
                fileLastModified =
                    if (backfill || existing.fileLastModified == null) {
                        primary.lastModified
                    } else {
                        existing.fileLastModified
                    },
            )
        retrogradedb.gameDao().update(listOf(updated))
    }

    private fun upsert(
        group: GroupedStorageFiles,
        existing: Game?,
        insertedId: Int?,
        system: SystemID,
        title: String,
        developer: String?,
        cover: String?,
        startedAtMs: Long,
    ): Int {
        val primary = group.primaryFile
        val current = existing ?: insertedId?.let { retrogradedb.gameDao().selectByIdBlocking(it) }
        if (current != null) {
            val changed =
                current.title != title || current.developer != developer || current.coverFrontUrl != cover ||
                    current.fileSize != primary.size || current.fileLastModified != primary.lastModified
            val updated =
                current.copy(
                    title = title,
                    developer = developer,
                    coverFrontUrl = cover,
                    lastIndexedAt = startedAtMs,
                    fileSize = primary.size,
                    fileLastModified = primary.lastModified,
                )
            if (changed || current.lastIndexedAt != startedAtMs) {
                retrogradedb.gameDao().update(listOf(updated))
            }
            return current.id
        }
        return insertGame(group, system, title, developer, cover, startedAtMs)
    }

    private fun insertGame(
        group: GroupedStorageFiles,
        system: SystemID,
        title: String,
        developer: String?,
        cover: String?,
        startedAtMs: Long,
    ): Int {
        val primary = group.primaryFile
        val game =
            Game(
                fileName = primary.name,
                fileUri = primary.uri.toString(),
                title = title,
                systemId = system.dbname,
                developer = developer,
                coverFrontUrl = cover,
                lastIndexedAt = startedAtMs,
                fileSize = primary.size,
                fileLastModified = primary.lastModified,
            )
        return retrogradedb.gameDao().insert(listOf(game)).first().toInt()
    }

    private fun updateDataFiles(
        group: GroupedStorageFiles,
        gameId: Int,
        startedAtMs: Long,
    ) {
        val dataFiles =
            group.dataFiles.map { file ->
                DataFile(
                    gameId = gameId,
                    fileUri = file.uri.toString(),
                    fileName = file.name,
                    lastIndexedAt = startedAtMs,
                    path = file.path,
                )
            }
        if (dataFiles.isNotEmpty()) {
            retrogradedb.dataFileDao().insert(dataFiles)
        }
    }

    private fun importBiosByName(
        provider: StorageProvider,
        primary: BaseStorageFile,
        startedAtMs: Long,
    ): Boolean {
        if (!BiosManager.isKnownBiosName(primary.name)) return false
        val stream = provider.getInputStream(primary.uri) ?: return false
        return stream.use {
            biosManager.tryAddBiosAfter(
                StorageFile(primary.name, primary.size, null, null, primary.uri, primary.path, null),
                it,
                startedAtMs,
            )
        }
    }

    private fun systemByName(systemId: String): SystemID {
        return SystemID.entries.first { it.dbname == systemId }
    }

    private fun scanInput(
        provider: StorageProvider,
        group: GroupedStorageFiles,
    ): ScanInput {
        val primary = group.primaryFile
        val named = (group.dataFiles + primary).associateBy { it.name.lowercase() }
        return ScanInput(
            name = primary.name,
            size = primary.size,
            path = primary.path,
            open = { provider.openRandomAccess(primary.uri) },
            openNamed = { name ->
                named[name.lowercase()]?.let { provider.openRandomAccess(it.uri) }
            },
            openChd = if (primary.extension == "chd") ChdOpener { provider.openChd(primary.uri) } else null,
            openNamedChd = { name ->
                val file = named[name.lowercase()]
                if (file == null || file.extension != "chd") {
                    null
                } else {
                    ChdOpener { provider.openChd(file.uri) }
                }
            },
        )
    }

    private fun cleanUp(startedAtMs: Long) {
        kotlin.runCatching { biosManager.deleteBiosBefore(startedAtMs) }
        kotlin.runCatching {
            val games = retrogradedb.gameDao().selectByLastIndexedAtLessThan(startedAtMs)
            games.forEach { game ->
                game.customCoverPath?.let { path ->
                    runCatching { File(path.substringBefore('?')).delete() }
                }
            }
            retrogradedb.gameDao().delete(games)
        }
        kotlin.runCatching {
            val dataFiles = retrogradedb.dataFileDao().selectByLastIndexedAtLessThan(startedAtMs)
            retrogradedb.dataFileDao().delete(dataFiles)
        }
        kotlin.runCatching { retrogradedb.unrecognizedFileDao().deleteOlderThan(startedAtMs) }
    }

    fun getGameFiles(
        game: Game,
        dataFiles: List<DataFile>,
        allowVirtualFiles: Boolean,
    ): RomFiles {
        val provider = storageProviderRegistry.get()
        return provider.getProvider(game).getGameRomFiles(game, dataFiles, allowVirtualFiles)
    }
}

class SliceStampStore(
    private val preferences: SharedPreferences,
) {
    fun get(): Map<String, String> {
        val raw = preferences.getString(KEY, null) ?: return emptyMap()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split('=', limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
    }

    fun set(stamps: Map<String, String>) {
        preferences.edit().putString(KEY, stamps.entries.joinToString("\n") { "${it.key}=${it.value}" }).apply()
    }

    private companion object {
        const val KEY = "libretro_slice_stamps"
    }
}
