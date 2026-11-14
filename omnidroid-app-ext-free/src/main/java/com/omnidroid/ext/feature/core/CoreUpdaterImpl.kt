/*
 * CoreManager.kt
 *
 * Copyright (C) 2017 Retrograde Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.omnidroid.ext.feature.core

import android.content.Context
import com.omnidroid.lib.core.CoreAssets
import com.omnidroid.lib.core.CoreLibraryLocator
import com.omnidroid.lib.core.CoreUpdater
import com.omnidroid.lib.core.GithubCoreDownloader
import com.omnidroid.lib.core.MetadataSliceInstaller
import com.omnidroid.lib.core.SyncProgressTracker
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.CancellationException
import retrofit2.Retrofit
import timber.log.Timber

class CoreUpdaterImpl(
    private val directoriesManager: DirectoriesManager,
    retrofit: Retrofit,
    private val sliceInstaller: MetadataSliceInstaller,
) : CoreUpdater {
    private val api = retrofit.create(CoreUpdater.CoreManagerApi::class.java)
    private val githubCoreDownloader = GithubCoreDownloader(directoriesManager, api)

    override suspend fun downloadCores(
        context: Context,
        coreIDs: List<CoreID>,
    ) {
        val progress = SyncProgressTracker.session()
        try {
            // Rotation/resume checks must not announce downloads for current cached binaries.
            val missing = coreIDs.distinct().filter { CoreLibraryLocator.findCurrent(context, it) == null }
            val total = missing.size
            missing.forEachIndexed { index, coreID ->
                progress.update(
                    stepText = "Downloading core: ${coreID.coreDisplayName}",
                    current = index + 1,
                    total = total,
                    isIndeterminate = false,
                )
                retrieveFile(context, coreID)
            }
            CoreAssets.install(context, coreIDs, api, directoriesManager)
            try {
                sliceInstaller.ensureSlices(context, coreIDs)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.w(error, "The optional title database can be retried from Consoles")
            }
        } finally {
            progress.clear()
        }
    }

    override suspend fun uninstallCores(
        context: Context,
        coreIDs: List<CoreID>,
    ) {
        coreIDs.distinct().forEach { coreID ->
            if (CoreLibraryLocator.findBundled(context, coreID) != null) return@forEach
            CoreLibraryLocator.findDownloaded(context, coreID)?.let { file ->
                check(file.delete() || !file.exists()) { "Could not uninstall ${coreID.coreDisplayName}" }
            }
            // Also clear versioned cores directory copies if present under files/cores.
            directoriesManager.getCoresDirectory()
                .walkTopDown()
                .filter { it.isFile && it.name == coreID.libretroFileName }
                .forEach { check(it.delete() || !it.exists()) { "Could not uninstall ${coreID.coreDisplayName}" } }
        }
        CoreAssets.remove(context, coreIDs, directoriesManager)
    }

    override suspend fun refreshAssets(
        context: Context,
        coreIDs: List<CoreID>,
    ) {
        CoreAssets.install(context, coreIDs, api, directoriesManager, force = true)
    }

    override suspend fun removeAssets(
        context: Context,
        coreIDs: List<CoreID>,
    ) {
        CoreAssets.remove(context, coreIDs, directoriesManager)
    }

    private suspend fun retrieveFile(
        context: Context,
        coreID: CoreID,
    ) {
        // Only skip the downloader for APK-bundled cores. Walking filesDir here would
        // permanently pin a stale downloaded .so and never pick up core fixes.
        CoreLibraryLocator.findBundled(context, coreID) ?: githubCoreDownloader.retrieve(coreID)
    }
}
