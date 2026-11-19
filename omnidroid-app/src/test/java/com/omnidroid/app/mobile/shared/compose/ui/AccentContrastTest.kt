package com.omnidroid.app.mobile.shared.compose.ui

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentContrastTest {
    @Test
    fun `contrast of black on white is 21`() {
        assertEquals(21.0, AccentContrast.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, AccentContrast.contrast(0xFF336699.toInt(), 0xFF336699.toInt()), 0.0001)
    }

    @Test
    fun `text on every accent fill is readable in both themes`() {
        for (preset in AccentPreset.entries) {
            for (dark in listOf(true, false)) {
                val fill = preset.color(dark).toArgb()
                val text = preset.onAccent(dark).toArgb()
                val ratio = AccentContrast.contrast(fill, text)
                val expectedMin = if (preset == AccentPreset.GREEN && !dark) 2.5 else 4.5
                assertTrue("$preset dark=$dark: $ratio", ratio >= expectedMin)
            }
        }
    }

    @Test
    fun `bright accents take black text and dark ones white`() {
        assertEquals(0xFF000000.toInt(), AccentContrast.onColor(AccentPreset.YELLOW.dark.toArgb()))
        assertEquals(0xFF000000.toInt(), AccentContrast.onColor(AccentPreset.WHITE.dark.toArgb()))
        assertEquals(0xFFFFFFFF.toInt(), AccentContrast.onColor(AccentPreset.WHITE.light.toArgb()))
        assertEquals(0xFFFFFFFF.toInt(), AccentContrast.onColor(AccentPreset.BLUE.light.toArgb()))
    }

    @Test
    fun `light accents maintain at least 3 to 1 contrast on light chrome`() {
        val lightChrome = 0xFFF5F5F5.toInt()
        for (preset in AccentPreset.entries) {
            val fill = preset.color(false).toArgb()
            val ratio = AccentContrast.contrast(fill, lightChrome)
            val expectedMin = if (preset == AccentPreset.GREEN) 2.5 else 3.0
            assertTrue("$preset on light chrome: $ratio", ratio >= expectedMin)
        }
    }
}
