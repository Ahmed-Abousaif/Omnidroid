package com.omnidroid.lib.savesync

import android.app.Activity
import android.content.Context
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.GameSystem

abstract class SaveSyncManager {
    abstract fun getProvider(): String

    abstract fun getSettingsActivity(): Class<out Activity>?

    abstract fun isSupported(): Boolean

    abstract fun isConfigured(): Boolean

    abstract fun getLastSyncInfo(): String

    abstract fun getConfigInfo(): String

    abstract suspend fun sync(cores: Set<CoreID>)

    abstract fun computeSavesSpace(): String

    abstract fun computeStatesSpace(core: CoreID): String

    open fun getProviders(): List<CloudSaveProviderInfo> = emptyList()

    open suspend fun sync(request: SaveSyncRequest): SaveSyncResult {
        sync(request.cores)
        return SaveSyncResult()
    }

    open fun signOut(providerId: String) {}

    open fun getConflicts(): List<SaveConflict> = emptyList()

    open fun resolveConflict(
        conflictId: String,
        resolution: ConflictResolution,
    ) {}

    open fun computeRemoteUsage(providerId: String): String = ""

    fun getDisplayNameForCore(
        context: Context,
        coreID: CoreID,
    ): String {
        val systems = GameSystem.findSystemForCore(coreID)
        val systemHasMultipleCores = systems.any { it.systemCoreConfigs.size > 1 }

        val chunks =
            mutableListOf<String>().apply {
                add(systems.joinToString(", ") { context.getString(it.shortTitleResId) })

                if (systemHasMultipleCores) {
                    add(coreID.coreDisplayName)
                }

                add(computeStatesSpace(coreID))
            }

        return chunks.joinToString(" - ")
    }
}
