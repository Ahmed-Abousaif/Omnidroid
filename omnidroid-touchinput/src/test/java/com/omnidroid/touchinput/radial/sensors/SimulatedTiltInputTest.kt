package com.omnidroid.touchinput.radial.sensors

import androidx.compose.ui.geometry.Offset
import gg.padkit.ids.Id
import gg.padkit.inputstate.InputState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedTiltInputTest {
    @Test
    fun switchingModeReleasesInputBeforeSuspendingSensorSetup() =
        runTest {
            val configurations = MutableStateFlow<TiltConfiguration>(TiltConfiguration.Cross(0))
            val readings = MutableSharedFlow<FloatArray>()
            val states = mutableListOf<InputState>()
            var setups = 0
            val job =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    configurations.toSimulatedInputStates {
                        setups++
                        if (setups == 2) delay(1_000)
                        readings
                    }.collect { states.add(it) }
                }
            runCurrent()
            readings.emit(floatArrayOf(1f, 0f))
            runCurrent()
            configurations.value = TiltConfiguration.Analog(2)
            runCurrent()
            assertEquals(2, setups)
            assertEquals(InputState(), states.last())
            advanceTimeBy(1_000)
            runCurrent()
            readings.emit(floatArrayOf(0.5f, -0.4f))
            runCurrent()
            assertEquals(Offset(0.5f, 0.4f), states.last().getContinuousDirection(Id.ContinuousDirection(2)))
            job.cancelAndJoin()
        }

    @Test
    fun disabledModeEmitsNeutralWithoutStartingSensor() =
        runTest {
            val configurations = MutableStateFlow<TiltConfiguration>(TiltConfiguration.Disabled)
            val states = mutableListOf<InputState>()
            var starts = 0
            val job =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    configurations.toSimulatedInputStates {
                        starts++
                        MutableSharedFlow()
                    }.collect { states.add(it) }
                }
            runCurrent()
            assertEquals(listOf(InputState()), states)
            assertEquals(0, starts)
            job.cancelAndJoin()
        }

    @Test
    fun disablingHeldAnalogEmitsNeutralAndStopsOldSensorFlow() =
        runTest {
            val configurations = MutableStateFlow<TiltConfiguration>(TiltConfiguration.Analog(1))
            val readings = MutableSharedFlow<FloatArray>()
            val states = mutableListOf<InputState>()
            var active = 0
            val job =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    configurations.toSimulatedInputStates {
                        readings.onStart { active++ }.onCompletion { active-- }
                    }.collect { states.add(it) }
                }
            runCurrent()
            readings.emit(floatArrayOf(0.8f, 0f))
            runCurrent()
            val held = states.last().getContinuousDirection(Id.ContinuousDirection(1))
            assertEquals(0.8f, held.x, 0f)
            assertEquals(0f, held.y, 0f)
            configurations.value = TiltConfiguration.Disabled
            runCurrent()
            assertEquals(InputState(), states.last())
            assertEquals(0, active)
            job.cancelAndJoin()
        }

    @Test
    fun switchingEnabledModesReleasesPreviousMappingBeforeNewReading() =
        runTest {
            val configurations = MutableStateFlow<TiltConfiguration>(TiltConfiguration.Cross(0))
            val readings = MutableSharedFlow<FloatArray>()
            val states = mutableListOf<InputState>()
            var active = 0
            val job =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    configurations.toSimulatedInputStates {
                        readings.onStart { active++ }.onCompletion { active-- }
                    }.collect { states.add(it) }
                }
            runCurrent()
            readings.emit(floatArrayOf(1f, 0f))
            runCurrent()
            assertEquals(Offset(1f, 0f), states.last().getDiscreteDirection(Id.DiscreteDirection(0)))
            configurations.value = TiltConfiguration.Analog(2)
            runCurrent()
            assertEquals(InputState(), states.last())
            assertEquals(1, active)
            readings.emit(floatArrayOf(0.5f, -0.4f))
            runCurrent()
            assertEquals(Offset.Unspecified, states.last().getDiscreteDirection(Id.DiscreteDirection(0)))
            assertEquals(Offset(0.5f, 0.4f), states.last().getContinuousDirection(Id.ContinuousDirection(2)))
            job.cancelAndJoin()
            assertEquals(0, active)
        }

    @Test
    fun disablingButtonPairReleasesDigitalStateWithoutAnotherReading() =
        runTest {
            val configurations = MutableStateFlow<TiltConfiguration>(TiltConfiguration.ButtonPair(4, 5))
            val readings = MutableSharedFlow<FloatArray>()
            val states = mutableListOf<InputState>()
            val job =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    configurations.toSimulatedInputStates { readings }.collect { states.add(it) }
                }
            runCurrent()
            readings.emit(floatArrayOf(-1f, 0f))
            runCurrent()
            assertTrue(states.last().getDigitalKey(Id.Key(4)))
            configurations.value = TiltConfiguration.Disabled
            runCurrent()
            assertEquals(InputState(), states.last())
            job.cancelAndJoin()
        }
}
