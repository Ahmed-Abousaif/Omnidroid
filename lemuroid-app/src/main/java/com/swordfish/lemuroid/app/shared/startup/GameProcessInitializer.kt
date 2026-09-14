package com.swordfish.lemuroid.app.shared.startup

import android.content.Context
import androidx.startup.Initializer
import com.swordfish.lemuroid.app.shared.game.GameProcessLock
import com.swordfish.lemuroid.app.shared.settings.HdModeBatteryMonitor
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
