package com.swordfish.lemuroid.app.tv.channel

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class ChannelUpdateWork
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val channelHandler: ChannelHandler,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            try {
                channelHandler.update()
            } catch (e: Throwable) {
                Timber.e(e, "Error in channel update")
            }

            return Result.success()
        }

        companion object {
            private val UNIQUE_WORK_ID: String = ChannelUpdateWork::class.java.simpleName

            fun enqueue(applicationContext: Context) {
                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    UNIQUE_WORK_ID,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<ChannelUpdateWork>().build(),
                )
            }

            fun cancel(applicationContext: Context) {
                WorkManager.getInstance(applicationContext).cancelUniqueWork(UNIQUE_WORK_ID)
            }
        }
    }
