package com.omnidroid.ext.feature.savesync

import android.app.Activity
import android.content.Context
import android.os.Build
import com.omnidroid.common.kotlin.calculateMd5
import com.omnidroid.ext.R
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.savesync.CloudSaveFolder
import com.omnidroid.lib.savesync.CloudSaveProviderInfo
import com.omnidroid.lib.savesync.ConflictResolution
import com.omnidroid.lib.savesync.ConflictStore
import com.omnidroid.lib.savesync.ProviderSyncStateStore
import com.omnidroid.lib.savesync.RemoteSaveFile
import com.omnidroid.lib.savesync.SaveConflict
import com.omnidroid.lib.savesync.SaveSyncFileMatcher
import com.omnidroid.lib.savesync.SaveSyncManager
import com.omnidroid.lib.savesync.SaveSyncRequest
import com.omnidroid.lib.savesync.SaveSyncResult
import com.omnidroid.lib.savesync.SnapshotEntry
import com.omnidroid.lib.savesync.SyncSnapshotStore
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SaveSyncManagerImpl(
    private val appContext: Context,
    private val directoriesManager: DirectoriesManager,
) : SaveSyncManager() {
    private val snapshotStore = SyncSnapshotStore(appContext)
    private val conflictStore = ConflictStore(appContext)
    private val stateStore = ProviderSyncStateStore(appContext)
    private val providers: List<CloudSaveProvider> =
        listOf(
            GoogleDriveCloudSaveProvider(appContext),
            OneDriveCloudSaveProvider(appContext),
            DropboxCloudSaveProvider(appContext),
        )

    override fun getProvider(): String =
        providers.filter { it.isConfigured() }.joinToString(", ") { it.displayName }
            .ifEmpty { appContext.getString(R.string.gdrive_connected_none_summary) }

    override fun getSettingsActivity(): Class<out Activity>? = null

    override fun isSupported(): Boolean = true

    override fun isConfigured(): Boolean = providers.any { it.isConfigured() }

    override fun getLastSyncInfo(): String {
        val latest = providers.maxOfOrNull { stateStore.getLastSync(it.id) } ?: 0L
        val dateString =
            if (latest > 0) {
                SimpleDateFormat.getDateTimeInstance().format(latest)
            } else {
                "-"
            }
        return appContext.getString(R.string.gdrive_last_sync_completed, dateString)
    }

    override fun getConfigInfo(): String =
        providers.filter { it.isConfigured() }.joinToString("\n") { it.getAccountLabel() }
            .ifEmpty { appContext.getString(R.string.gdrive_connected_none_summary) }

    override suspend fun sync(cores: Set<CoreID>) {
        sync(SaveSyncRequest(cores = cores))
    }

    override suspend fun sync(request: SaveSyncRequest): SaveSyncResult =
        withContext(Dispatchers.IO) {
            synchronized(SYNC_LOCK) {
                performSync(request)
            }
        }

    override fun computeSavesSpace() = formatSize(directoriesManager.getSavesDirectory())

    override fun computeStatesSpace(core: CoreID) =
        formatSize(File(directoriesManager.getStatesDirectory(), core.coreName))

    override fun getProviders(): List<CloudSaveProviderInfo> = providers.map { toInfo(it) }

    override fun signOut(providerId: String) {
        providers.firstOrNull { it.id == providerId }?.signOut()
        stateStore.clear(providerId)
    }

    override fun getConflicts(): List<SaveConflict> = conflictStore.load()

    override fun resolveConflict(
        conflictId: String,
        resolution: ConflictResolution,
    ) {
        val conflict = conflictStore.find(conflictId) ?: return
        val local = File(conflict.localPath)
        val remoteCopy = conflict.remoteCopyPath?.let { File(it) }
        when (resolution) {
            ConflictResolution.USE_LOCAL -> remoteCopy?.delete()
            ConflictResolution.USE_CLOUD -> {
                if (remoteCopy != null && remoteCopy.exists()) {
                    remoteCopy.copyTo(local, overwrite = true)
                    local.setLastModified(remoteCopy.lastModified())
                    remoteCopy.delete()
                }
            }
            ConflictResolution.KEEP_BOTH -> Unit
        }
        conflictStore.remove(conflictId)
    }

    override fun computeRemoteUsage(providerId: String): String {
        val provider = providers.firstOrNull { it.id == providerId } ?: return ""
        if (!provider.isConfigured()) return ""
        return runCatching { formatUsage(provider.computeRemoteUsage()) }.getOrDefault("")
    }

    private fun performSync(request: SaveSyncRequest): SaveSyncResult {
        val configured = providers.filter { it.isConfigured() }
        if (configured.isEmpty()) return SaveSyncResult()

        val folders = foldersFor(request)
        val results = mutableListOf<com.omnidroid.lib.savesync.ProviderSyncResult>()
        val conflicts = mutableListOf<SaveConflict>()

        configured.forEach { provider ->
            val providerResult =
                runCatching {
                    syncProvider(provider, request, folders)
                }
            providerResult.fold(
                onSuccess = { created ->
                    conflicts += created
                    stateStore.setLastError(provider.id, null)
                    stateStore.setLastSync(provider.id, System.currentTimeMillis())
                    runCatching { formatUsage(provider.computeRemoteUsage()) }
                        .onSuccess { stateStore.setLastUsage(provider.id, it) }
                    results +=
                        com.omnidroid.lib.savesync.ProviderSyncResult(
                            provider.id,
                            true,
                        )
                },
                onFailure = { error ->
                    Timber.e(error, "Error syncing ${provider.id}")
                    val message = error.message ?: error.javaClass.simpleName
                    stateStore.setLastError(provider.id, message)
                    results +=
                        com.omnidroid.lib.savesync.ProviderSyncResult(
                            provider.id,
                            false,
                            message,
                        )
                },
            )
        }

        return SaveSyncResult(results, conflicts)
    }

    private fun syncProvider(
        provider: CloudSaveProvider,
        request: SaveSyncRequest,
        folders: List<CloudSaveFolder>,
    ): List<SaveConflict> {
        val snapshot = snapshotStore.load(provider.id)
        val createdConflicts = mutableListOf<SaveConflict>()
        val uploads = mutableListOf<PendingUpload>()

        folders.forEach { folder ->
            val localRoot = localRoot(folder)
            val remoteMap = provider.listRemote(folder).associateBy { it.relativePath }
            val localMap = buildLocalFileMap(localRoot)
            val keys =
                (remoteMap.keys + localMap.keys)
                    .filter { shouldInclude(it, folder, request) }
                    .toSet()

            keys.forEach { relativePath ->
                val local = localMap[relativePath]
                val remote = remoteMap[relativePath]
                val snap = snapshot[snapshotKey(folder, relativePath)]
                when {
                    remote != null && local == null -> {
                        val dest = File(localRoot, relativePath)
                        provider.download(remote, dest)
                        snapshot[snapshotKey(folder, relativePath)] = snapshotFrom(dest, remote)
                    }
                    remote == null && local != null -> {
                        uploads += PendingUpload(folder, local, relativePath, null)
                    }
                    remote != null && local != null -> {
                        val localChanged = snap == null || !localMatches(local, snap)
                        val remoteChanged = snap == null || !remoteMatches(remote, snap)
                        when {
                            !localChanged && !remoteChanged -> Unit
                            localChanged && !remoteChanged ->
                                uploads += PendingUpload(folder, local, relativePath, remote)
                            !localChanged && remoteChanged -> {
                                provider.download(remote, local)
                                snapshot[snapshotKey(folder, relativePath)] = snapshotFrom(local, remote)
                            }
                            snap == null -> {
                                if (areDifferent(local, remote)) {
                                    if (remote.modifiedTime < local.lastModified()) {
                                        uploads += PendingUpload(folder, local, relativePath, remote)
                                    } else if (remote.modifiedTime > local.lastModified()) {
                                        provider.download(remote, local)
                                        snapshot[snapshotKey(folder, relativePath)] = snapshotFrom(local, remote)
                                    } else {
                                        snapshot[snapshotKey(folder, relativePath)] = snapshotFrom(local, remote)
                                    }
                                } else {
                                    snapshot[snapshotKey(folder, relativePath)] = snapshotFrom(local, remote)
                                }
                            }
                            else -> {
                                createdConflicts += keepBoth(provider, folder, localRoot, local, remote)
                                snapshot[snapshotKey(folder, relativePath)] = snapshotFrom(local, remote)
                            }
                        }
                    }
                }
            }
        }

        uploads.forEach { upload ->
            val uploaded = provider.upload(upload.folder, upload.local, upload.relativePath, upload.existing)
            snapshot[snapshotKey(upload.folder, upload.relativePath)] = snapshotFrom(upload.local, uploaded)
        }

        snapshotStore.save(provider.id, snapshot)
        return createdConflicts
    }

    private fun keepBoth(
        provider: CloudSaveProvider,
        folder: CloudSaveFolder,
        localRoot: File,
        local: File,
        remote: RemoteSaveFile,
    ): SaveConflict {
        val copyPath = conflictCopyPath(remote.relativePath)
        val dest = File(localRoot, copyPath)
        provider.download(remote, dest)
        val conflict =
            SaveConflict(
                id = UUID.randomUUID().toString(),
                providerId = provider.id,
                folder = folder.remoteName,
                relativePath = remote.relativePath,
                localPath = local.absolutePath,
                remoteCopyPath = dest.absolutePath,
            )
        conflictStore.add(conflict)
        return conflict
    }

    private fun shouldInclude(
        relativePath: String,
        folder: CloudSaveFolder,
        request: SaveSyncRequest,
    ): Boolean {
        // Profile files are always synced — never filtered by game name or exclusion lists
        if (folder == CloudSaveFolder.PROFILE) return true
        if (SaveSyncFileMatcher.matchesAnyGame(relativePath, request.excludedFileNames)) {
            return false
        }
        val gameFileName = request.gameFileName
        if (gameFileName != null) {
            return SaveSyncFileMatcher.matchesGame(relativePath, gameFileName)
        }
        val always = SaveSyncFileMatcher.matchesAnyGame(relativePath, request.alwaysFileNames)
        return when (folder) {
            CloudSaveFolder.PROFILE -> true
            CloudSaveFolder.SAVES -> request.includeSaves || always
            CloudSaveFolder.STATES,
            CloudSaveFolder.STATE_PREVIEWS,
            -> {
                val prefixes = request.cores.map { it.coreName }.toSet()
                val matchesCore = prefixes.isEmpty() || prefixes.any { relativePath.startsWith("$it/") || relativePath.startsWith(it) }
                (request.includeStates || always) && matchesCore
            }
        }
    }

    private fun foldersFor(request: SaveSyncRequest): List<CloudSaveFolder> {
        val folders = mutableListOf<CloudSaveFolder>()
        // Profile is always synced when any sync occurs
        folders += CloudSaveFolder.PROFILE
        if (request.includeSaves || request.alwaysFileNames.isNotEmpty() || request.gameFileName != null) {
            folders += CloudSaveFolder.SAVES
        }
        if (request.includeStates || request.includePreviews || request.alwaysFileNames.isNotEmpty() || request.gameFileName != null) {
            if (request.includeStates || request.alwaysFileNames.isNotEmpty() || request.gameFileName != null) {
                folders += CloudSaveFolder.STATES
            }
            if (request.includePreviews || request.gameFileName != null) {
                folders += CloudSaveFolder.STATE_PREVIEWS
            }
        }
        return folders.distinct()
    }

    private fun localRoot(folder: CloudSaveFolder): File =
        when (folder) {
            CloudSaveFolder.SAVES -> directoriesManager.getSavesDirectory()
            CloudSaveFolder.STATES -> directoriesManager.getStatesDirectory()
            CloudSaveFolder.STATE_PREVIEWS -> directoriesManager.getStatesPreviewDirectory()
            CloudSaveFolder.PROFILE -> directoriesManager.getProfileDirectory()
        }

    private fun buildLocalFileMap(folder: File): Map<String, File> {
        return folder
            .walkBottomUp()
            .filter { it.exists() && !it.isDirectory && it.length() > 0 }
            .associate { it.toRelativeString(folder) to it }
    }

    private fun localMatches(
        local: File,
        snap: SnapshotEntry,
    ): Boolean {
        if (local.length() == snap.localSize && local.lastModified() == snap.localMtime) return true
        return local.calculateMd5() == snap.localMd5
    }

    private fun remoteMatches(
        remote: RemoteSaveFile,
        snap: SnapshotEntry,
    ): Boolean {
        if (remote.size != snap.remoteSize) return false
        if (remote.modifiedTime != snap.remoteMtime) return false
        if (remote.hash != null && snap.remoteHash != null) return remote.hash == snap.remoteHash
        return true
    }

    private fun areDifferent(
        local: File,
        remote: RemoteSaveFile,
    ): Boolean {
        if (remote.modifiedTime == local.lastModified() && remote.size == local.length()) return false
        if (remote.size != local.length()) return true
        return remote.hash != null && remote.hash != local.calculateMd5()
    }

    private fun snapshotFrom(
        local: File,
        remote: RemoteSaveFile,
    ) = SnapshotEntry(
        localMd5 = runCatching { local.calculateMd5() }.getOrDefault(""),
        localSize = local.length(),
        localMtime = local.lastModified(),
        remoteHash = remote.hash,
        remoteSize = remote.size,
        remoteMtime = remote.modifiedTime,
    )

    private fun snapshotKey(
        folder: CloudSaveFolder,
        relativePath: String,
    ) = "${folder.remoteName}/$relativePath"

    private fun conflictCopyPath(relativePath: String): String {
        val slash = relativePath.lastIndexOf('/')
        val dir = if (slash >= 0) relativePath.substring(0, slash + 1) else ""
        val name = if (slash >= 0) relativePath.substring(slash + 1) else relativePath
        val dot = name.lastIndexOf('.')
        val base = if (dot >= 0) name.substring(0, dot) else name
        val ext = if (dot >= 0) name.substring(dot) else ""
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "$dir$base (${deviceName()}, $date)$ext"
    }

    private fun deviceName(): String =
        Build.MODEL.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "device" }

    private fun toInfo(provider: CloudSaveProvider): CloudSaveProviderInfo {
        val lastSync = stateStore.getLastSync(provider.id)
        val lastSyncLabel =
            if (lastSync > 0) {
                appContext.getString(
                    R.string.gdrive_last_sync_completed,
                    SimpleDateFormat.getDateTimeInstance().format(lastSync),
                )
            } else {
                appContext.getString(R.string.gdrive_last_sync_completed, "-")
            }
        val usage = stateStore.getLastUsage(provider.id).orEmpty()
        return CloudSaveProviderInfo(
            id = provider.id,
            displayName = provider.displayName,
            configured = provider.isConfigured(),
            accountLabel = provider.getAccountLabel(),
            remoteUsage = usage,
            lastSync = lastSyncLabel,
            lastError = stateStore.getLastError(provider.id),
            signInActivity = provider.getSignInActivity(),
        )
    }

    private fun formatSize(directory: File): String {
        val size = directory.walkBottomUp().fold(0L) { acc, file -> acc + file.length() }
        return android.text.format.Formatter.formatShortFileSize(appContext, size)
    }

    private fun formatUsage(usage: RemoteUsage): String {
        val size = android.text.format.Formatter.formatShortFileSize(appContext, usage.bytes)
        return appContext.getString(R.string.save_sync_remote_usage, size, usage.fileCount)
    }

    private data class PendingUpload(
        val folder: CloudSaveFolder,
        val local: File,
        val relativePath: String,
        val existing: RemoteSaveFile?,
    )

    companion object {
        private val SYNC_LOCK = Object()
    }
}
