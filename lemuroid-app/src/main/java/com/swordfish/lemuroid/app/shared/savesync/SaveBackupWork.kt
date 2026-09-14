package com.swordfish.lemuroid.app.shared.savesync

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.swordfish.lemuroid.app.mobile.shared.NotificationsManager
import com.swordfish.lemuroid.app.utils.android.createSyncForegroundInfo
import com.swordfish.lemuroid.lib.injection.AndroidWorkerInjection
import com.swordfish.lemuroid.lib.injection.WorkerKey
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import dagger.Binds
import dagger.android.AndroidInjector
import dagger.multibindings.IntoMap
import timber.log.Timber
import javax.inject.Inject

class SaveBackupWork(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {
    @Inject
    lateinit var directoriesManager: DirectoriesManager

    override suspend fun doWork(): Result {
        AndroidWorkerInjection.inject(this)
        val uri = inputData.getString(URI)?.let { Uri.parse(it) } ?: return Result.failure()
        val export = inputData.getBoolean(IS_EXPORT, true)

        val notificationsManager = NotificationsManager(applicationContext)
        setForegroundAsync(
            createSyncForegroundInfo(
                NotificationsManager.SAVE_SYNC_NOTIFICATION_ID,
                notificationsManager.saveSyncNotification(),
            ),
        )

        return runCatching {
            val manager = SaveBackupManager(applicationContext, directoriesManager)
            if (export) manager.exportTo(uri) else manager.importFrom(uri)
        }.onFailure {
            Timber.e(it, "Save backup failed")
        }.fold({ Result.success() }, { Result.success() })
    }

    companion object {
        val UNIQUE_WORK_ID: String = SaveBackupWork::class.java.simpleName
        private const val URI = "URI"
        private const val IS_EXPORT = "IS_EXPORT"

        fun enqueueExport(
            context: Context,
            uri: Uri,
        ) {
            enqueue(context, uri, true)
        }

        fun enqueueImport(
            context: Context,
            uri: Uri,
        ) {
            enqueue(context, uri, false)
        }

        private fun enqueue(
            context: Context,
            uri: Uri,
            export: Boolean,
        ) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_ID,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SaveBackupWork>()
                    .setInputData(workDataOf(URI to uri.toString(), IS_EXPORT to export))
                    .build(),
            )
        }
    }

    @dagger.Module(subcomponents = [Subcomponent::class])
    abstract class Module {
        @Binds
        @IntoMap
        @WorkerKey(SaveBackupWork::class)
        abstract fun bindMyWorkerFactory(builder: Subcomponent.Builder): AndroidInjector.Factory<out ListenableWorker>
    }

    @dagger.Subcomponent
    interface Subcomponent : AndroidInjector<SaveBackupWork> {
        @dagger.Subcomponent.Builder
        abstract class Builder : AndroidInjector.Builder<SaveBackupWork>()
    }
}
