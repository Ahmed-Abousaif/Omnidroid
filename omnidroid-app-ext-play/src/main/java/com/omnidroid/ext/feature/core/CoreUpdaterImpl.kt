package com.omnidroid.ext.feature.core

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.play.core.ktx.hasTerminalStatus
import com.google.android.play.core.ktx.requestCancelInstall
import com.google.android.play.core.ktx.requestInstall
import com.google.android.play.core.ktx.requestProgressFlow
import com.google.android.play.core.ktx.requestSessionStates
import com.google.android.play.core.ktx.sessionId
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.omnidroid.common.coroutines.retry
import com.omnidroid.lib.core.CoreAssets
import com.omnidroid.lib.core.CoreLibraryLocator
import com.omnidroid.lib.core.CoreUpdater
import com.omnidroid.lib.core.GithubCoreDownloader
import com.omnidroid.lib.core.MetadataSliceInstaller
import com.omnidroid.lib.core.SyncProgressTracker
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import retrofit2.Retrofit
import kotlin.time.Duration.Companion.seconds

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

            if (missing.isNotEmpty() && isInstalledFromPlayStore(context)) {
                missing.forEachIndexed { index, coreID ->
                    progress.update(
                        stepText = "Installing core: ${coreID.coreDisplayName}",
                        current = index + 1,
                        total = total,
                        isIndeterminate = false,
                    )
                }
                val installManager = SplitInstallManagerFactory.create(context)
                val installSession = installCores(installManager, missing)

                try {
                    cancelPendingInstalls(installManager, installSession)
                } catch (e: Throwable) {
                    log("Error while canceling pending installs: ${e.message}")
                }
            }

            missing.filter { CoreLibraryLocator.findCurrent(context, it) == null }.forEachIndexed { index, coreID ->
                progress.update(
                    stepText = "Downloading core: ${coreID.coreDisplayName}",
                    current = index + 1,
                    total = total,
                    isIndeterminate = false,
                )
                log("Falling back to GitHub download for ${coreID.coreName}")
                githubCoreDownloader.retrieve(coreID)
            }

            CoreAssets.install(context, coreIDs, api, directoriesManager)

            try {
                sliceInstaller.ensureSlices(context, coreIDs)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                log("Error while installing metadata slices: ${e.message}")
            }

            log("downloadCores has terminated")
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

    private suspend fun installCores(
        installManager: SplitInstallManager,
        coreIDs: List<CoreID>,
    ): Int? {
        val installSession = requestCoresInstall(installManager, coreIDs) ?: return null

        try {
            waitForCompletion(installSession, installManager)
        } catch (e: Throwable) {
            log("Error while waiting for core install: ${e.message}")
        }

        return installSession
    }

    private fun computePlayModuleName(it: CoreID) = "omnidroid_core_${it.coreName}"

    private suspend fun waitForCompletion(
        sessionId: Int,
        installManager: SplitInstallManager,
    ) {
        installManager.requestProgressFlow()
            .filter { it.sessionId() == sessionId }
            .onEach { log("Session status for id $sessionId updated to $it") }
            .takeWhile { !it.hasTerminalStatus }
            .catch { log("Error while waitingForCompletion $it") }
            .onCompletion { log("Terminating waitingForCompletion for session: $sessionId") }
            .collect()
    }

    private suspend fun requestCoresInstall(
        installManager: SplitInstallManager,
        coreIDs: List<CoreID>,
    ): Int? {
        val modulesToInstall =
            coreIDs
                .map { computePlayModuleName(it) }
                .filter { it !in installManager.installedModules }

        if (modulesToInstall.isEmpty()) {
            return null
        }

        log("Starting request install for the following modules: $modulesToInstall")

        val result =
            retry(RETRY_ATTEMPTS, RETRY_DELAY) {
                installManager.requestInstall(modulesToInstall)
            }

        return result.getOrNull()
    }

    private suspend fun cancelPendingInstalls(
        installManager: SplitInstallManager,
        currentSession: Int?,
    ) {
        log("Starting cancelPendingInstalls")

        val pending =
            retry(RETRY_ATTEMPTS, RETRY_DELAY) { installManager.requestSessionStates() }
                .getOrNull() ?: return

        pending
            .asFlow()
            .filter { !it.hasTerminalStatus && it.sessionId != currentSession }
            .onEach { cancelPendingInstall(installManager, it.sessionId) }
            .catch { log("Failed to cancel pending installs: ${it.message}") }
            .collect()

        log("Terminating cancelPendingInstalls")
    }

    private suspend fun cancelPendingInstall(
        installManager: SplitInstallManager,
        sessionId: Int,
    ) {
        try {
            installManager.requestCancelInstall(sessionId)
        } catch (e: Throwable) {
            log("Failed to cancel pending install for session: $sessionId")
        }
        log("Terminated cancel for session: $sessionId")
    }

    private fun isInstalledFromPlayStore(context: Context): Boolean {
        val installer =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching {
                    context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
                }.getOrNull()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        return installer == PLAY_STORE_PACKAGE
    }

    private fun log(message: String) {
        Log.i(TAG_LOG, message)
    }

    companion object {
        private const val TAG_LOG = "CoreUpdaterImpl"
        private const val RETRY_ATTEMPTS = 5
        private val RETRY_DELAY = 2.seconds
        private const val PLAY_STORE_PACKAGE = "com.android.vending"
    }
}
