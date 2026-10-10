package com.omnidroid.app.shared.covers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
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
                val imageLoader = CoverUtils.buildImageLoader(applicationContext)
                val games = retrogradeDatabase.gameDao().selectAll()
                if (games.isEmpty()) return@withContext Result.success()

                var loaded = 0
                games.forEachIndexed { index, game ->
                    val data = CoverUtils.coverData(game)
                    if (data != null) {
                        val request =
                            ImageRequest.Builder(applicationContext)
                                .data(data)
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .size(512, 512)
                                .build()

                        val result = imageLoader.execute(request)
                        if (result is SuccessResult) loaded++
                    }

                    if (index % 10 == 0 || index == games.lastIndex) {
                        setProgress(
                            workDataOf(
                                "current" to index + 1,
                                "total" to games.size,
                                "loaded" to loaded,
                            ),
                        )
                    }
                }

                Result.success(workDataOf("total" to games.size, "loaded" to loaded))
            }
    }
