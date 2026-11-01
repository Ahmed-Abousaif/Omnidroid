package com.omnidroid.ext.feature.savesync

import android.app.Activity
import android.content.Context
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.savesync.CloudSaveProviderInfo
import com.omnidroid.lib.savesync.ConflictResolution
import com.omnidroid.lib.savesync.ConflictStore
import com.omnidroid.lib.savesync.SaveConflict
import com.omnidroid.lib.savesync.SaveSyncManager
import com.omnidroid.lib.savesync.SaveSyncRequest
import com.omnidroid.lib.savesync.SaveSyncResult
import com.omnidroid.lib.storage.DirectoriesManager
import java.io.File

class SaveSyncManagerImpl(
    private val appContext: Context,
    private val directoriesManager: DirectoriesManager,
) : SaveSyncManager() {
    private val conflictStore = ConflictStore(appContext)

    override fun getProvider(): String = ""

    override fun getSettingsActivity(): Class<out Activity>? = null

    override fun isSupported(): Boolean = false

    override fun isConfigured(): Boolean = false

    override fun getLastSyncInfo(): String = ""

    override fun getConfigInfo(): String = ""

    override suspend fun sync(cores: Set<CoreID>) {}

    override suspend fun sync(request: SaveSyncRequest): SaveSyncResult = SaveSyncResult()

    override fun computeSavesSpace(): String = formatSize(directoriesManager.getSavesDirectory())

    override fun computeStatesSpace(coreID: CoreID): String =
        formatSize(File(directoriesManager.getStatesDirectory(), coreID.coreName))

    override fun getProviders(): List<CloudSaveProviderInfo> = emptyList()

    override fun signOut(providerId: String) {}

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

    override fun computeRemoteUsage(providerId: String): String = ""

    private fun formatSize(directory: File): String {
        val size = directory.walkBottomUp().fold(0L) { acc, file -> acc + file.length() }
        return android.text.format.Formatter.formatShortFileSize(appContext, size)
    }
}
