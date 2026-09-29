package com.omnidroid.app.shared.library

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.omnidroid.app.mobile.shared.NotificationsManager
import com.omnidroid.app.utils.android.createSyncForegroundInfo
import com.omnidroid.lib.library.OmnidroidLibrary
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltWorker
class LibraryIndexWork
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val omnidroidLibrary: OmnidroidLibrary,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            val notificationsManager = NotificationsManager(applicationContext)

            val foregroundInfo =
                createSyncForegroundInfo(
                    NotificationsManager.LIBRARY_INDEXING_NOTIFICATION_ID,
                    notificationsManager.libraryIndexingNotification(),
                )

            setForegroundAsync(foregroundInfo)

            val result =
                withContext(Dispatchers.IO) {
                    kotlin.runCatching {
                        omnidroidLibrary.indexLibrary()
                    }
                }

            result.exceptionOrNull()?.let {
                Timber.e("Library indexing work terminated with an exception:", it)
            }

            return Result.success()
        }
    }
