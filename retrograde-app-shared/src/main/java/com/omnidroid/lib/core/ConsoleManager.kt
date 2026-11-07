package com.omnidroid.lib.core

import android.content.Context
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.MetaSystemID
import com.omnidroid.lib.library.findByName

/**
 * Pure helpers for mapping consoles to cores/slices and deciding when a downloaded core file
 * can safely be deleted.
 */
object ConsoleManager {
    fun slicesFor(metaSystemID: MetaSystemID): List<SliceCatalog.Slice> {
        val systemNames = metaSystemID.systemIDs.map { it.dbname }.toSet()
        return SliceCatalog.slices.filter { slice -> slice.systems.any { it in systemNames } }
    }

    fun sliceIdsFor(metaSystemID: MetaSystemID): List<String> = slicesFor(metaSystemID).map { it.id }

    fun systemDbNames(metaSystemID: MetaSystemID): List<String> = metaSystemID.systemIDs.map { it.dbname }

    suspend fun coresFor(
        metaSystemID: MetaSystemID,
        coresSelection: CoresSelection,
    ): List<CoreID> {
        return metaSystemID.systemIDs
            .map { GameSystem.findById(it.dbname) }
            .map { coresSelection.getCoreConfigForSystem(it).coreID }
            .distinct()
    }

    fun catalogCoresFor(metaSystemID: MetaSystemID): List<CoreID> {
        return slicesFor(metaSystemID)
            .flatMap { it.cores }
            .distinct()
            .mapNotNull { findByName(it) }
    }

    fun isCorePresent(
        context: Context,
        coreID: CoreID,
    ): Boolean = CoreLibraryLocator.find(context, coreID) != null

    fun isCoreBundled(
        context: Context,
        coreID: CoreID,
    ): Boolean = CoreLibraryLocator.findBundled(context, coreID) != null

    /**
     * Returns true when [coreID] is still required by any [remainingRegistered] console other than
     * [removing], based on [SliceCatalog] core membership (covers shared cores like Genesis Plus GX).
     */
    fun isCoreStillNeeded(
        coreID: CoreID,
        removing: MetaSystemID,
        remainingRegistered: Set<MetaSystemID>,
    ): Boolean {
        val others = remainingRegistered - removing
        if (others.isEmpty()) return false
        return others.any { meta ->
            slicesFor(meta).any { coreID.coreName in it.cores }
        }
    }

    fun coresSafeToUninstall(
        context: Context,
        removing: MetaSystemID,
        cores: List<CoreID>,
        remainingRegistered: Set<MetaSystemID>,
    ): List<CoreID> {
        return cores.filter { core ->
            !isCoreBundled(context, core) &&
                !isCoreStillNeeded(core, removing, remainingRegistered)
        }
    }
}
