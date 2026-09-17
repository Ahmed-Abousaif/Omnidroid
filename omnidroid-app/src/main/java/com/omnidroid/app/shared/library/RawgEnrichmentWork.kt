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
                    repairBackgroundUsedAsCover(dao)

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
                        }
                        delay(250)
                    }

                    refreshCoverStore(dao)
                }.onFailure {
                    Timber.e(it, "RAWG enrichment work failed")
                }
            }

            return Result.success()
        }

        private suspend fun repairBackgroundUsedAsCover(
            dao: com.omnidroid.lib.library.db.dao.RawgGameMetadataDao,
        ) {
            val broken = dao.selectRowsWithBackgroundUsedAsCover()
            if (broken.isEmpty()) return
            Timber.i("RAWG enrichment: clearing %d rows that used background as cover", broken.size)
            for (row in broken) {
                dao.insert(row.copy(coverImageUrl = null, updatedAt = System.currentTimeMillis()))
            }
        }

        private suspend fun refreshCoverStore(
            dao: com.omnidroid.lib.library.db.dao.RawgGameMetadataDao,
        ) {
            val all = dao.selectAll()
            RawgCoverStore.replaceAll(
                all.mapNotNull { meta ->
                    val cover = meta.coverImageUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    // Never treat the backdrop promo image as library cover art.
                    if (cover == meta.backgroundImageUrl) return@mapNotNull null
                    meta.gameId to cover
                }.toMap(),
            )
        }
    }
