package com.omnidroid.app.shared.library

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.omnidroid.app.mobile.feature.library.RegisteredSystemsStore
import com.omnidroid.app.mobile.shared.NotificationsManager
import com.omnidroid.app.utils.android.createSyncForegroundInfo
import com.omnidroid.lib.core.CoreLibraryLocator
import com.omnidroid.lib.core.ConsoleInstallPreferences
import com.omnidroid.lib.core.CoreUpdater
import com.omnidroid.lib.core.MetadataSliceInstaller
import com.omnidroid.lib.core.CoresSelection
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.metaSystemID
import kotlinx.coroutines.CancellationException
import com.omnidroid.lib.library.db.RetrogradeDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

import com.omnidroid.lib.core.SyncProgressTracker

@HiltWorker
class CoreUpdateWork
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val retrogradeDatabase: RetrogradeDatabase,
        private val coreUpdater: CoreUpdater,
        private val coresSelection: CoresSelection,
        private val sliceInstaller: MetadataSliceInstaller,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            Timber.i("Checking core existence for scanned games")

            try {
                val requiredCores = requiredCores()
                val missingCores =
                    requiredCores.filter { coreID ->
                        CoreLibraryLocator.find(applicationContext, coreID) == null
                    }

                Timber.i(
                    "Cores required=${requiredCores.map { it.coreName }}, missing=${missingCores.map { it.coreName }}",
                )

                val needsDownload = missingCores.isNotEmpty()
                val lastIndex = LastLibraryIndexResult.get()
                val noisy =
                    needsDownload ||
                        lastIndex.deltaCount >= LibraryIndexWork.SILENT_DELTA_THRESHOLD ||
                        lastIndex.newSystemIds.isNotEmpty()

                if (noisy) {
                    val notificationsManager = NotificationsManager(applicationContext)
                    val foregroundInfo =
                        createSyncForegroundInfo(
                            NotificationsManager.CORE_INSTALL_NOTIFICATION_ID,
                            notificationsManager.installingCoresNotification(),
                        )
                    setForegroundAsync(foregroundInfo)
                    SyncProgressTracker.update(
                        stepText = applicationContext.getString(com.omnidroid.R.string.library_scanning),
                        isIndeterminate = true,
                    )
                }

                coreUpdater.downloadCores(applicationContext, requiredCores)
                sliceInstaller.ensureSlices(applicationContext, requiredCores)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Core update work failed with exception: ${e.message}")
                return if (runAttemptCount < 2) Result.retry() else Result.failure()
            } finally {
                SyncProgressTracker.clear()
            }

            return Result.success()
        }

        private suspend fun requiredCores(): List<CoreID> {
            val systemsWithGames =
                retrogradeDatabase.gameDao().selectSystems()
                    .mapNotNull { systemId -> runCatching { GameSystem.findById(systemId) }.getOrNull() }

            val registeredSystems =
                RegisteredSystemsStore(applicationContext).get()
                    .flatMap { it.systemIDs }
                    .mapNotNull { systemId -> runCatching { GameSystem.findById(systemId.dbname) }.getOrNull() }

            return (systemsWithGames + registeredSystems)
                .distinctBy { it.id }
                .filter { it.metaSystemID() !in ConsoleInstallPreferences(applicationContext).uninstalled() }
                .map { coresSelection.getCoreConfigForSystem(it).coreID }
                .distinct()
        }
    }
