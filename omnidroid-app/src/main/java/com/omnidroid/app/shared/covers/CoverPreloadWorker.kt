package com.omnidroid.app.shared.covers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.omnidroid.lib.library.db.RetrogradeDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class CoverPreloadWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val retrogradeDatabase: RetrogradeDatabase,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result =
            withContext(Dispatchers.IO) {
                val store = CoverUtils.coverCache(applicationContext)
                val total = retrogradeDatabase.gameDao().countAll()
                if (total == 0) return@withContext Result.success()

                var loaded = 0
                var index = 0
                var offset = 0
                while (offset < total) {
                    val page = retrogradeDatabase.gameDao().selectPage(offset, PAGE_SIZE)
                    if (page.isEmpty()) break
                    for (game in page) {
                        val url = game.coverFrontUrl
                        if (!url.isNullOrBlank() && CoverUtils.isAllowedRemoteCoverUrl(url)) {
                            if (store.ensureGrid(game.id, url) != null) loaded++
                        }
                        index++
                        if (index % 10 == 0 || index == total) {
                            setProgress(
                                workDataOf(
                                    "current" to index,
                                    "total" to total,
                                    "loaded" to loaded,
                                ),
                            )
                        }
                    }
                    offset += page.size
                }

                Result.success(workDataOf("total" to total, "loaded" to loaded))
            }

        companion object {
            private const val PAGE_SIZE = 50
        }
    }
