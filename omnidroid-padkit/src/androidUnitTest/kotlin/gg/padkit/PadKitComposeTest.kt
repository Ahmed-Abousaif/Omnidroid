package gg.padkit

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import gg.padkit.config.HapticFeedbackType
import gg.padkit.controls.ControlAnalog
import gg.padkit.controls.ControlButton
import gg.padkit.controls.ControlCross
import gg.padkit.controls.ControlFaceButtons
import gg.padkit.ids.Id
import gg.padkit.inputevents.InputEvent
import gg.padkit.inputstate.InputState
import kotlinx.collections.immutable.persistentListOf
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
class PadKitComposeTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<InputEvent>()

    @Test
    fun removingHeldControlReleasesItWithoutAnotherPointerEvent() {
        val visible = mutableStateOf(true)
        rule.setContent {
            PadKit(onInputEvents = { events.addAll(it) }, hapticFeedbackType = HapticFeedbackType.NONE) {
                if (visible.value) ControlButton(Modifier.size(100.dp).testTag("button"), Id.Key(1))
            }
        }
        rule.onNodeWithTag("button").performTouchInput { down(center) }
        rule.runOnIdle { visible.value = false }
        rule.runOnIdle {
            assertEquals(listOf(InputEvent.Button(1, true), InputEvent.Button(1, false)), events)
        }
        rule.onRoot().performTouchInput { up() }
    }

    @Test
    fun disposingEntirePadReleasesTouchAndSimulation() {
        val visible = mutableStateOf(true)
        val simulated = mutableStateOf(InputState().setDigitalKey(Id.Key(2), true))
        rule.setContent {
            if (visible.value) {
                PadKit(
                    onInputEvents = { events.addAll(it) },
                    hapticFeedbackType = HapticFeedbackType.NONE,
                    simulatedState = simulated,
                    simulatedControlIds = mutableStateOf(setOf<Id>(Id.Key(2))),
                ) {
                    ControlButton(Modifier.size(100.dp).testTag("button"), Id.Key(1))
                }
            } else {
                Box(Modifier.size(100.dp))
            }
        }
        rule.onNodeWithTag("button").performTouchInput { down(center) }
        rule.runOnIdle { visible.value = false }
        rule.runOnIdle {
            assertTrue(events.contains(InputEvent.Button(1, false)))
            assertTrue(events.contains(InputEvent.Button(2, false)))
        }
        rule.onRoot().performTouchInput { up() }
    }

    @Test
    fun removingOneControlDoesNotReleaseAnotherHeldFinger() {
        val visible = mutableStateOf(true)
        rule.setContent {
            PadKit(
                modifier = Modifier.size(200.dp, 100.dp).testTag("pad"),
                onInputEvents = { events.addAll(it) },
                hapticFeedbackType = HapticFeedbackType.NONE,
            ) {
                Row {
                    if (visible.value) ControlButton(Modifier.size(100.dp), Id.Key(1)) else Box(Modifier.size(100.dp))
                    ControlButton(Modifier.size(100.dp), Id.Key(2))
                }
            }
        }
        rule.onNodeWithTag("pad").performTouchInput {
            down(0, Offset(width * 0.25f, height * 0.5f))
            down(1, Offset(width * 0.75f, height * 0.5f))
        }
        rule.runOnIdle { visible.value = false }
        rule.runOnIdle {
            assertTrue(events.contains(InputEvent.Button(1, false)))
            assertFalse(events.contains(InputEvent.Button(2, false)))
        }
        rule.onNodeWithTag("pad").performTouchInput { cancel() }
        rule.runOnIdle { assertEquals(InputEvent.Button(2, false), events.last()) }
    }

    @Test
    fun cancelledGestureReleasesStickAndNextGestureStartsCentered() {
        var state = InputState()
        rule.setContent {
            PadKit(onInputStateUpdated = { state = it }, hapticFeedbackType = HapticFeedbackType.NONE) {
                ControlAnalog(Modifier.size(100.dp).testTag("stick"), Id.ContinuousDirection(1))
            }
        }
        rule.onNodeWithTag("stick").performTouchInput {
            down(center)
            moveTo(center + Offset(30f, 0f))
            cancel()
        }
        rule.runOnIdle { assertEquals(InputState(), state) }
        rule.onNodeWithTag("stick").performTouchInput { down(center + Offset(20f, 0f)) }
        rule.runOnIdle { assertEquals(Offset.Zero, state.getContinuousDirection(Id.ContinuousDirection(1))) }
        rule.onNodeWithTag("stick").performTouchInput { up() }
    }

    @Test
    fun changingButtonIdReleasesOldMappingAndUpdatesPressedVisual() {
        val id = mutableStateOf(Id.Key(1))
        rule.setContent {
            PadKit(onInputEvents = { events.addAll(it) }, hapticFeedbackType = HapticFeedbackType.NONE) {
                ControlButton(
                    modifier = Modifier.size(100.dp).testTag("button"),
                    id = id.value,
                    foreground = {
                            pressed ->
                        Box(Modifier.fillMaxSize().testTag("visual-${id.value.value}-${pressed.value}"))
                    },
                )
            }
        }
        rule.onNodeWithTag("button").performTouchInput { down(center) }
        rule.onNodeWithTag("visual-1-true").assertExists()
        rule.runOnIdle { id.value = Id.Key(2) }
        rule.onNodeWithTag("visual-2-false").assertExists()
        rule.runOnIdle { assertTrue(events.contains(InputEvent.Button(1, false))) }
        rule.onNodeWithTag("button").performTouchInput {
            up()
            down(center)
            up()
        }
        rule.runOnIdle {
            assertTrue(events.contains(InputEvent.Button(2, true)))
            assertEquals(InputEvent.Button(2, false), events.last())
        }
    }

    @Test
    fun changingAnalogIdUpdatesHandlerAndPressedVisual() {
        val id = mutableStateOf(Id.ContinuousDirection(1))
        var state = InputState()
        rule.setContent {
            PadKit(onInputStateUpdated = { state = it }, hapticFeedbackType = HapticFeedbackType.NONE) {
                ControlAnalog(
                    modifier = Modifier.size(100.dp).testTag("stick"),
                    id = id.value,
                    foreground = { pressed -> Box(Modifier.fillMaxSize().testTag("stick-${pressed.value}")) },
                )
            }
        }
        rule.onNodeWithTag("stick").performTouchInput {
            down(center)
            moveTo(center + Offset(20f, 0f))
        }
        rule.runOnIdle { id.value = Id.ContinuousDirection(2) }
        rule.onNodeWithTag("stick-false").assertExists()
        rule.runOnIdle { assertEquals(Offset.Unspecified, state.getContinuousDirection(Id.ContinuousDirection(1))) }
        rule.onNodeWithTag("stick").performTouchInput {
            up()
            down(center)
            moveTo(center + Offset(20f, 0f))
        }
        rule.runOnIdle { assertTrue(state.getContinuousDirection(Id.ContinuousDirection(2)).x > 0f) }
        rule.onNodeWithTag("stick").performTouchInput { up() }
    }

    @Test
    fun changingAnalogIdUpdatesThumbPositionFromSimulatedState() {
        val id = mutableStateOf(Id.ContinuousDirection(1))
        val simulation =
            mutableStateOf(
                InputState().setContinuousDirection(Id.ContinuousDirection(1), Offset(1f, 0f))
                    .setContinuousDirection(Id.ContinuousDirection(2), Offset(-1f, 0f)),
            )
        rule.setContent {
            PadKit(
                hapticFeedbackType = HapticFeedbackType.NONE,
                simulatedState = simulation,
                simulatedControlIds = mutableStateOf(setOf<Id>(Id.ContinuousDirection(1), Id.ContinuousDirection(2))),
            ) {
                ControlAnalog(Modifier.size(100.dp), id.value, foreground = {
                    Box(Modifier.fillMaxSize().testTag("thumb"))
                })
            }
        }
        val before = rule.onNodeWithTag("thumb").fetchSemanticsNode().boundsInRoot.center.x
        rule.runOnIdle { id.value = Id.ContinuousDirection(2) }
        val after = rule.onNodeWithTag("thumb").fetchSemanticsNode().boundsInRoot.center.x
        assertTrue(before > after)
    }

    @Test
    fun changingCrossIdUpdatesDirectionVisualAtSameCompositionSlot() {
        val id = mutableStateOf(Id.DiscreteDirection(1))
        val simulated =
            mutableStateOf(
                InputState().setDiscreteDirection(Id.DiscreteDirection(1), Offset(1f, 0f))
                    .setDiscreteDirection(Id.DiscreteDirection(2), Offset(-1f, 0f)),
            )
        rule.setContent {
            PadKit(
                hapticFeedbackType = HapticFeedbackType.NONE,
                simulatedState = simulated,
                simulatedControlIds = mutableStateOf(setOf<Id>(Id.DiscreteDirection(1), Id.DiscreteDirection(2))),
            ) {
                ControlCross(Modifier.size(100.dp), id.value, foreground = { direction ->
                    Box(Modifier.fillMaxSize().testTag("cross-${direction.value.x}"))
                })
            }
        }
        rule.onNodeWithTag("cross-1.0").assertExists()
        rule.runOnIdle { id.value = Id.DiscreteDirection(2) }
        rule.onNodeWithTag("cross--1.0").assertExists()
    }

    @Test
    fun changingFaceKeysUpdatesPrimaryAndCompositePressedVisuals() {
        val ids = mutableStateOf(persistentListOf(Id.Key(1), Id.Key(2)))
        val simulated = mutableStateOf(InputState().setDigitalKey(Id.Key(1), true).setDigitalKey(Id.Key(2), true))
        rule.setContent {
            PadKit(
                hapticFeedbackType = HapticFeedbackType.NONE,
                simulatedState = simulated,
                simulatedControlIds = mutableStateOf(setOf<Id>(Id.Key(1), Id.Key(2), Id.Key(3), Id.Key(4))),
            ) {
                ControlFaceButtons(
                    modifier = Modifier.size(150.dp),
                    ids = ids.value,
                    foreground = {
                            key,
                            pressed,
                        ->
                        Box(Modifier.fillMaxSize().testTag("face-${key.value}-${pressed.value}"))
                    },
                    foregroundComposite = {
                            pressed ->
                        Box(Modifier.fillMaxSize().testTag("composite-${pressed.value}"))
                    },
                )
            }
        }
        rule.onNodeWithTag("face-1-true").assertExists()
        rule.onAllNodesWithTag("composite-true").assertCountEquals(2)
        rule.runOnIdle { ids.value = persistentListOf(Id.Key(3), Id.Key(4)) }
        rule.onNodeWithTag("face-3-false").assertExists()
        rule.onNodeWithTag("face-4-false").assertExists()
        rule.onAllNodesWithTag("composite-false").assertCountEquals(2)
    }

    @Test
    fun changingOnlyOverrideIdsUpdatesOutputWithoutTouch() {
        val ids = mutableStateOf(setOf<Id>(Id.Key(1)))
        val simulated = mutableStateOf(InputState().setDigitalKey(Id.Key(1), true).setDigitalKey(Id.Key(2), true))
        rule.setContent {
            PadKit(
                onInputEvents = { events.addAll(it) },
                hapticFeedbackType = HapticFeedbackType.NONE,
                simulatedState = simulated,
                simulatedControlIds = ids,
            ) {}
        }
        rule.runOnIdle { ids.value = setOf(Id.Key(2)) }
        rule.runOnIdle {
            assertTrue(events.contains(InputEvent.Button(1, false)))
            assertTrue(events.contains(InputEvent.Button(2, true)))
        }
    }

    @Test
    fun replacingSimulationStateObjectIsNotUndoneByNextTouch() {
        val simulation = mutableStateOf<State<InputState>>(mutableStateOf(InputState()))
        var state = InputState()
        rule.setContent {
            PadKit(
                onInputStateUpdated = { state = it },
                hapticFeedbackType = HapticFeedbackType.NONE,
                simulatedState = simulation.value,
                simulatedControlIds = mutableStateOf(setOf<Id>(Id.Key(1))),
            ) { ControlButton(Modifier.size(100.dp).testTag("button"), Id.Key(2)) }
        }
        rule.runOnIdle { simulation.value = mutableStateOf(InputState().setDigitalKey(Id.Key(1), true)) }
        rule.onNodeWithTag("button").performTouchInput {
            down(center)
            up()
        }
        rule.runOnIdle { assertTrue(state.getDigitalKey(Id.Key(1))) }
    }

    @Test
    fun disabledHapticsNeverRequestsVibrationService() {
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
                PadKit(hapticFeedbackType = HapticFeedbackType.NONE) {
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
