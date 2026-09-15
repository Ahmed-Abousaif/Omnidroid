package com.swordfish.lemuroid.app.shared.library

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.swordfish.lemuroid.app.mobile.feature.library.RegisteredSystemsStore
import com.swordfish.lemuroid.app.mobile.shared.NotificationsManager
import com.swordfish.lemuroid.app.utils.android.createSyncForegroundInfo
import com.swordfish.lemuroid.lib.core.CoreLibraryLocator
import com.swordfish.lemuroid.lib.core.CoreUpdater
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class CoreUpdateWork
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val retrogradeDatabase: RetrogradeDatabase,
        private val coreUpdater: CoreUpdater,
        private val coresSelection: CoresSelection,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            Timber.i("Checking core existence for scanned games")

            val notificationsManager = NotificationsManager(applicationContext)

            val foregroundInfo =
                createSyncForegroundInfo(
                    NotificationsManager.CORE_INSTALL_NOTIFICATION_ID,
                    notificationsManager.installingCoresNotification(),
                )

            setForegroundAsync(foregroundInfo)

            try {
                val requiredCores = requiredCores()
                val missingCores =
                    requiredCores.filter { coreID ->
                        CoreLibraryLocator.find(applicationContext, coreID) == null
                    }

                Timber.i(
                    "Cores required=${requiredCores.map { it.coreName }}, missing=${missingCores.map { it.coreName }}",
                )

                if (missingCores.isNotEmpty()) {
                    coreUpdater.downloadCores(applicationContext, missingCores)
                }
            } catch (e: Throwable) {
                Timber.e(e, "Core update work failed with exception: ${e.message}")
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
                .map { coresSelection.getCoreConfigForSystem(it).coreID }
                .distinct()
        }
    }
