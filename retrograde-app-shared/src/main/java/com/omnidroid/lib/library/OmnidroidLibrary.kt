package com.omnidroid.lib.library

import android.content.SharedPreferences
import android.net.Uri
import com.omnidroid.lib.bios.BiosManager
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.DataFile
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.library.db.entity.UnrecognizedFile
import com.omnidroid.lib.library.metadata.GameMetadataProvider
import com.omnidroid.lib.library.metadata.LibretroTitles
import com.omnidroid.lib.library.scan.ChdOpener
import com.omnidroid.lib.library.scan.Detection
import com.omnidroid.lib.library.scan.DetectionRules
import com.omnidroid.lib.library.scan.FolderHints
import com.omnidroid.lib.library.scan.LibraryDiff
import com.omnidroid.lib.library.scan.LibraryDiffEntry
import com.omnidroid.lib.library.scan.MetadataKey
import com.omnidroid.lib.library.scan.MetadataKeyType
import com.omnidroid.lib.library.scan.PartialContentHasher
import com.omnidroid.lib.library.scan.ScanInput
import com.omnidroid.lib.library.scan.SkippedSubtreeTracker
import com.omnidroid.lib.saves.SaveFileRenamer
import com.omnidroid.lib.storage.BaseStorageFile
import com.omnidroid.lib.storage.DirectoriesManager
import com.omnidroid.lib.storage.GroupedStorageFiles
import com.omnidroid.lib.storage.RomFiles
import com.omnidroid.lib.storage.StorageFile
import com.omnidroid.lib.storage.StorageListOptions
import com.omnidroid.lib.storage.StorageProvider
import com.omnidroid.lib.storage.StorageProviderRegistry
import dagger.Lazy
import timber.log.Timber
import java.io.File

class OmnidroidLibrary(
    private val retrogradedb: RetrogradeDatabase,
    private val storageProviderRegistry: Lazy<StorageProviderRegistry>,
    private val gameMetadataProvider: Lazy<GameMetadataProvider>,
    private val biosManager: BiosManager,
    private val sliceStamps: SliceStampStore,
    private val directoriesManager: DirectoriesManager,
) {
    suspend fun indexLibrary(forceDeep: Boolean = false): LibraryIndexResult {
        val startedAtMs = System.currentTimeMillis()
        val metadata = gameMetadataProvider.get()
        val currentStamps = metadata.sliceStamps()
        val savedStamps = sliceStamps.get()
        var aggregate = LibraryIndexResult.EMPTY
        var completed = true
        try {
            val providers = storageProviderRegistry.get().enabledProviders
            for (provider in providers) {
                try {
                    val result =
                        indexProvider(
                            provider,
                            startedAtMs,
                            metadata,
                            currentStamps,
                            savedStamps,
                            forceDeep,
                        )
                    aggregate = aggregate.merge(result)
                    if (!result.folderReadable) {
                        completed = false
                    }
                } catch (error: Throwable) {
                    completed = false
                    Timber.e(error, "Library indexing stopped for a storage provider")
                    markProviderGamesUnavailable(provider)
                    aggregate = aggregate.copy(folderReadable = false)
                }
            }
            if (completed) {
                kotlin.runCatching { biosManager.deleteBiosBefore(startedAtMs) }
                kotlin.runCatching { retrogradedb.unrecognizedFileDao().deleteOlderThan(startedAtMs) }
                sliceStamps.set(currentStamps)
            }
        } catch (error: Throwable) {
            Timber.e(error, "Library indexing stopped due to exception")
        }
        Timber.i(
            "Library indexing completed in: ${System.currentTimeMillis() - startedAtMs} ms " +
                "(+${aggregate.added} ~${aggregate.modified} -${aggregate.removed} r${aggregate.renamed})",
        )
        return aggregate
    }

    /** Permanently delete games marked unavailable (user-initiated clear missing). */
    fun clearMissingGames(): Int {
        val missing = retrogradedb.gameDao().selectUnavailable()
        missing.forEach { game ->
            game.customCoverPath?.let { path ->
                runCatching { File(path.substringBefore('?')).delete() }
            }
        }
        retrogradedb.gameDao().delete(missing)
        return missing.size
    }

    private suspend fun indexProvider(
        provider: StorageProvider,
        startedAtMs: Long,
        metadata: GameMetadataProvider,
        currentStamps: Map<String, String>,
        savedStamps: Map<String, String>,
        forceDeep: Boolean,
    ): LibraryIndexResult {
        if (!provider.isLibraryRootReadable()) {
            Timber.w("Library root not readable for provider ${provider.id}; marking games unavailable")
            markProviderGamesUnavailable(provider)
            return LibraryIndexResult(folderReadable = false)
        }

        val allFiles = mutableListOf<BaseStorageFile>()
        provider.listBaseStorageFiles(StorageListOptions(forceDeep = forceDeep)).collect { files ->
            allFiles += files
        }
        val skippedIds = provider.lastSkippedDocumentIds().ifEmpty { SkippedSubtreeTracker.lastSkipped }
        val skipTracker =
            SkippedSubtreeTracker().also { tracker ->
                skippedIds.forEach { tracker.add(it) }
            }
        if (skippedIds.isNotEmpty()) {
            Timber.i("SAF skip-cache left ${skippedIds.size} directories unscanned")
        }

        val grouped = StorageFilesMerger.mergeDataFiles(provider, allFiles)
        var existing = gamesForProvider(provider)
        // Prefetch hashes for games that share size+mtime with another game (rename disambiguation).
        ensurePartialHashesForAmbiguous(provider, existing)
        existing = gamesForProvider(provider)
        val knownSystemsBefore = existing.map { it.systemId }.toSet()

        // Games under mtime-skipped subtrees stay present without a re-list.
        val existingForDiff =
            if (skippedIds.isEmpty()) {
                existing
            } else {
                existing.filterNot { skipTracker.isUnderSkipped(it.fileUri) }
            }

        val entries =
            LibraryDiff.compute(grouped, existingForDiff) { file ->
                hashFile(provider, file)
            }

        var added = 0
        var modified = 0
        var removed = 0
        var renamed = 0
        var unchanged = 0
        val newSystems = mutableSetOf<String>()

        for (entry in entries) {
            try {
                when (entry) {
                    is LibraryDiffEntry.Unchanged -> {
                        unchanged++
                        handleUnchanged(entry, startedAtMs, metadata, currentStamps, savedStamps, provider)
                    }
                    is LibraryDiffEntry.Modified -> {
                        modified++
                        indexGroup(
                            provider,
                            entry.group,
                            startedAtMs,
                            metadata,
                            currentStamps,
                            savedStamps,
                            entry.game,
                        )
                    }
                    is LibraryDiffEntry.Added -> {
                        added++
                        val beforeSystems = knownSystemsBefore + newSystems
                        indexGroup(
                            provider,
                            entry.group,
                            startedAtMs,
                            metadata,
                            currentStamps,
                            savedStamps,
                            existing = null,
                        )
                        val created = retrogradedb.gameDao().selectByFileUri(entry.group.primaryFile.uri.toString())
                        if (created != null && created.systemId !in beforeSystems) {
                            newSystems += created.systemId
                        }
                    }
                    is LibraryDiffEntry.Removed -> {
                        removed++
                        if (entry.game.isAvailable) {
                            retrogradedb.gameDao().update(
                                listOf(entry.game.copy(isAvailable = false, lastIndexedAt = startedAtMs)),
                            )
                        }
                    }
                    is LibraryDiffEntry.Renamed -> {
                        renamed++
                        applyRename(provider, entry.game, entry.group, startedAtMs)
                    }
                }
            } catch (error: Throwable) {
                Timber.e(error, "Failed to apply library diff for ${entryLabel(entry)}")
            }
        }

        return LibraryIndexResult(
            added = added,
            modified = modified,
            removed = removed,
            renamed = renamed,
            unchanged = unchanged,
            folderReadable = true,
            newSystemIds = newSystems,
        )
    }

    private suspend fun handleUnchanged(
        entry: LibraryDiffEntry.Unchanged,
        startedAtMs: Long,
        metadata: GameMetadataProvider,
        currentStamps: Map<String, String>,
        savedStamps: Map<String, String>,
        provider: StorageProvider,
    ) {
        val game = entry.game
        val stampChanged =
            currentStamps[game.systemId] != null &&
                currentStamps[game.systemId] != savedStamps[game.systemId]
        val needsTitle =
            currentStamps[game.systemId] != null &&
                (
                    game.coverFrontUrl.isNullOrBlank() ||
                        '_' in game.title ||
                        game.title == game.fileName.substringBeforeLast('.') ||
                        LibretroTitles.isArticleSorted(game.title)
                )
        if (stampChanged || needsTitle) {
            indexGroup(provider, entry.group, startedAtMs, metadata, currentStamps, savedStamps, game)
            return
        }
        val restored = !game.isAvailable
        touch(
            game,
            entry.group.primaryFile,
            startedAtMs,
            backfill = game.fileSize == null,
            forceAvailable = restored || game.isAvailable,
        )
        updateDataFiles(entry.group, game.id, startedAtMs)
        retrogradedb.unrecognizedFileDao().deleteByUri(entry.group.primaryFile.uri.toString())
    }

    private fun applyRename(
        provider: StorageProvider,
        game: Game,
        group: GroupedStorageFiles,
        startedAtMs: Long,
    ) {
        val primary = group.primaryFile
        val newFileName = primary.name
        if (game.fileName != newFileName) {
            val renamedOk =
                SaveFileRenamer.renameArtifacts(
                    directoriesManager.getSavesDirectory(),
                    directoriesManager.getStatesDirectory(),
                    directoriesManager.getStatesPreviewDirectory(),
                    game.fileName,
                    newFileName,
                )
            if (!renamedOk) {
                Timber.w("Save rename collision for ${game.fileName}; keeping URIs but marking conflict")
            }
        }
        val hash = game.partialHash ?: hashFile(provider, primary)
        val updated =
            game.copy(
                fileUri = primary.uri.toString(),
                fileName = newFileName,
                fileSize = primary.size,
                fileLastModified = primary.lastModified,
                lastIndexedAt = startedAtMs,
                isAvailable = true,
                partialHash = hash,
            )
        retrogradedb.gameDao().update(listOf(updated))
        // Replace data files for this game from the new group.
        val oldData = retrogradedb.dataFileDao().selectDataFilesForGame(game.id)
        if (oldData.isNotEmpty()) {
            retrogradedb.dataFileDao().delete(oldData)
        }
        updateDataFiles(group, game.id, startedAtMs)
        retrogradedb.unrecognizedFileDao().deleteByUri(primary.uri.toString())
    }

    private fun ensurePartialHashesForAmbiguous(
        provider: StorageProvider,
        games: List<Game>,
    ) {
        val ambiguousKeys =
            games
                .mapNotNull { game ->
                    val size = game.fileSize ?: return@mapNotNull null
                    val mtime = game.fileLastModified ?: return@mapNotNull null
                    LibraryDiff.SizeMtime(size, mtime) to game
                }
                .groupBy({ it.first }, { it.second })
                .filter { it.value.size > 1 }

        for (group in ambiguousKeys.values) {
            for (game in group) {
                if (game.partialHash != null) continue
                val file =
                    BaseStorageFile(
                        name = game.fileName,
                        size = game.fileSize ?: 0L,
                        uri = Uri.parse(game.fileUri),
                        lastModified = game.fileLastModified ?: 0L,
                    )
                val hash = hashFile(provider, file) ?: continue
                retrogradedb.gameDao().update(listOf(game.copy(partialHash = hash)))
            }
        }
    }

    private fun hashFile(
        provider: StorageProvider,
        file: BaseStorageFile,
    ): String? {
        return runCatching {
            provider.openRandomAccess(file.uri)?.use { PartialContentHasher.hash(it) }
        }.onFailure {
            Timber.w(it, "Failed to hash ${file.name}")
        }.getOrNull()
    }

    private fun gamesForProvider(provider: StorageProvider): List<Game> {
        val schemes = provider.uriSchemes.toSet()
        return retrogradedb.gameDao().selectAllBlocking().filter { game ->
            Uri.parse(game.fileUri).scheme in schemes
        }
    }

    private fun markProviderGamesUnavailable(provider: StorageProvider) {
        val ids = gamesForProvider(provider).map { it.id }
        if (ids.isNotEmpty()) {
            ids.chunked(500).forEach { chunk ->
                retrogradedb.gameDao().markUnavailable(chunk)
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
        existing: Game?,
    ) {
        val primary = group.primaryFile
        val stampChanged =
            existing != null &&
                currentStamps[existing.systemId] != null &&
                currentStamps[existing.systemId] != savedStamps[existing.systemId]
        val needsTitle =
            existing != null &&
                currentStamps[existing.systemId] != null &&
                (
                    existing.coverFrontUrl.isNullOrBlank() ||
                        '_' in existing.title ||
                        existing.title == existing.fileName.substringBeforeLast('.') ||
                        LibretroTitles.isArticleSorted(existing.title)
                )
        if (existing != null && !shouldRelookup(existing, primary, stampChanged || needsTitle)) {
            touch(existing, primary, startedAtMs, backfill = existing.fileSize == null, forceAvailable = true)
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
                    group,
                    existing,
                    insertedId,
                    detection.system,
                    detection.key,
                    startedAtMs,
                    metadata,
                    detection.archiveEntry,
                )
            is Detection.HashedBin ->
                saveHashedBin(
                    provider,
                    group,
                    existing,
                    detection.crc,
                    detection.archiveEntry,
                    startedAtMs,
                    metadata,
                )
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
        group: GroupedStorageFiles,
        existing: Game?,
        insertedId: Int?,
        system: SystemID,
        key: MetadataKey?,
        startedAtMs: Long,
        metadata: GameMetadataProvider,
        archiveEntry: String? = null,
    ) {
        val primary = group.primaryFile
        if (existing != null && existing.systemId != system.dbname) {
            Timber.w("Keeping system ${existing.systemId} for ${primary.name}; scan found ${system.dbname}")
            touch(existing, primary, startedAtMs, backfill = true, forceAvailable = true)
            updateDataFiles(group, existing.id, startedAtMs)
            return
        }
        val fallbackTitle =
            archiveEntry
                ?.substringAfterLast('/')
                ?.substringAfterLast('\\')
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }
                ?: primary.extensionlessName
        val described =
            metadata.describe(
                system.dbname,
                fallbackTitle,
                key,
                primary.size,
            )
        val gameId =
            upsert(
                group,
                existing,
                insertedId,
                system,
                described.name ?: fallbackTitle,
                described.developer,
                described.thumbnail,
                startedAtMs,
                archiveEntry,
            )
        updateDataFiles(group, gameId, startedAtMs)
        retrogradedb.unrecognizedFileDao().deleteByUri(primary.uri.toString())
    }

    private suspend fun saveHashedBin(
        provider: StorageProvider,
        group: GroupedStorageFiles,
        existing: Game?,
        crc: String,
        archiveEntry: String?,
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
                group,
                existing,
                null,
                systemByName(cartridge.system),
                MetadataKey(MetadataKeyType.CRC, crc),
                startedAtMs,
                metadata,
                archiveEntry,
            )
            return
        }
        val hinted =
            FolderHints.systemAmong(
                primary.path,
                setOf(SystemID.ATARI7800, SystemID.PC_ENGINE),
            )
        if (hinted != null) {
            saveGame(group, existing, null, hinted, null, startedAtMs, metadata, archiveEntry)
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
        val resolved = metadata.resolveArcade(detection.setName, detection.memberCrcs)
        if (resolved == null) {
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
        val (systemId, driverName) = resolved
        saveGame(
            group,
            existing,
            insertedId,
            systemByName(systemId),
            MetadataKey(MetadataKeyType.ROM_NAME, driverName),
            startedAtMs,
            metadata,
            archiveEntry = "$driverName.zip",
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
            touch(existing, primary, startedAtMs, backfill = true, forceAvailable = true)
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

    private fun touch(
        existing: Game,
        primary: BaseStorageFile,
        startedAtMs: Long,
        backfill: Boolean,
        forceAvailable: Boolean,
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
                isAvailable = if (forceAvailable) true else existing.isAvailable,
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
        archiveEntry: String? = null,
    ): Int {
        val primary = group.primaryFile
        val current = existing ?: insertedId?.let { retrogradedb.gameDao().selectByIdBlocking(it) }
        if (current != null) {
            val fileName = archiveEntry ?: current.fileName
            val changed =
                current.title != title || current.developer != developer || current.coverFrontUrl != cover ||
                    current.fileName != fileName ||
                    current.fileSize != primary.size || current.fileLastModified != primary.lastModified ||
                    !current.isAvailable
            val updated =
                current.copy(
                    fileName = fileName,
                    title = title,
                    developer = developer,
                    coverFrontUrl = cover,
                    lastIndexedAt = startedAtMs,
                    fileSize = primary.size,
                    fileLastModified = primary.lastModified,
                    isAvailable = true,
                )
            if (changed || current.lastIndexedAt != startedAtMs) {
                retrogradedb.gameDao().update(listOf(updated))
            }
            return current.id
        }
        return insertGame(group, system, title, developer, cover, startedAtMs, archiveEntry)
    }

    private fun insertGame(
        group: GroupedStorageFiles,
        system: SystemID,
        title: String,
        developer: String?,
        cover: String?,
        startedAtMs: Long,
        archiveEntry: String? = null,
    ): Int {
        val primary = group.primaryFile
        val game =
            Game(
                fileName = archiveEntry ?: primary.name,
                fileUri = primary.uri.toString(),
                title = title,
                systemId = system.dbname,
                developer = developer,
                coverFrontUrl = cover,
                lastIndexedAt = startedAtMs,
                fileSize = primary.size,
                fileLastModified = primary.lastModified,
                isAvailable = true,
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

    fun getGameFiles(
        game: Game,
        dataFiles: List<DataFile>,
        allowVirtualFiles: Boolean,
    ): RomFiles {
        val provider = storageProviderRegistry.get()
        return provider.getProvider(game).getGameRomFiles(game, dataFiles, allowVirtualFiles)
    }

    private fun entryLabel(entry: LibraryDiffEntry): String =
        when (entry) {
            is LibraryDiffEntry.Added -> entry.group.primaryFile.name
            is LibraryDiffEntry.Modified -> entry.group.primaryFile.name
            is LibraryDiffEntry.Unchanged -> entry.group.primaryFile.name
            is LibraryDiffEntry.Renamed -> entry.group.primaryFile.name
            is LibraryDiffEntry.Removed -> entry.game.fileName
        }

    private fun LibraryIndexResult.merge(other: LibraryIndexResult): LibraryIndexResult =
        LibraryIndexResult(
            added = added + other.added,
            modified = modified + other.modified,
            removed = removed + other.removed,
            renamed = renamed + other.renamed,
            unchanged = unchanged + other.unchanged,
            folderReadable = folderReadable && other.folderReadable,
            newSystemIds = newSystemIds + other.newSystemIds,
        )
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
