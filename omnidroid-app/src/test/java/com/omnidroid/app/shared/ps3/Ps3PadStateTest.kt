package com.omnidroid.app.shared.ps3

import com.omnidroid.lib.backend.NormalizedKey
import com.omnidroid.lib.backend.NormalizedMotionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ps3PadStateTest {
    @Test fun faceButtonsMatchPlayStationTouchLabels() {
        // PS2Right (also used for PS3) emits these logical keys for its symbols.
        // Expected bits are CELL_PAD_CTRL_* from the PS3 controller protocol.
        val buttons =
            listOf(
                NormalizedKey.BUTTON_B to 0x40, // Cross
                NormalizedKey.BUTTON_A to 0x20, // Circle
                NormalizedKey.BUTTON_X to 0x10, // Triangle
                NormalizedKey.BUTTON_Y to 0x80, // Square
            )
        val pad = Ps3PadState()
        for ((key, expected) in buttons) {
            assertTrue(pad.button(key, true))
            assertEquals(expected, pad.digital2)
            assertEquals(0, pad.digital1)
            assertTrue(pad.button(key, false))
            assertEquals(0, pad.digital2)
        }
    }

    @Test fun hostMenuNeverBecomesANativePsButton() {
        val pad = Ps3PadState()
        pad.button(NormalizedKey.DPAD_UP, true)
        assertFalse(pad.button(NormalizedKey.BUTTON_HOME, true))
        assertFalse(pad.button(NormalizedKey.BUTTON_HOME, false))
        assertEquals(16, pad.digital1)
    }

    @Test fun simultaneousButtonsSurviveStickAndReleaseEvents() {
        val pad = Ps3PadState()
        pad.button(NormalizedKey.BUTTON_A, true)
        pad.button(NormalizedKey.TRIGGER_R1, true)
        pad.stick(NormalizedMotionSource.LeftStick, 1f, -1f)
        assertEquals(32 or 8, pad.digital2)
        assertEquals(255, pad.lx)
        assertEquals(0, pad.ly)
        pad.button(NormalizedKey.BUTTON_A, false)
        assertEquals(8, pad.digital2)
        pad.release()
        assertEquals(0, pad.digital2)
        assertEquals(128, pad.lx)
        assertEquals(128, pad.ly)
    }

    @Test fun padsHaveIndependentStateAndAxesAreClamped() {
        val first = Ps3PadState()
        val second = Ps3PadState()
        first.button(NormalizedKey.DPAD_LEFT, true)
        first.stick(NormalizedMotionSource.RightStick, -4f, 4f)
        assertEquals(128, first.digital1)
        assertEquals(0, second.digital1)
        assertEquals(0, first.rx)
        assertEquals(255, first.ry)
    }
}
