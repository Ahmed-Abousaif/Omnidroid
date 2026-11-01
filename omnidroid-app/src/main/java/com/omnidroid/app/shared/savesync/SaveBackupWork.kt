package com.omnidroid.app.shared.savesync

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.omnidroid.app.mobile.shared.NotificationsManager
import com.omnidroid.app.utils.android.createSyncForegroundInfo
import com.omnidroid.lib.storage.DirectoriesManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class SaveBackupWork
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val directoriesManager: DirectoriesManager,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            val action = inputData.getString(ACTION)
                ?: if (inputData.getBoolean(IS_EXPORT, true)) ACTION_EXPORT else ACTION_IMPORT
            val uri = inputData.getString(URI)?.let { Uri.parse(it) }

            val notificationsManager = NotificationsManager(applicationContext)
            setForegroundAsync(
                createSyncForegroundInfo(
                    NotificationsManager.SAVE_SYNC_NOTIFICATION_ID,
                    notificationsManager.saveSyncNotification(),
                ),
            )

            return runCatching {
                val manager = SaveBackupManager(applicationContext, directoriesManager)
                when (action) {
                    ACTION_EXPORT -> {
                        if (uri == null) return Result.failure()
                        manager.exportTo(uri)
                    }
                    ACTION_IMPORT -> {
                        if (uri == null) return Result.failure()
                        manager.importFrom(uri)
                    }
                    ACTION_IMPORT_FOLDER -> {
                        if (uri == null) return Result.failure()
                        manager.importFromFolder(uri)
                    }
                    ACTION_MIGRATE_LEMUROID -> {
                        val migrationManager = LemuroidMigrationManager(applicationContext, directoriesManager)
                        migrationManager.migrateFromLemuroid()
                    }
                    else -> return Result.failure()
                }
            }.onFailure {
                Timber.e(it, "Save backup/migration failed for action: $action")
            }.fold({ Result.success() }, { Result.success() })
        }

        companion object {
            val UNIQUE_WORK_ID: String = SaveBackupWork::class.java.simpleName
            private const val URI = "URI"
            private const val IS_EXPORT = "IS_EXPORT"
            private const val ACTION = "ACTION"

            const val ACTION_EXPORT = "EXPORT"
            const val ACTION_IMPORT = "IMPORT"
            const val ACTION_IMPORT_FOLDER = "IMPORT_FOLDER"
            const val ACTION_MIGRATE_LEMUROID = "MIGRATE_LEMUROID"

            fun enqueueExport(
                context: Context,
                uri: Uri,
            ) {
                enqueue(context, uri, ACTION_EXPORT)
            }

            fun enqueueImport(
                context: Context,
                uri: Uri,
            ) {
                enqueue(context, uri, ACTION_IMPORT)
            }

            fun enqueueImportFolder(
                context: Context,
                treeUri: Uri,
            ) {
                enqueue(context, treeUri, ACTION_IMPORT_FOLDER)
            }

            fun enqueueLemuroidMigration(
                context: Context,
            ) {
                enqueue(context, null, ACTION_MIGRATE_LEMUROID)
            }

            private fun enqueue(
                context: Context,
                uri: Uri?,
                action: String,
            ) {
                val workData = workDataOf(
                    ACTION to action,
                    URI to (uri?.toString() ?: ""),
                    IS_EXPORT to (action == ACTION_EXPORT),
                )

                WorkManager.getInstance(context).enqueueUniqueWork(
                    UNIQUE_WORK_ID,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<SaveBackupWork>()
                        .setInputData(workData)
                        .build(),
                )
            }
        }
    }
