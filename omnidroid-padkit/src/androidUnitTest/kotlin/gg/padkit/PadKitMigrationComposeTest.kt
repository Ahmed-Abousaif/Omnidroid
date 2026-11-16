package gg.padkit

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import gg.padkit.config.HapticFeedbackSettings
import gg.padkit.config.HapticFeedbackType
import gg.padkit.controls.ControlButton
import gg.padkit.controls.ControlDirectionalAnalog
import gg.padkit.haptics.HapticEffect
import gg.padkit.haptics.HapticFeedback
import gg.padkit.ids.Id
import gg.padkit.inputevents.InputEvent
import gg.padkit.inputstate.InputState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PadKitMigrationComposeTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun heldDirectionalStickRecomputesPolicyWithoutSnappingThumb() {
        val diagonals = mutableStateOf(true)
        val deadZone = mutableStateOf(0.25f)
        var state = InputState()
        val events = mutableListOf<InputEvent>()
        rule.setContent {
            PadKit(onInputStateUpdated = {
                state = it
            }, onInputEvents = { events += it }, hapticFeedbackType = HapticFeedbackType.NONE) {
                ControlDirectionalAnalog(
                    Modifier.size(200.dp).testTag("stick"),
                    Id.DiscreteDirection(1),
                    allowDiagonals = diagonals.value,
                    deadZone = deadZone.value,
                    foreground = { androidx.compose.foundation.layout.Box(Modifier.size(20.dp).testTag("thumb")) },
                )
            }
        }
        rule.onNodeWithTag("stick").performTouchInput {
            down(center)
            moveTo(Offset(width * 0.85f, height * 0.25f))
        }
        val thumb = rule.onNodeWithTag("thumb").getUnclippedBoundsInRoot()
        rule.runOnIdle {
            assertEquals(Offset(1f, 1f), state.getDiscreteDirection(Id.DiscreteDirection(1)))
            diagonals.value = false
        }
        rule.runOnIdle { assertEquals(Offset(1f, 0f), state.getDiscreteDirection(Id.DiscreteDirection(1))) }
        assertEquals(thumb, rule.onNodeWithTag("thumb").getUnclippedBoundsInRoot())
        rule.runOnIdle { deadZone.value = 1f }
        rule.runOnIdle { assertEquals(Offset.Zero, state.getDiscreteDirection(Id.DiscreteDirection(1))) }
        rule.onNodeWithTag("stick").performTouchInput { cancel() }
        rule.runOnIdle {
            assertEquals(Offset.Unspecified, state.getDiscreteDirection(Id.DiscreteDirection(1)))
            assertFalse(events.any { it is InputEvent.ContinuousDirection })
        }
    }

    @Test
    fun directionalSimulationMappingAndDisposalReleaseCorrectIds() {
        val id = mutableStateOf(Id.DiscreteDirection(1))
        val visible = mutableStateOf(true)
        val simulatedIds = mutableStateOf(setOf<Id>(Id.DiscreteDirection(1)))
        val simulation = mutableStateOf(InputState().setDiscreteDirection(Id.DiscreteDirection(1), Offset(-1f, 0f)))
        val events = mutableListOf<InputEvent>()
        var state = InputState()
        rule.setContent {
            PadKit(
                onInputStateUpdated = { state = it },
                onInputEvents = { events += it },
                hapticFeedbackType = HapticFeedbackType.NONE,
                simulatedControlIds = simulatedIds,
                simulatedState = simulation,
            ) {
                if (visible.value) ControlDirectionalAnalog(Modifier.size(200.dp).testTag("stick"), id.value)
            }
        }
        rule.onNodeWithTag("stick").performTouchInput {
            down(center)
            moveTo(Offset(width * 0.85f, height * 0.5f))
        }
        rule.runOnIdle {
            assertEquals(Offset(-1f, 0f), state.getDiscreteDirection(Id.DiscreteDirection(1)))
            simulatedIds.value = emptySet()
        }
        rule.runOnIdle {
            assertEquals(Offset(1f, 0f), state.getDiscreteDirection(Id.DiscreteDirection(1)))
            id.value = Id.DiscreteDirection(2)
        }
        rule.runOnIdle {
            assertEquals(Offset.Unspecified, state.getDiscreteDirection(Id.DiscreteDirection(1)))
            assertTrue(events.contains(InputEvent.DiscreteDirection(1, Offset.Unspecified)))
        }
        rule.onNodeWithTag("stick").performTouchInput { moveTo(center) }
        rule.runOnIdle { visible.value = false }
        rule.runOnIdle { assertEquals(InputState(), state) }
        rule.onRoot().performTouchInput { up() }
    }

    @Test
    fun feedbackUsesLatestIntensityModeAndAdapterWithoutReplayingHeldInput() {
        val settings = mutableStateOf(HapticFeedbackSettings(intensity = 0.5f))
        val mode = mutableStateOf(HapticFeedbackType.PRESS_RELEASE)
        val output = mutableStateOf(1)
        val effects = mutableListOf<Triple<Int, HapticEffect, Float>>()
        rule.setContent {
            val sink = output.value
            PadKit(
                hapticFeedbackType = mode.value,
                hapticFeedbackSettings = settings.value,
                hapticFeedback = HapticFeedback { effect, intensity -> effects += Triple(sink, effect, intensity) },
            ) {
                ControlButton(Modifier.size(100.dp).testTag("button"), Id.Key(1))
            }
        }
        rule.onNodeWithTag("button").performTouchInput { down(center) }
        rule.runOnIdle {
            settings.value = settings.value.copy(intensity = 0.2f)
            output.value = 2
        }
        rule.onNodeWithTag("button").performTouchInput { up() }
        rule.runOnIdle {
            assertEquals(listOf(Triple(1, HapticEffect.PRESS, 0.5f), Triple(2, HapticEffect.RELEASE, 0.2f)), effects)
            mode.value = HapticFeedbackType.NONE
        }
        rule.onNodeWithTag("button").performTouchInput { down(center) }
        rule.runOnIdle { mode.value = HapticFeedbackType.PRESS_RELEASE }
        rule.runOnIdle { assertEquals(2, effects.size) }
        rule.onNodeWithTag("button").performTouchInput { up() }
        rule.runOnIdle { assertEquals(Triple(2, HapticEffect.RELEASE, 0.2f), effects.last()) }
    }

    @Test
    fun zeroIntensityDoesNotRequestPlatformService() {
        var requests = 0
        rule.setContent {
            val baseContext = LocalContext.current
            val context =
                object : ContextWrapper(baseContext) {
                    override fun getApplicationContext(): Context = this

                    override fun getSystemService(name: String): Any? {
                        if (name == Context.VIBRATOR_SERVICE || name == Context.VIBRATOR_MANAGER_SERVICE) requests++
                        return super.getSystemService(name)
                    }
                }
            CompositionLocalProvider(LocalContext provides context) {
                PadKit(hapticFeedbackSettings = HapticFeedbackSettings(intensity = 0f)) {
                    ControlButton(Modifier.size(100.dp).testTag("button"), Id.Key(1))
                }
            }
        }
        rule.onNodeWithTag("button").performTouchInput {
            down(center)
            up()
        }
        rule.runOnIdle { assertEquals(0, requests) }
    }
}
