package com.swordfish.lemuroid.ext.feature.savesync

import android.app.Activity
import android.content.Context
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.savesync.CloudSaveProviderInfo
import com.swordfish.lemuroid.lib.savesync.ConflictResolution
import com.swordfish.lemuroid.lib.savesync.SaveConflict
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import com.swordfish.lemuroid.lib.savesync.SaveSyncRequest
import com.swordfish.lemuroid.lib.savesync.SaveSyncResult
import com.swordfish.lemuroid.lib.storage.DirectoriesManager

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
