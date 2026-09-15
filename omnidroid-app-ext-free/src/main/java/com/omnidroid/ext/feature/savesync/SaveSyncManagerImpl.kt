package com.omnidroid.ext.feature.savesync

import android.app.Activity
import android.content.Context
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.savesync.CloudSaveProviderInfo
import com.omnidroid.lib.savesync.ConflictResolution
import com.omnidroid.lib.savesync.SaveConflict
import com.omnidroid.lib.savesync.SaveSyncManager
import com.omnidroid.lib.savesync.SaveSyncRequest
import com.omnidroid.lib.savesync.SaveSyncResult
import com.omnidroid.lib.storage.DirectoriesManager

class SaveSyncManagerImpl(
    private val appContext: Context,
    private val directoriesManager: DirectoriesManager,
) : SaveSyncManager() {
    override fun getProvider(): String = ""

    override fun getSettingsActivity(): Class<out Activity>? = null

    override fun isSupported(): Boolean = false

    override fun isConfigured(): Boolean = false

    override fun getLastSyncInfo(): String = ""

    override fun getConfigInfo(): String = ""

    override suspend fun sync(cores: Set<CoreID>) {}

    override suspend fun sync(request: SaveSyncRequest): SaveSyncResult = SaveSyncResult()

    override fun computeSavesSpace() = ""

    override fun computeStatesSpace(coreID: CoreID) = ""

    override fun getProviders(): List<CloudSaveProviderInfo> = emptyList()

    override fun signOut(providerId: String) {}

    override fun getConflicts(): List<SaveConflict> = emptyList()

    override fun resolveConflict(
        conflictId: String,
        resolution: ConflictResolution,
    ) {}

    override fun computeRemoteUsage(providerId: String) = ""
}
