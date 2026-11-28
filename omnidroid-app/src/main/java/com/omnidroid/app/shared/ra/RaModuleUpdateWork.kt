package com.omnidroid.app.shared.ra

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.omnidroid.app.mobile.feature.settings.SettingsManager
import com.omnidroid.app.shared.game.GameProcessLock
import com.omnidroid.lib.ra.RaUpdateCheckOutcome
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Ensures `libomnidroid_ra.so` is cached in the background on unmetered networks when RA is enabled,
 * matching standard core downloads without touching active `:game` sessions.
 */
@HiltWorker
class RaModuleUpdateWork
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val coordinator: RetroAchievementsCoordinator,
        private val settingsManager: SettingsManager,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            val shouldCheck =
                settingsManager.raEnabled() ||
                    coordinator.isSignedIn() ||
                    coordinator.moduleManager.resolveLoadableModule() != null
            if (!shouldCheck) {
                return Result.success()
            }
            if (GameProcessLock.isHeldByAnotherProcess(applicationContext)) {
                Timber.i("Deferring RA module cleanup/check while :game process holds lock")
                return Result.retry()
            }

            return try {
                when (val outcome = coordinator.checkForModuleUpdates(force = false)) {
                    is RaUpdateCheckOutcome.Updated -> {
                        Timber.i("Updated RetroAchievements module to %s", outcome.module.version)
                    }
                    is RaUpdateCheckOutcome.Failed -> {
                        Timber.w("RA module update failed: %s", outcome.message)
                    }
                    is RaUpdateCheckOutcome.UpToDate,
                    RaUpdateCheckOutcome.DisabledOnDistributionChannel,
                    -> Unit
                }
                Result.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error checking RetroAchievements module updates")
                if (runAttemptCount < 2) Result.retry() else Result.failure()
            }
        }

        companion object {
            private const val WORK_ID = "RaModuleUpdateWork"

            fun schedule(context: Context) {
                val constraints =
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .build()
                val req =
                    OneTimeWorkRequestBuilder<RaModuleUpdateWork>()
                        .setConstraints(constraints)
                        .build()
                WorkManager
                    .getInstance(context)
                    .enqueueUniqueWork(
                        WORK_ID,
                        ExistingWorkPolicy.KEEP,
                        req,
                    )
            }
        }
    }
