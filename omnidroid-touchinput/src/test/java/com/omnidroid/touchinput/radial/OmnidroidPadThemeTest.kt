package com.omnidroid.touchinput.radial

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OmnidroidPadThemeTest {
    @Test
    fun `default gray theme matches standard unstyled pad`() {
        val theme = OmnidroidPadTheme.gray()
        assertNull(theme.tint)
        assertFalse(theme.isBlack)

        assertEquals(Color(1f, 1f, 1f, 0.05f), theme.level0Fill)
        assertEquals(Color.Transparent, theme.level0Border)
        assertEquals(0.dp, theme.level0BorderWidth)

        assertEquals(Color(1f, 1f, 1f, 0.10f), theme.level1Fill)
        assertEquals(Color.Transparent, theme.level1Border)
        assertEquals(0.dp, theme.level1BorderWidth)

        assertEquals(Color(1f, 1f, 1f, 0.50f), theme.foregroundFill(pressed = false))
        assertEquals(Color(0f, 0f, 0f, 0.50f), theme.foregroundFill(pressed = true))
        assertEquals(Color.Transparent, theme.foregroundBorder(pressed = false))

        assertEquals(Color(0f, 0f, 0f, 0.50f), theme.icons(pressed = false))
        assertEquals(Color(1f, 1f, 1f, 0.50f), theme.icons(pressed = true))
    }

    @Test
    fun `accent theme tints buttons and maintains accent on press`() {
        val accentColor = Color(0xFF3D8BFF)
        val onAccent = Color.White
        val theme = OmnidroidPadTheme.accent(tint = accentColor, iconTint = onAccent)

        assertEquals(accentColor, theme.tint)
        assertFalse(theme.isBlack)

        // Portrait glass panel remains neutral
        assertEquals(Color(1f, 1f, 1f, 0.05f), theme.level0Fill)

        assertEquals(accentColor.copy(alpha = 0.10f), theme.level1Fill)
        assertEquals(accentColor.copy(alpha = 0.50f), theme.foregroundFill(pressed = false))
        // Pressed state keeps accent color
        assertEquals(accentColor.copy(alpha = 0.75f), theme.foregroundFill(pressed = true))

        assertEquals(onAccent.copy(alpha = 0.65f), theme.icons(pressed = false))
    }

    @Test
    fun `accent theme without explicit iconTint uses WCAG contrast for brand green`() {
        val brandGreen = Color(0xFF00B33C)
        val theme = OmnidroidPadTheme.accent(tint = brandGreen)

        // Brand green has luminance ~0.326 > 0.18, so dark icons must be chosen
        assertEquals(Color(0f, 0f, 0f, 0.65f), theme.icons(pressed = false))
    }

    @Test
    fun `black theme has high contrast outlines and dark fills matching spec`() {
        val theme = OmnidroidPadTheme.black()
        assertTrue(theme.isBlack)

        assertEquals(Color.Transparent, theme.level0Fill)

        // Dial circle outline
        assertEquals(Color.Transparent, theme.level1Fill)
        assertEquals(Color(1f, 1f, 1f, 0.15f), theme.level1Border)
        assertEquals(1.dp, theme.level1BorderWidth)

        // Composite secondary / diagonal outline
        assertEquals(Color.Transparent, theme.compositeFill(pressed = false))
        assertEquals(Color(1f, 1f, 1f, 0.22f), theme.compositeBorder(pressed = false))
        assertEquals(1.dp, theme.level2BorderWidth)

        // Button foreground outline and fill
        assertEquals(Color(0f, 0f, 0f, 0.50f), theme.foregroundFill(pressed = false))
        assertEquals(Color(1f, 1f, 1f, 0.45f), theme.foregroundBorder(pressed = false))
        assertEquals(1.5.dp, theme.level3BorderWidth)

        // Feedback on press
        assertEquals(Color(1f, 1f, 1f, 0.30f), theme.foregroundFill(pressed = true))
        assertEquals(Color(1f, 1f, 1f, 0.85f), theme.foregroundBorder(pressed = true))

        // Icons
        assertEquals(Color(1f, 1f, 1f, 0.70f), theme.icons(pressed = false))
        assertEquals(Color.White, theme.icons(pressed = true))
    }
}
