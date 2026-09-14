package com.swordfish.lemuroid.app.shared.savesync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.app.mobile.shared.NotificationsManager
import com.swordfish.lemuroid.app.utils.android.createSyncForegroundInfo
import com.swordfish.lemuroid.lib.injection.AndroidWorkerInjection
import com.swordfish.lemuroid.lib.injection.WorkerKey
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.findByName
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.savesync.GameCloudSyncPreferences
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import com.swordfish.lemuroid.lib.savesync.SaveSyncRequest
import dagger.Binds
import dagger.android.AndroidInjector
import dagger.multibindings.IntoMap
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SaveSyncWork(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {
    @Inject
    lateinit var saveSyncManager: SaveSyncManager

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    override suspend fun doWork(): Result {
        AndroidWorkerInjection.inject(this)

        if (!shouldPerformSaveSync()) {
            return Result.success()
        }

        displayNotification()

        val coresToSync =
            settingsManager.syncStatesCores()
                .mapNotNull { findByName(it) }
                .toSet()

        val gameFileName = inputData.getString(GAME_FILE_NAME)
        val partition =
            GameCloudSyncPreferences(applicationContext)
                .partitionGames(retrogradeDb.gameDao().selectAll())

        val includeSaves = settingsManager.syncSaves() || gameFileName != null
        val includeStates = coresToSync.isNotEmpty() || gameFileName != null

        try {
            saveSyncManager.sync(
                SaveSyncRequest(
                    cores = coresToSync,
                    gameFileName = gameFileName,
                    includeSaves = includeSaves,
                    includeStates = includeStates,
                    includePreviews = includeStates,
                    excludedFileNames = partition.excludedFileNames,
                    alwaysFileNames = partition.alwaysFileNames,
                ),
            )
        } catch (e: Throwable) {
            Timber.e(e, "Error in saves sync")
        }

        return Result.success()
    }

    private suspend fun shouldPerformSaveSync(): Boolean {
        val conditionsToRunThisWork =
            flow {
                emit(saveSyncManager.isSupported())
                emit(saveSyncManager.isConfigured())
                emit(shouldScheduleThisSync())
            }

        return conditionsToRunThisWork.firstOrNull { !it } ?: true
    }

    private suspend fun shouldScheduleThisSync(): Boolean {
        val isAutoSync = inputData.getBoolean(IS_AUTO, false)
        val isGameSync = inputData.getString(GAME_FILE_NAME) != null
        val isManualSync = !isAutoSync && !isGameSync
        val gameAlways =
            inputData.getString(GAME_FILE_NAME)?.let { fileName ->
                retrogradeDb.gameDao().selectAll().any { it.fileName == fileName } &&
                    GameCloudSyncPreferences(applicationContext).shouldSyncGame(
                        retrogradeDb.gameDao().selectAll().first { it.fileName == fileName },
                        settingsManager.syncSaves(),
                    )
            } ?: true
        return when {
            isGameSync -> gameAlways
            isAutoSync ->
                settingsManager.autoSaveSync() &&
                    (
                        settingsManager.syncSaves() ||
                            GameCloudSyncPreferences(applicationContext)
                                .partitionGames(retrogradeDb.gameDao().selectAll())
                                .alwaysFileNames
                                .isNotEmpty()
                        )
            isManualSync -> settingsManager.syncSaves() || GameCloudSyncPreferences(applicationContext)
                .partitionGames(retrogradeDb.gameDao().selectAll()).alwaysFileNames.isNotEmpty()
            else -> false
        }
    }

    private fun displayNotification() {
        val notificationsManager = NotificationsManager(applicationContext)
        val foregroundInfo =
            createSyncForegroundInfo(
                NotificationsManager.SAVE_SYNC_NOTIFICATION_ID,
                notificationsManager.saveSyncNotification(),
            )
        setForegroundAsync(foregroundInfo)
    }

    companion object {
        val UNIQUE_WORK_ID: String = SaveSyncWork::class.java.simpleName
        val UNIQUE_PERIODIC_WORK_ID: String = SaveSyncWork::class.java.simpleName + "Periodic"
        private const val IS_AUTO = "IS_AUTO"
        private const val GAME_FILE_NAME = "GAME_FILE_NAME"

        fun enqueueManualWork(applicationContext: Context) {
            enqueueOneShot(applicationContext, workDataOf(IS_AUTO to false))
        }

        fun enqueueGameWork(
            applicationContext: Context,
            gameFileName: String,
        ) {
            enqueueOneShot(
                applicationContext,
                workDataOf(IS_AUTO to false, GAME_FILE_NAME to gameFileName),
            )
        }

        fun enqueueAutoWork(
            applicationContext: Context,
            delayMinutes: Long = 0,
        ) {
            val prefs = SharedPreferencesHelper.getSharedPreferences(applicationContext)
            val autoEnabled = prefs.getBoolean(applicationContext.getString(R.string.pref_key_save_sync_auto), false)
            if (!autoEnabled) {
                cancelAutoWork(applicationContext)
                return
            }

            val interval = prefs.getString(applicationContext.getString(R.string.pref_key_save_sync_interval), "3h")
            val (repeat, unit) =
                when (interval) {
                    "1h" -> 1L to TimeUnit.HOURS
                    "daily" -> 1L to TimeUnit.DAYS
                    else -> 3L to TimeUnit.HOURS
                }

            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK_ID,
                ExistingPeriodicWorkPolicy.REPLACE,
                PeriodicWorkRequestBuilder<SaveSyncWork>(repeat, unit)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.UNMETERED)
                            .setRequiresBatteryNotLow(true)
                            .build(),
                    )
                    .setInputData(workDataOf(IS_AUTO to true))
                    .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                    .build(),
            )
        }

        fun cancelManualWork(applicationContext: Context) {
            WorkManager.getInstance(applicationContext).cancelUniqueWork(UNIQUE_WORK_ID)
        }

        fun cancelAutoWork(applicationContext: Context) {
            WorkManager.getInstance(applicationContext).cancelUniqueWork(UNIQUE_PERIODIC_WORK_ID)
        }

        private fun enqueueOneShot(
            applicationContext: Context,
            inputData: Data,
        ) {
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_ID,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SaveSyncWork>()
                    .setInputData(inputData)
                    .build(),
            )
        }
    }

    @dagger.Module(subcomponents = [Subcomponent::class])
    abstract class Module {
        @Binds
        @IntoMap
        @WorkerKey(SaveSyncWork::class)
        abstract fun bindMyWorkerFactory(builder: Subcomponent.Builder): AndroidInjector.Factory<out ListenableWorker>
    }

    @dagger.Subcomponent
    interface Subcomponent : AndroidInjector<SaveSyncWork> {
        @dagger.Subcomponent.Builder
        abstract class Builder : AndroidInjector.Builder<SaveSyncWork>()
    }
}
