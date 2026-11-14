package com.omnidroid.app.shared.displays

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import timber.log.Timber

/** Owned by the coordinator, including registration and teardown on the main thread. */
class DisplayRepository(context: Context, private val onChanged: (List<DisplayFacts>) -> Unit) :
    DisplayManager.DisplayListener {
    private val context = context.applicationContext
    private val manager = context.getSystemService(DisplayManager::class.java)
    private val supportsActivities =
        context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS,
        )

    fun start() {
        manager.registerDisplayListener(this, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun stop() = manager.unregisterDisplayListener(this)

    fun display(id: Int): Display? = manager.getDisplay(id)?.takeIf { it.isValid }

    @Suppress("DEPRECATION")
    fun refresh() {
        // Never return early after finding a presentation display: interactive displays can coexist.
        val displays =
            try {
                manager.displays
            } catch (error: RuntimeException) {
                Timber.w(error, "Unable to enumerate displays")
                return
            }
        val facts =
            displays.mapNotNull { display ->
                if (!display.isValid) return@mapNotNull null
                try {
                    val metrics = DisplayMetrics().also(display::getRealMetrics)
                    val privateDisplay = display.flags and Display.FLAG_PRIVATE != 0
                    val activityAllowed =
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            supportsActivities &&
                            runCatching {
                                context.getSystemService(ActivityManager::class.java).isActivityStartAllowedOnDisplay(
                                    context, display.displayId, Intent(context, SecondaryDisplayActivity::class.java),
                                )
                            }.getOrDefault(false)
                    DisplayFacts(
                        id = display.displayId,
                        name = display.name.ifBlank { "Display ${display.displayId}" },
                        width = metrics.widthPixels,
                        height = metrics.heightPixels,
                        densityDpi = metrics.densityDpi,
                        rotation = display.rotation,
                        flags = display.flags,
                        awake = display.state == Display.STATE_ON || display.state == Display.STATE_VR,
                        presentation = display.flags and Display.FLAG_PRESENTATION != 0,
                        privateDisplay = privateDisplay,
                        activityAllowed = activityAllowed,
                    )
                } catch (error: RuntimeException) {
                    // Hot-unplug can race any metadata query, including an added/removed callback.
                    Timber.w(error, "Display disappeared during discovery")
                    null
                }
            }.sortedBy { it.id }
        onChanged(facts)
    }

    override fun onDisplayAdded(displayId: Int) = refresh()

    override fun onDisplayRemoved(displayId: Int) = refresh()

    override fun onDisplayChanged(displayId: Int) = refresh()
}
