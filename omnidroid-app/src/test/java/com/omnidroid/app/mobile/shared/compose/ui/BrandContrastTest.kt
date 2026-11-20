package com.omnidroid.app.mobile.shared.compose.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandContrastTest {
    /** WCAG 2.x contrast ratio between two opaque colors. */
    private fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    @Test
    fun textOnGreenButtonsMeetsNormalTextContrast() {
        assertTrue(contrast(OnLibraryNeonGreen, LibraryNeonGreen) >= 4.5f)
    }

    @Test
    fun whiteOnGreenWasBelowTheBar() {
        // Documents why the button text changed: white on the brand green is ~2.8:1.
        assertTrue(contrast(Color.White, LibraryNeonGreen) < 4.5f)
    }

    @Test
    fun lightModeAccentIsVisibleOnLightChrome() {
        val lightAccent = BrandGreen
        val lightPanel = Color(0xFFF5F5F5)
        assertTrue(contrast(lightAccent, lightPanel) >= 2.5f)
    }
}
