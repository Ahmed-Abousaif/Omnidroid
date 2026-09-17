package com.omnidroid.app.shared.cast

import android.app.Activity
import android.app.ActivityOptions
import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.MediaRouter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.lang.ref.WeakReference

data class CastTarget(
    val displayId: Int,
    val name: String,
)

data class CastState(
    val targets: List<CastTarget> = emptyList(),
    val selectedDisplayId: Int? = null,
    val selectedName: String? = null,
) {
    val isCasting: Boolean get() = selectedDisplayId != null
}

/**
 * Result of [CastDisplayManager.stopCasting].
 *
 * [NeedsSystemDisconnect] means the app cleared its own cast target, but the OS still has an
 * active remote live-video route (wireless display / Cast). The UI should open system Cast
 * settings so the user can finish disconnecting — apps cannot silently kill every OEM session.
 */
sealed class StopCastResult {
    data object Stopped : StopCastResult()

    data object NeedsSystemDisconnect : StopCastResult()
}

class CastDisplayManager(
    context: Context,
) : DisplayManager.DisplayListener {
    private val appContext = context.applicationContext
    private val displayManager =
        appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val mediaRouter =
        appContext.getSystemService(Context.MEDIA_ROUTER_SERVICE) as MediaRouter

    private val _state = MutableStateFlow(CastState())
    val state: StateFlow<CastState> = _state.asStateFlow()

    private var idlePresentation: Presentation? = null
    private var suppressIdle = false
    private var hostActivityRef: WeakReference<Activity>? = null

    init {
        displayManager.registerDisplayListener(this, null)
        refresh()
    }

    /** Keep a host Activity so idle Presentation and keep-screen-on can be maintained. */
    fun attachHost(activity: Activity) {
        hostActivityRef = WeakReference(activity)
        applyHostKeepScreenOn(activity, _state.value.isCasting)
        if (_state.value.isCasting && !suppressIdle) {
            showIdle(activity)
        }
    }

    fun refresh() {
        val targets = presentationDisplays().map { it.toCastTarget() }
        val selectedId = _state.value.selectedDisplayId
        val selectedDisplay = selectedId?.let { displayManager.getDisplay(it) }
        // Keep the selection while the Display still exists, even if it briefly reports STATE_OFF
        // and is omitted from the picker list. Only drop it when the display is gone.
        val selectedStillExists = selectedDisplay != null
        _state.value =
            CastState(
                targets = targets,
                selectedDisplayId = selectedId.takeIf { selectedStillExists },
                selectedName =
                    when {
                        !selectedStillExists -> null
                        else ->
                            targets.firstOrNull { it.displayId == selectedId }?.name
                                ?: selectedDisplay.name.ifBlank { "Display $selectedId" }
                    },
            )
        if (selectedId != null && !selectedStillExists) {
            hideIdle()
            hostActivity()?.let { applyHostKeepScreenOn(it, false) }
        }
    }

    fun selectDisplay(displayId: Int) {
        val target = _state.value.targets.firstOrNull { it.displayId == displayId } ?: return
        _state.value =
            _state.value.copy(
                selectedDisplayId = target.displayId,
                selectedName = target.name,
            )
        hostActivity()?.let { applyHostKeepScreenOn(it, true) }
    }

    fun stopCasting(): StopCastResult {
        hideIdle()
        _state.value =
            _state.value.copy(
                selectedDisplayId = null,
                selectedName = null,
            )
        hostActivity()?.let { applyHostKeepScreenOn(it, false) }
        disconnectSystemDisplayRoute()
        refresh()
        return if (hasActiveRemoteVideoRoute()) {
            StopCastResult.NeedsSystemDisconnect
        } else {
            StopCastResult.Stopped
        }
    }

    fun selectedDisplay(): Display? {
        val id = _state.value.selectedDisplayId ?: return null
        return displayManager.getDisplay(id)
    }

    fun launchOptions(): Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val display = selectedDisplay() ?: return null
        return ActivityOptions.makeBasic().setLaunchDisplayId(display.displayId).toBundle()
    }

    fun hideIdleForGame() {
        suppressIdle = true
        hideIdle()
    }

    fun restoreIdle(activity: Activity) {
        suppressIdle = false
        attachHost(activity)
        showIdle(activity)
    }

    fun allowIdle() {
        suppressIdle = false
    }

    fun showIdle(activity: Activity) {
        hostActivityRef = WeakReference(activity)
        applyHostKeepScreenOn(activity, _state.value.isCasting)
        if (suppressIdle) return
        val display = selectedDisplay() ?: run {
            hideIdle()
            return
        }
        if (display.state == Display.STATE_OFF) {
            // Display is parked (often while the source sleeps). Wait for onDisplayChanged.
            return
        }
        val current = idlePresentation
        if (current != null && current.isShowing && current.display.displayId == display.displayId) {
            return
        }
        hideIdle()
        try {
            idlePresentation =
                CastIdlePresentation(activity, display).also { presentation ->
                    presentation.setOnDismissListener {
                        if (idlePresentation === presentation) {
                            idlePresentation = null
                        }
                    }
                    presentation.window?.addFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    )
                    presentation.show()
                }
        } catch (error: Exception) {
            Timber.e(error, "Unable to show cast presentation")
            idlePresentation = null
        }
    }

    fun hideIdle() {
        try {
            idlePresentation?.dismiss()
        } catch (_: Exception) {
        }
        idlePresentation = null
    }

    fun openSystemCastSettings(context: Context) {
        val intents =
            buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(Intent(Settings.ACTION_CAST_SETTINGS))
                }
                add(Intent("android.settings.CAST_SETTINGS"))
                add(Intent("android.settings.WIFI_DISPLAY_SETTINGS"))
                add(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            }
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return
            }
        }
    }

    override fun onDisplayAdded(displayId: Int) {
        refresh()
        reattachIdleIfNeeded()
    }

    override fun onDisplayRemoved(displayId: Int) {
        refresh()
    }

    override fun onDisplayChanged(displayId: Int) {
        refresh()
        reattachIdleIfNeeded()
    }

    private fun reattachIdleIfNeeded() {
        if (!_state.value.isCasting || suppressIdle) return
        val activity = hostActivity() ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        showIdle(activity)
    }

    private fun hostActivity(): Activity? = hostActivityRef?.get()

    private fun applyHostKeepScreenOn(
        activity: Activity,
        enabled: Boolean,
    ) {
        val window = activity.window ?: return
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /**
     * Disconnect the platform MediaRouter live-video route (wireless display / Cast).
     * This is the standard way to return the selected route to "this device".
     */
    private fun disconnectSystemDisplayRoute() {
        try {
            val selected = mediaRouter.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_VIDEO)
            val defaultRoute = mediaRouter.defaultRoute
            if (selected != defaultRoute) {
                mediaRouter.selectRoute(MediaRouter.ROUTE_TYPE_LIVE_VIDEO, defaultRoute)
            }
        } catch (error: Exception) {
            Timber.w(error, "Unable to disconnect system display route")
        }
    }

    private fun hasActiveRemoteVideoRoute(): Boolean {
        return try {
            val selected = mediaRouter.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_VIDEO)
            selected != mediaRouter.defaultRoute
        } catch (_: Exception) {
            false
        }
    }

    private fun presentationDisplays(): List<Display> {
        val category =
            displayManager
                .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                .filter { it.state != Display.STATE_OFF }
        if (category.isNotEmpty()) return category
        val flagged =
            displayManager.displays.filter { display ->
                display.displayId != Display.DEFAULT_DISPLAY &&
                    display.state != Display.STATE_OFF &&
                    (display.flags and Display.FLAG_PRESENTATION) != 0
            }
        if (flagged.isNotEmpty()) return flagged
        return displayManager.displays.filter { display ->
            display.displayId != Display.DEFAULT_DISPLAY &&
                display.state != Display.STATE_OFF
        }
    }

    private fun Display.toCastTarget(): CastTarget =
        CastTarget(
            displayId = displayId,
            name = name.ifBlank { "Display $displayId" },
        )
}
