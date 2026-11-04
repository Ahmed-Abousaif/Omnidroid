package com.omnidroid.app.shared.library

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.omnidroid.app.mobile.shared.NotificationsManager
import com.omnidroid.app.utils.android.createSyncForegroundInfo
import com.omnidroid.lib.core.SyncProgressTracker
import com.omnidroid.lib.library.LibraryIndexResult
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
            // Keep a foreground service for long SAF walks; UI progress is gated on delta size.
            val foregroundInfo: ForegroundInfo =
                createSyncForegroundInfo(
                    NotificationsManager.LIBRARY_INDEXING_NOTIFICATION_ID,
                    notificationsManager.libraryIndexingNotification(),
                )
            setForegroundAsync(foregroundInfo)

            // Do not publish SyncProgressTracker until we know the delta is large — resume
            // auto-syncs stay silent and must not flash the library progress strip.
            val forceDeep = inputData.getBoolean(LibraryIndexScheduler.KEY_FORCE_DEEP, false)
            val result =
                withContext(Dispatchers.IO) {
                    kotlin.runCatching {
                        omnidroidLibrary.indexLibrary(forceDeep = forceDeep)
                    }
                }

            result.exceptionOrNull()?.let {
                Timber.e(it, "Library indexing work terminated with an exception")
            }

            val indexResult = result.getOrDefault(LibraryIndexResult.EMPTY)
            val silent =
                indexResult.deltaCount < SILENT_DELTA_THRESHOLD &&
                    indexResult.newSystemIds.isEmpty()
            if (silent) {
                SyncProgressTracker.clear()
            } else {
                SyncProgressTracker.update(
                    stepText = applicationContext.getString(com.omnidroid.R.string.library_scanning),
                    current = indexResult.deltaCount,
                    total = indexResult.deltaCount + indexResult.unchanged,
                    isIndeterminate = false,
                )
            }

            // Stash for CoreUpdateWork / UI: whether this pass was "noisy".
            LastLibraryIndexResult.set(indexResult)

            return Result.success()
        }

        companion object {
            const val SILENT_DELTA_THRESHOLD = 20
        }
    }

object LastLibraryIndexResult {
    @Volatile
    private var value: LibraryIndexResult = LibraryIndexResult.EMPTY

    fun set(result: LibraryIndexResult) {
        value = result
    }

    fun get(): LibraryIndexResult = value
}
