package com.omnidroid.app

import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.startup.AppInitializer
import androidx.work.Configuration
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.google.android.material.color.DynamicColors
import com.omnidroid.lib.backend.ConsoleBackendRegistry
import com.omnidroid.lib.backend.libretro.LibretroBackendDescriptor
import com.omnidroid.lib.backend.switch_backend.SwitchBackendDescriptor
import com.omnidroid.app.mobile.feature.settings.SettingsManager
import com.omnidroid.app.shared.displays.DisplayClient
import com.omnidroid.app.shared.covers.CoverUtils
import com.omnidroid.app.shared.startup.GameProcessInitializer
import com.omnidroid.app.shared.startup.MainProcessInitializer
import com.omnidroid.app.utils.android.isMainProcess
import com.omnidroid.ext.feature.context.ContextHandler
import com.omnidroid.lib.library.db.RetrogradeDatabase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

@HiltAndroidApp
class OmnidroidApplication : android.app.Application(), ImageLoaderFactory, Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    @Inject
    lateinit var settingsManager: SettingsManager

    val displays by lazy { DisplayClient(this) }

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

        displays.register()

        DynamicColors.applyToActivitiesIfAvailable(this)

        ConsoleBackendRegistry.registerDescriptor(LibretroBackendDescriptor())
        ConsoleBackendRegistry.registerDescriptor(SwitchBackendDescriptor())

        if (isMainProcess()) {
            com.omnidroid.app.shared.library.LibraryPeriodicScanScheduler.syncWithPreference(this)
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        ContextHandler.attachBaseContext(base)
    }

    override fun newImageLoader(): ImageLoader {
        return CoverUtils.buildImageLoader(applicationContext)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            CoverUtils.trimMemory(this)
        }
    }
}
