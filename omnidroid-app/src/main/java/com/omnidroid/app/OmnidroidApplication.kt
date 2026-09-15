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
import com.omnidroid.app.shared.covers.CoverUtils
import com.omnidroid.app.shared.startup.GameProcessInitializer
import com.omnidroid.app.shared.startup.MainProcessInitializer
import com.omnidroid.app.utils.android.isMainProcess
import com.omnidroid.ext.feature.context.ContextHandler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OmnidroidApplication : android.app.Application(), ImageLoaderFactory, Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()

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
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        ContextHandler.attachBaseContext(base)
    }

    override fun newImageLoader(): ImageLoader {
        return CoverUtils.buildImageLoader(applicationContext)
    }
}
