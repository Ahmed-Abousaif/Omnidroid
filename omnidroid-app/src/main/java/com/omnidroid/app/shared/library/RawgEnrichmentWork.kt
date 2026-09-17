package com.omnidroid.app.shared.library

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.omnidroid.app.mobile.feature.settings.SettingsManager
import com.omnidroid.app.mobile.shared.NotificationsManager
import com.omnidroid.app.shared.covers.RawgCoverStore
import com.omnidroid.app.utils.android.createSyncForegroundInfo
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.RawgGameMetadata
import com.omnidroid.metadata.rawg.RawgMetadataRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltWorker
class RawgEnrichmentWork
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val retrogradeDatabase: RetrogradeDatabase,
        private val rawgMetadataRepository: RawgMetadataRepository,
        private val settingsManager: SettingsManager,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            if (!settingsManager.enableRawgMetadata()) {
                Timber.i("RAWG enrichment skipped (disabled)")
                RawgCoverStore.clear()
                return Result.success()
            }

            val notificationsManager = NotificationsManager(applicationContext)
            val foregroundInfo =
                createSyncForegroundInfo(
                    NotificationsManager.RAWG_ENRICHMENT_NOTIFICATION_ID,
                    notificationsManager.rawgEnrichmentNotification(),
                )
            setForegroundAsync(foregroundInfo)

            withContext(Dispatchers.IO) {
                runCatching {
                    val dao = retrogradeDatabase.rawgGameMetadataDao()
                    dao.deleteOrphans()

                    val missingIds = dao.selectGameIdsMissingMetadata()
                    Timber.i("RAWG enrichment: %d games missing metadata", missingIds.size)

                    for (gameId in missingIds) {
                        val game = retrogradeDatabase.gameDao().selectById(gameId) ?: continue
                        val fetched =
                            rawgMetadataRepository.fetchForGame(game.displayName, game.systemId)
                        if (fetched != null) {
                            val row =
                                RawgGameMetadata(
                                    gameId = game.id,
                                    rawgId = fetched.rawgId,
                                    description = fetched.description,
                                    genres = fetched.genres,
                                    released = fetched.released,
                                    backgroundImageUrl = fetched.backgroundImageUrl,
                                    coverImageUrl = fetched.coverImageUrl,
                                    rating = fetched.rating,
                                    publisher = fetched.publisher,
                                    updatedAt = System.currentTimeMillis(),
                                )
                            dao.insert(row)
                            fetched.coverImageUrl?.takeIf { it.isNotBlank() }?.let {
                                RawgCoverStore.put(game.id, it)
                            }
                        }
                        delay(250)
                    }

                    // Refresh cover cache from all persisted rows.
                    val all = dao.selectAll()
                    RawgCoverStore.replaceAll(
                        all.mapNotNull { meta ->
                            meta.coverImageUrl?.takeIf { it.isNotBlank() }?.let { meta.gameId to it }
                        }.toMap(),
                    )
                }.onFailure {
                    Timber.e(it, "RAWG enrichment work failed")
                }
            }

            return Result.success()
        }
    }
