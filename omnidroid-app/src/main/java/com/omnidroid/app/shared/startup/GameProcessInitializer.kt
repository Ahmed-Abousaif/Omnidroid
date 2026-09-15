package com.omnidroid.app.shared.startup

import android.content.Context
import androidx.startup.Initializer
import com.omnidroid.app.shared.game.GameProcessLock
import com.omnidroid.app.shared.settings.HdModeBatteryMonitor
import timber.log.Timber

class GameProcessInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        Timber.i("Requested initialization of game process tasks")
        GameProcessLock.acquire(context.applicationContext)
        HdModeBatteryMonitor.start(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return listOf(DebugInitializer::class.java)
    }
}
