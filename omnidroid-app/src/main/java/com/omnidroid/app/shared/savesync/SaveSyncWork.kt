package com.omnidroid.app.shared.savesync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.profile.ProfileSyncHelper
import com.omnidroid.app.mobile.feature.settings.SettingsManager
import com.omnidroid.app.mobile.shared.NotificationsManager
import com.omnidroid.app.utils.android.createSyncForegroundInfo
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.findByName
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import com.omnidroid.lib.savesync.GameCloudSyncPreferences
import com.omnidroid.lib.savesync.SaveSyncManager
import com.omnidroid.lib.savesync.SaveSyncRequest
import com.omnidroid.lib.storage.DirectoriesManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class SaveSyncWork
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val saveSyncManager: SaveSyncManager,
        private val settingsManager: SettingsManager,
        private val retrogradeDb: RetrogradeDatabase,
        private val directoriesManager: DirectoriesManager,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
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

            val profileSync = ProfileSyncHelper(applicationContext, directoriesManager)
            profileSync.exportBeforeSync()

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

            profileSync.importAfterSync()

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
    }
