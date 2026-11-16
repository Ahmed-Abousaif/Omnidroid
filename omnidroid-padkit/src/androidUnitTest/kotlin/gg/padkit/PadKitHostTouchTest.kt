package gg.padkit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import gg.padkit.config.HapticFeedbackType
import gg.padkit.controls.ControlButton
import gg.padkit.ids.Id
import gg.padkit.inputevents.InputEvent
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
class PadKitHostTouchTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun secondFingerAbovePadIsForwardedInRootCoordinatesWhileButtonStaysHeld() {
        val events = mutableListOf<InputEvent>()
        var unhandled = emptyList<UnhandledPointer>()
        rule.setContent {
            Box(Modifier.padding(20.dp)) {
                PadKit(
                    modifier = Modifier.size(300.dp, 200.dp).testTag("pad"),
                    hapticFeedbackType = HapticFeedbackType.NONE,
                    onInputEvents = { events += it },
                    onUnhandledPointers = { unhandled = it },
                ) {
                    Box(Modifier.size(300.dp, 200.dp)) {
                        ControlButton(Modifier.align(Alignment.BottomStart).size(100.dp), Id.Key(1))
                    }
                }
            }
        }
        val bounds = rule.onNodeWithTag("pad").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("pad").performTouchInput {
            down(0, Offset(width / 6f, height * 0.75f))
            down(1, Offset(width / 6f, height * 0.1f))
        }
        rule.runOnIdle {
            assertEquals(listOf(InputEvent.Button(1, true)), events)
            assertEquals(1, unhandled.size)
            assertEquals(bounds.left + bounds.width / 6f, unhandled.single().position.x, 1f)
            assertEquals(bounds.top + bounds.height * 0.1f, unhandled.single().position.y, 1f)
        }
        rule.onNodeWithTag("pad").performTouchInput { up(1) }
        rule.runOnIdle {
            assertTrue(unhandled.isEmpty())
            assertFalse(events.contains(InputEvent.Button(1, false)))
        }
        rule.onNodeWithTag("pad").performTouchInput { cancel() }
        rule.runOnIdle { assertEquals(InputEvent.Button(1, false), events.last()) }
    }

    @Test
    fun disposingPadCancelsHostPointerWithoutAnotherTouch() {
        val visible = mutableStateOf(true)
        var unhandled = emptyList<UnhandledPointer>()
        rule.setContent {
            if (visible.value) {
                PadKit(
                    modifier = Modifier.size(200.dp).testTag("pad"),
                    hapticFeedbackType = HapticFeedbackType.NONE,
                    onUnhandledPointers = { unhandled = it },
                ) {}
            }
        }
        rule.onNodeWithTag("pad").performTouchInput { down(center) }
        rule.runOnIdle { assertEquals(1, unhandled.size) }
        rule.runOnIdle { visible.value = false }
        rule.runOnIdle { assertTrue(unhandled.isEmpty()) }
    }
}
