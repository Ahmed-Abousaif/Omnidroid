package com.omnidroid.app.shared.rumble

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.InputDevice
import com.omnidroid.app.mobile.feature.settings.SettingsManager
import com.omnidroid.app.shared.input.InputDeviceManager
import com.omnidroid.common.coroutines.safeCollect
import com.omnidroid.lib.library.SystemCoreConfig
import com.swordfish.libretrodroid.RumbleEvent
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.newSingleThreadContext
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class RumbleManager(
    applicationContext: Context,
    private val settingsManager: SettingsManager,
    private val inputDeviceManager: InputDeviceManager,
) {
    private val deviceVibrator = applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val rumbleContext = newSingleThreadContext("Rumble")

    suspend fun collectAndProcessRumbleEvents(
        systemCoreConfig: SystemCoreConfig,
        rumbleEventsObservable: Flow<RumbleEvent>,
    ) {
        val enableRumble = settingsManager.enableRumble()
        val rumbleSupported = systemCoreConfig.rumbleSupported
        val vibrationIntensity = settingsManager.vibrationIntensity()

        if (!enableRumble && rumbleSupported) {
            return
        }

        inputDeviceManager.getEnabledInputsObservable()
            .map { getVibrators(it) }
            .flatMapLatest { vibrators ->
                rumbleEventsObservable
                    .onEach { kotlin.runCatching { vibrate(vibrators[it.port], it, vibrationIntensity) } }
                    .onStart { stopAllVibrators(vibrators) }
                    .onCompletion { stopAllVibrators(vibrators) }
                    .flowOn(rumbleContext)
            }
            .safeCollect { }
    }

    private fun stopAllVibrators(vibrators: List<Vibrator>) {
        vibrators.forEach {
            kotlin.runCatching { it.cancel() }
        }
    }

    private suspend fun getVibrators(gamePads: List<InputDevice>): List<Vibrator> {
        val enableDeviceRumble = settingsManager.enableDeviceRumble()

        return if (gamePads.isEmpty() && enableDeviceRumble) {
            listOf(deviceVibrator)
        } else {
            gamePads.map { it.vibrator }
        }
    }

    private fun vibrate(
        vibrator: Vibrator?,
        rumbleEvent: RumbleEvent,
        intensity: Float,
    ) {
        if (vibrator == null) return

        vibrator.cancel()

        val amplitude = computeAmplitude(rumbleEvent, intensity)

        if (amplitude == 0) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && vibrator.hasAmplitudeControl()) {
            vibrator.vibrate(VibrationEffect.createOneShot(MAX_RUMBLE_DURATION_MS, amplitude))
        } else if (amplitude > LEGACY_MIN_RUMBLE_STRENGTH) {
            vibrator.vibrate(MAX_RUMBLE_DURATION_MS)
        }
    }

    private fun computeAmplitude(
        rumbleEvent: RumbleEvent,
        intensity: Float,
    ): Int {
        val strength = rumbleEvent.strengthStrong * 0.66f + rumbleEvent.strengthWeak * 0.33f
        return (intensity * strength * 255).roundToInt()
    }

    companion object {
        const val MAX_RUMBLE_DURATION_MS = 1000L
        const val DEFAULT_RUMBLE_STRENGTH = 0.5f
        const val LEGACY_MIN_RUMBLE_STRENGTH = 100
    }
}
