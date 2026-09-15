package com.swordfish.lemuroid.app.shared.library

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.swordfish.lemuroid.app.mobile.shared.NotificationsManager
import com.swordfish.lemuroid.app.utils.android.createSyncForegroundInfo
import com.swordfish.lemuroid.lib.library.LemuroidLibrary
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
        private val lemuroidLibrary: LemuroidLibrary,
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
                        lemuroidLibrary.indexLibrary()
                    }
                }

            result.exceptionOrNull()?.let {
                Timber.e("Library indexing work terminated with an exception:", it)
            }

            return Result.success()
        }
    }
