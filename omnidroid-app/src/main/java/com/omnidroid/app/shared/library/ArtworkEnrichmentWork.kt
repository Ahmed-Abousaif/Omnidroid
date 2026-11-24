package com.omnidroid.app.shared.library

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.omnidroid.app.shared.metadata.MetadataRepository
import com.omnidroid.app.shared.metadata.MetadataSettings
import com.omnidroid.lib.library.db.RetrogradeDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class ArtworkEnrichmentWork @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: RetrogradeDatabase,
    private val repository: MetadataRepository,
    private val settings: MetadataSettings,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!settings.enabled) return Result.success()
        repository.syncCorrections()
        val started = android.os.SystemClock.elapsedRealtime()
        var retry = false
        while (!isStopped && android.os.SystemClock.elapsedRealtime() - started < 180_000) {
            val games = database.catalogDao().automaticGames(30, settings.cursor)
            if (games.isEmpty()) { settings.cursor = 0; return if (retry) Result.retry() else Result.success() }
            retry = repository.enrich(games) || retry
            settings.cursor = games.last().id
        }
        return Result.retry()
    }

    companion object {
        private const val NAME = "artwork_enrichment"
        fun schedule(context: Context, reset: Boolean = false) {
            val settings = MetadataSettings(context.applicationContext)
            val manager = WorkManager.getInstance(context.applicationContext)
            if (!settings.enabled) { manager.cancelUniqueWork(NAME); return }
            if (reset) settings.cursor = 0
            val prefs = com.omnidroid.lib.preferences.SharedPreferencesHelper.getSharedPreferences(context)
            val changedNetwork = prefs.contains("artwork_scheduled_wifi_only") && prefs.getBoolean("artwork_scheduled_wifi_only", true) != settings.wifiOnly
            prefs.edit().putBoolean("artwork_scheduled_wifi_only", settings.wifiOnly).apply()
            manager.enqueueUniqueWork(NAME, if (changedNetwork) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ArtworkEnrichmentWork>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        }
    }
}
