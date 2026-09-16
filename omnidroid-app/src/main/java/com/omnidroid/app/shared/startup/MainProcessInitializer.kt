package com.omnidroid.app.shared.startup

import android.content.Context
import androidx.startup.Initializer
import com.omnidroid.app.mobile.feature.profile.UserProfileStore
import com.omnidroid.app.shared.library.LibraryIndexScheduler
import com.omnidroid.app.shared.savesync.SaveSyncWork
import com.omnidroid.app.shared.settings.HdModeBatteryMonitor
import timber.log.Timber

class MainProcessInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        Timber.i("Requested initialization of main process tasks")
        // Auto-create profile on first launch (idempotent — no-op on subsequent launches)
        UserProfileStore(context).ensureCreated()
        // Avoid WorkManagerInitializer — it locks in the default factory and skips HiltWorkerFactory.
        SaveSyncWork.enqueueAutoWork(context, 0)
        LibraryIndexScheduler.scheduleCoreUpdate(context)
        HdModeBatteryMonitor.start(context)
    }


    override fun dependencies(): List<Class<out Initializer<*>>> {
        return listOf(DebugInitializer::class.java)
    }
}
