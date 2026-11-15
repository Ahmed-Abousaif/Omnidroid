package com.omnidroid.touchinput.radial.utils

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalogDpadStateTest {
    @Test
    fun eightWayModePreservesAllDirectionsAndPositiveUp() {
        val directions =
            listOf(
                Offset(1f, 0f),
                Offset(1f, 1f),
                Offset(0f, 1f),
                Offset(-1f, 1f),
                Offset(-1f, 0f),
                Offset(-1f, -1f),
                Offset(0f, -1f),
                Offset(1f, -1f),
            )
        directions.forEach { assertEquals(it, quantizeAnalogDpad(it, true)) }
    }

    @Test
    fun fourWayModeChoosesDominantAxisWithHorizontalTies() {
        assertEquals(Offset(1f, 0f), quantizeAnalogDpad(Offset(0.8f, 0.5f), false))
        assertEquals(Offset(-1f, 0f), quantizeAnalogDpad(Offset(-0.8f, -0.5f), false))
        assertEquals(Offset(0f, 1f), quantizeAnalogDpad(Offset(0.5f, 0.8f), false))
        assertEquals(Offset(0f, -1f), quantizeAnalogDpad(Offset(-0.5f, -0.8f), false))
        assertEquals(Offset(1f, 0f), quantizeAnalogDpad(Offset(1f, 1f), false))
        assertEquals(Offset(-1f, 0f), quantizeAnalogDpad(Offset(-1f, -1f), false))
    }

    @Test
    fun invalidOrDeadZoneInputIsNeutralInBothModes() {
        val inputs =
            listOf(
                Offset.Zero,
                Offset(0.1f, 0.1f),
                Offset.Unspecified,
                Offset(Float.POSITIVE_INFINITY, 1f),
                Offset(1f, Float.NaN),
            )
        inputs.forEach { input ->
            assertEquals(Offset.Zero, quantizeAnalogDpad(input, true))
            assertEquals(Offset.Zero, quantizeAnalogDpad(input, false))
        }
        assertEquals(Offset(1f, 0f), quantizeAnalogDpad(Offset(0.25f, 0f), false))
    }

    @Test
    fun policyChangeRecomputesEveryHeldSourceWithoutAnotherInput() {
        val state = AnalogDpadState()
        assertEquals(Offset(1f, 1f), state.update(0, Offset(0.8f, 0.5f)))
        state.update(3, Offset(-0.5f, -0.8f))
        assertEquals(mapOf(0 to Offset(1f, 0f), 3 to Offset(0f, -1f)), state.setAllowDiagonals(false))
        assertTrue(state.setAllowDiagonals(false).isEmpty())
        assertEquals(mapOf(0 to Offset(1f, 1f), 3 to Offset(-1f, -1f)), state.setAllowDiagonals(true))
    }

    @Test
    fun releasedOrInvalidSourcesAreNotResurrectedByPolicyChanges() {
        val state = AnalogDpadState()
        state.update(0, Offset(1f, 1f))
        assertEquals(Offset.Zero, state.update(0, Offset.Unspecified))
        assertTrue(state.setAllowDiagonals(false).isEmpty())
        state.update(0, Offset(1f, 1f))
        assertEquals(Offset.Zero, state.update(0, Offset(Float.NaN, 1f)))
        assertTrue(state.setAllowDiagonals(true).isEmpty())
    }
}
