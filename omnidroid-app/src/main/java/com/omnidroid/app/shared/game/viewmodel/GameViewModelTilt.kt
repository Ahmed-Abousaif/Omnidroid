package com.omnidroid.app.shared.game.viewmodel

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.omnidroid.app.mobile.feature.settings.SettingsManager
import com.omnidroid.touchinput.radial.sensors.TiltConfiguration
import com.omnidroid.touchinput.radial.sensors.TiltSensor
import com.omnidroid.touchinput.radial.sensors.toSimulatedInputStates
import gg.padkit.inputstate.InputState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

class GameViewModelTilt(
    appContext: Context,
    private val settingsManager: SettingsManager,
) : DefaultLifecycleObserver {
    private val tiltSensor = TiltSensor(appContext)
    private val tiltConfiguration = MutableStateFlow<TiltConfiguration>(TiltConfiguration.Disabled)

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        tiltSensor.isAllowedToRun = true
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        tiltSensor.isAllowedToRun = false
    }

    fun changeTiltConfiguration(configuration: TiltConfiguration) {
        tiltConfiguration.value = configuration
    }

    fun getTiltConfiguration(): Flow<TiltConfiguration> {
        return tiltConfiguration
    }

    fun getSimulatedTiltEvents(): Flow<InputState> {
        return tiltConfiguration
            .toSimulatedInputStates {
                tiltSensor.setSensitivity(settingsManager.tiltSensitivity())
                tiltSensor.getTiltEvents()
                    .onStart { tiltSensor.shouldRun = true }
                    .onCompletion { tiltSensor.shouldRun = false }
            }
    }
}
