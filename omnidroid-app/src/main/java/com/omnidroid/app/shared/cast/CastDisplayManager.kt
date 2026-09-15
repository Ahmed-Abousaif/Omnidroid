package com.omnidroid.app.shared.cast

import android.app.Activity
import android.app.ActivityOptions
import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

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

class CastDisplayManager(
    context: Context,
) : DisplayManager.DisplayListener {
    private val appContext = context.applicationContext
    private val displayManager =
        appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private val _state = MutableStateFlow(CastState())
    val state: StateFlow<CastState> = _state.asStateFlow()

    private var idlePresentation: Presentation? = null
    private var suppressIdle = false

    init {
        displayManager.registerDisplayListener(this, null)
        refresh()
    }

    fun refresh() {
        val targets =
            presentationDisplays().map { display ->
                CastTarget(
                    displayId = display.displayId,
                    name = display.name.ifBlank { "Display ${display.displayId}" },
                )
            }
        val selectedId = _state.value.selectedDisplayId
        val selectedStillThere = targets.any { it.displayId == selectedId }
        _state.value =
            CastState(
                targets = targets,
                selectedDisplayId = selectedId.takeIf { selectedStillThere },
                selectedName = targets.firstOrNull { it.displayId == selectedId }?.name,
            )
        if (selectedId != null && !selectedStillThere) {
            hideIdle()
        }
    }

    fun selectDisplay(displayId: Int) {
        val target = _state.value.targets.firstOrNull { it.displayId == displayId } ?: return
        _state.value =
            _state.value.copy(
                selectedDisplayId = target.displayId,
                selectedName = target.name,
            )
    }

    fun stopCasting() {
        hideIdle()
        _state.value =
            _state.value.copy(
                selectedDisplayId = null,
                selectedName = null,
            )
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
        showIdle(activity)
    }

    fun allowIdle() {
        suppressIdle = false
    }

    fun showIdle(activity: Activity) {
        if (suppressIdle) return
        val display = selectedDisplay() ?: run {
            hideIdle()
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

    override fun onDisplayAdded(displayId: Int) = refresh()

    override fun onDisplayRemoved(displayId: Int) = refresh()

    override fun onDisplayChanged(displayId: Int) = refresh()

    private fun presentationDisplays(): List<Display> {
        val category =
            displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).toList()
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
}
