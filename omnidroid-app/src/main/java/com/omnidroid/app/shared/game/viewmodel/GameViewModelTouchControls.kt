package com.omnidroid.app.shared.game.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.settings.SettingsManager
import com.omnidroid.app.shared.game.GamepadColor
import com.omnidroid.app.shared.rumble.RumbleManager
import com.omnidroid.app.shared.settings.HapticFeedbackMode
import com.omnidroid.app.tv.shared.TVHelper
import com.omnidroid.common.coroutines.launchOnState
import com.omnidroid.common.coroutines.safeCollect
import com.omnidroid.lib.controller.ControllerConfig
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.SystemCoreConfig
import com.omnidroid.lib.library.SystemID
import com.omnidroid.libretrodroid.GLRetroView
import com.omnidroid.libretrodroid.GLRetroView.Companion.MOTION_SOURCE_ANALOG_LEFT
import com.omnidroid.libretrodroid.GLRetroView.Companion.MOTION_SOURCE_ANALOG_RIGHT
import com.omnidroid.libretrodroid.GLRetroView.Companion.MOTION_SOURCE_DPAD
import com.omnidroid.touchinput.radial.layouts.shared.ComposeTouchLayouts
import com.omnidroid.touchinput.radial.settings.TouchControllerID
import com.omnidroid.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.haptics.HapticEffect
import gg.padkit.haptics.createAndroidHapticFeedback
import gg.padkit.inputevents.InputEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTouchControls(
    private val appContext: Context,
    private val settingsManager: SettingsManager,
    private val touchControllerSettingsManager: TouchControllerSettingsManager,
    private val retroGameView: GameViewModelRetroGameView,
    private val inputs: GameViewModelInput,
    private val tilt: GameViewModelTilt,
    private val sideEffects: GameViewModelSideEffects,
    private val scope: CoroutineScope,
    private val systemId: SystemID,
    private val systemCoreConfig: SystemCoreConfig,
    private val sharedPreferences: SharedPreferences,
    private val hasTouchScreen: Boolean,
) : DefaultLifecycleObserver {
    private val touchControlId = MutableStateFlow(TouchControllerID.GB)
    private val screenOrientation = MutableStateFlow(TouchControllerSettingsManager.Orientation.PORTRAIT)
    private val menuPressed = MutableStateFlow(false)
    private val showEditControls = MutableStateFlow(false)
    private val hapticFeedbackMode = MutableStateFlow(HapticFeedbackMode.NONE)
    private val vibrationIntensity = MutableStateFlow(RumbleManager.DEFAULT_RUMBLE_STRENGTH)
    private val virtualPadOpacity = MutableStateFlow(DEFAULT_PAD_OPACITY)
    private val gamepadTint = MutableStateFlow<Color?>(null)
    private val hostHapticFeedback by lazy { createAndroidHapticFeedback(appContext) }
    private val forceHideTouchControls = MutableStateFlow(false)

    private val screenLayoutController =
        TouchScreenLayoutController(
            sharedPreferences = sharedPreferences,
            systemId = systemId,
            coreId = systemCoreConfig.coreID,
        )
    private val screenLayoutRevision = MutableStateFlow(0)

    private var loadingMenuJob: Job? = null
    private var touchPreferencesJob: Job? = null
    private val touchPreferenceKeys = setOf(
        appContext.getString(R.string.pref_key_haptic_feedback_mode),
        appContext.getString(R.string.pref_key_vibration_intensity),
        appContext.getString(R.string.pref_key_virtual_pad_opacity),
        appContext.getString(R.string.pref_key_gamepad_color),
        appContext.getString(R.string.pref_key_accent_color),
    )

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        screenLayoutRevision.value++
        if (key == null || key in touchPreferenceKeys) refreshTouchPreferences()
    }

    override fun onCreate(owner: LifecycleOwner) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefListener)
        owner.launchOnState(Lifecycle.State.CREATED) {
            getTouchControllerConfig().safeCollect {
                touchControlId.value = it.touchControllerID
            }
        }
        refreshTouchPreferences()
    }

    private fun refreshTouchPreferences() {
        touchPreferencesJob?.cancel()
        touchPreferencesJob = scope.launch {
            val mode = HapticFeedbackMode.parse(settingsManager.hapticFeedbackMode())
            val intensity = settingsManager.vibrationIntensity()
            val opacity = settingsManager.virtualPadOpacity()
            val tint = GamepadColor.tint(settingsManager.gamepadColor(), settingsManager.accentColor())
            hapticFeedbackMode.value = mode
            vibrationIntensity.value = if (intensity.isFinite()) intensity.coerceIn(0f, 1f) else 0f
            virtualPadOpacity.value = opacity
            gamepadTint.value = tint
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefListener)
        touchPreferencesJob?.cancel()
        super.onDestroy(owner)
    }

    fun getTouchControlsSettings(): Flow<TouchControllerSettingsManager.Settings?> {
        return combine(
            touchControlId,
            screenOrientation,
        ) { touchControlId, orientation -> touchControlId to orientation }
            .flatMapLatest { (touchControlId, orientation) ->
                touchControllerSettingsManager.observeSettings(touchControlId, orientation)
            }
    }

    fun getTouchHapticFeedbackMode(): Flow<HapticFeedbackMode> {
        return hapticFeedbackMode
    }

    fun getTouchVibrationIntensity(): Flow<Float> = vibrationIntensity

    fun getVirtualPadOpacity(): Flow<Float> {
        return virtualPadOpacity
    }

    /** The on-screen gamepad's tint, or null for the default grey pad. */
    fun getGamepadTint(): Flow<Color?> = gamepadTint

    fun updateTouchControllerSettings(touchControllerSettings: TouchControllerSettingsManager.Settings) {
        scope.launch {
            touchControllerSettingsManager.storeSettings(
                touchControlId.value,
                screenOrientation.value,
                touchControllerSettings,
            )
        }
    }

    fun resetTouchControls() {
        scope.launch {
            touchControllerSettingsManager.resetSettings(
                touchControlId.value,
                screenOrientation.value,
            )
        }
    }

    fun getGameAspectRatio(): Flow<Float> {
        return combine(screenOrientation, screenLayoutRevision) { orientation, _ ->
            screenLayoutController.getAspectRatio(orientation)
        }.distinctUntilChanged()
    }

    /** DS/3DS single-screen layouts fill the display with pads overlaid, like PSP. */
    fun isSingleScreenLayout(): Flow<Boolean> =
        screenLayoutRevision
            .map { screenLayoutController.isSingleScreen() }
            .distinctUntilChanged()

    fun getTouchScreenRegion(): Flow<TouchScreenRegion?> =
        combine(screenOrientation, screenLayoutRevision) { orientation, _ ->
            screenLayoutController.getTouchScreenRegion(orientation)
        }.distinctUntilChanged()

    fun refreshLayout() {
        screenLayoutRevision.value++
    }

    /**
     * On-screen layout button and gamepad shortcut. DS/3DS: dual -> dual swapped -> top only ->
     * bottom only. Wii U: TV <-> dual.
     */
    fun cycleScreenLayout() {
        if (!screenLayoutController.supportsToggle()) return
        val variables = screenLayoutController.cycle(screenOrientation.value)
        screenLayoutRevision.value++
        if (variables.isNotEmpty()) {
            retroGameView.applyCoreVariables(variables)
        }
    }

    fun updateScreenOrientation(orientation: TouchControllerSettingsManager.Orientation) {
        screenOrientation.value = orientation
        screenLayoutRevision.value++
        if (hasTouchScreen && screenLayoutController.supportsToggle()) {
            val variables = screenLayoutController.onOrientationChanged(orientation)
            if (variables.isNotEmpty()) {
                retroGameView.applyCoreVariables(variables)
            }
        }
    }

    fun isTouchControllerVisible(): Flow<Boolean> {
        if (TVHelper.isTV(appContext)) {
            return flowOf(false)
        }
        return combine(
            inputs.getEnabledInputDevices(),
            forceHideTouchControls,
        ) { devices, forceHide ->
            devices.isEmpty() && !forceHide
        }
    }

    fun isForceHideTouchControls(): Flow<Boolean> = forceHideTouchControls

    fun toggleForceHideTouchControls() {
        forceHideTouchControls.value = !forceHideTouchControls.value
    }

    fun showTouchControls() {
        forceHideTouchControls.value = false
    }

    fun getTouchControllerConfig(): Flow<ControllerConfig> {
        return inputs.getControllerConfigState()
            .map { it[0] }
            .filterNotNull()
            .distinctUntilChanged()
    }

    fun handleVirtualInputEvent(events: List<InputEvent>) {
        val menuEvent = events.firstOrNull { it is InputEvent.Button && it.id == KeyEvent.KEYCODE_BUTTON_MODE }
        if (menuEvent != null) {
            onMenuPressed((menuEvent as InputEvent.Button).pressed)
        }

        events.forEach { event ->
            when (event) {
                is InputEvent.Button -> {
                    if (handleHostButton(event)) {
                        return@forEach
                    }
                    handleVirtualInputButton(event)
                }

                is InputEvent.DiscreteDirection -> {
                    val dir = if (event.direction.isSpecified) event.direction else Offset.Zero
                    handleVirtualInputDirection(event.id, dir.x, -dir.y)
                }

                is InputEvent.ContinuousDirection -> {
                    val dir = if (event.direction.isSpecified) event.direction else Offset.Zero
                    handleVirtualInputDirection(event.id, dir.x, -dir.y)
                }
            }
        }
    }

    private fun handleHostButton(event: InputEvent.Button): Boolean {
        if (!hasTouchScreen) {
            return event.id == ComposeTouchLayouts.HOST_KEY_SCREEN_LAYOUT ||
                event.id == ComposeTouchLayouts.HOST_KEY_HIDE_PADS ||
                event.id == ComposeTouchLayouts.HOST_KEY_CLOSE_LID
        }

        return when (event.id) {
            ComposeTouchLayouts.HOST_KEY_SCREEN_LAYOUT -> {
                if (event.pressed && screenLayoutController.supportsToggle()) {
                    playTouchHapticForButton(true)
                    cycleScreenLayout()
                }
                true
            }
            ComposeTouchLayouts.HOST_KEY_HIDE_PADS -> {
                if (event.pressed) {
                    playTouchHapticForButton(true)
                    toggleForceHideTouchControls()
                }
                true
            }
            ComposeTouchLayouts.HOST_KEY_CLOSE_LID -> {
                playTouchHapticForButton(event.pressed)
                val action = if (event.pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
                if (systemCoreConfig.coreID == CoreID.MELONDS_DS) {
                    if (event.pressed) {
                        retroGameView.retroGameView?.sendKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_L2)
                        retroGameView.retroGameView?.sendKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_Y)
                    } else {
                        retroGameView.retroGameView?.sendKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_Y)
                        retroGameView.retroGameView?.sendKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_L2)
                    }
                } else {
                    retroGameView.retroGameView?.sendKeyEvent(action, KeyEvent.KEYCODE_BUTTON_THUMBL)
                }
                true
            }
            else -> false
        }
    }

    private fun playTouchHapticForButton(pressed: Boolean) {
        val mode = hapticFeedbackMode.value
        if (mode == HapticFeedbackMode.NONE) return
        if (!pressed && mode != HapticFeedbackMode.PRESS_RELEASE) return
        hostHapticFeedback.generate(
            if (pressed) HapticEffect.PRESS else HapticEffect.RELEASE,
            vibrationIntensity.value,
        )
    }

    private fun onMenuPressed(pressed: Boolean) {
        menuPressed.value = pressed

        if (pressed) {
            loadingMenuJob?.cancel()
            loadingMenuJob =
                scope.launch {
                    delay(MENU_LOADING_ANIMATION_MILLIS.toLong())
                    sideEffects.showMenu(tilt, inputs)
                }
        } else {
            loadingMenuJob?.cancel()
            loadingMenuJob = null
        }
    }

    fun isMenuPressed(): Flow<Boolean> {
        return menuPressed
    }

    fun isEditControlsShown(): Flow<Boolean> {
        return showEditControls
    }

    fun showEditControls(show: Boolean) {
        showEditControls.value = show
    }

    private fun handleVirtualInputButton(event: InputEvent.Button) {
        val action = if (event.pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        retroGameView.retroGameView?.sendKeyEvent(action, event.id)
    }

    private fun handleVirtualInputDirection(
        id: Int,
        xAxis: Float,
        yAxis: Float,
    ) {
        when (id) {
            ComposeTouchLayouts.MOTION_SOURCE_DPAD -> {
                retroGameView.retroGameView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, xAxis, yAxis)
            }

            ComposeTouchLayouts.MOTION_SOURCE_LEFT_STICK -> {
                retroGameView.retroGameView?.sendMotionEvent(
                    MOTION_SOURCE_ANALOG_LEFT,
                    xAxis,
                    yAxis,
                )
            }

            ComposeTouchLayouts.MOTION_SOURCE_RIGHT_STICK -> {
                retroGameView.retroGameView?.sendMotionEvent(
                    MOTION_SOURCE_ANALOG_RIGHT,
                    xAxis,
                    yAxis,
                )
            }

            ComposeTouchLayouts.MOTION_SOURCE_DPAD_AND_LEFT_STICK -> {
                retroGameView.retroGameView?.sendMotionEvent(
                    MOTION_SOURCE_ANALOG_LEFT,
                    xAxis,
                    yAxis,
                )
                retroGameView.retroGameView?.sendMotionEvent(MOTION_SOURCE_DPAD, xAxis, yAxis)
            }

            ComposeTouchLayouts.MOTION_SOURCE_RIGHT_DPAD -> {
                retroGameView.retroGameView?.sendMotionEvent(
                    MOTION_SOURCE_ANALOG_RIGHT,
                    xAxis,
                    yAxis,
                )
            }
        }
    }

    companion object {
        const val MENU_LOADING_ANIMATION_MILLIS = 500
        private const val DEFAULT_PAD_OPACITY = 1f
    }
}
