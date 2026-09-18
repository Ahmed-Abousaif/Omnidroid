package com.omnidroid.app

import android.annotation.SuppressLint
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.startup.AppInitializer
import androidx.work.Configuration
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.google.android.material.color.DynamicColors
import com.omnidroid.app.mobile.feature.settings.SettingsManager
import com.omnidroid.app.shared.covers.CoverUtils
import com.omnidroid.app.shared.covers.RawgCoverStore
import com.omnidroid.app.shared.startup.GameProcessInitializer
import com.omnidroid.app.shared.startup.MainProcessInitializer
import com.omnidroid.app.utils.android.isMainProcess
import com.omnidroid.ext.feature.context.ContextHandler
import com.omnidroid.lib.library.db.RetrogradeDatabase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class OmnidroidApplication : android.app.Application(), ImageLoaderFactory, Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    @Inject
    lateinit var settingsManager: SettingsManager

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()

    companion object {
        fun scope(context: Context): CoroutineScope =
            (context.applicationContext as OmnidroidApplication).applicationScope
    }

    @SuppressLint("CheckResult")
    override fun onCreate() {
        super.onCreate()

        // Initialize WorkManager with HiltWorkerFactory before App Startup enqueues any @HiltWorker.
        WorkManager.getInstance(this)

        val initializeComponent =
            if (isMainProcess()) {
                MainProcessInitializer::class.java
            } else {
                GameProcessInitializer::class.java
            }

        AppInitializer.getInstance(this).initializeComponent(initializeComponent)

        DynamicColors.applyToActivitiesIfAvailable(this)

        if (isMainProcess()) {
            bootstrapRawgCovers()
        }
    }

    private fun bootstrapRawgCovers() {
        applicationScope.launch {
            runCatching {
                if (!settingsManager.enableRawgMetadata()) {
                    RawgCoverStore.clear()
                    return@launch
                }
                val dao = retrogradeDb.rawgGameMetadataDao()
                // Fix older rows that stored the landscape background as cover art.
                dao.selectRowsWithBackgroundUsedAsCover().forEach { row ->
                    dao.insert(row.copy(coverImageUrl = null, updatedAt = System.currentTimeMillis()))
                }
                val all = dao.selectAll()
                RawgCoverStore.replaceAll(
                    all.mapNotNull { meta ->
                        val cover = meta.coverImageUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        if (cover == meta.backgroundImageUrl) return@mapNotNull null
                        meta.gameId to cover
                    }.toMap(),
                )
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        ContextHandler.attachBaseContext(base)
    }

    override fun newImageLoader(): ImageLoader {
        return CoverUtils.buildImageLoader(applicationContext)
    }
}
