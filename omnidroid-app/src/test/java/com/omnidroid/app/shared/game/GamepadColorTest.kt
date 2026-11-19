package com.omnidroid.app.shared.game

import androidx.compose.ui.graphics.Color
import com.omnidroid.app.mobile.shared.compose.ui.AccentPreset
import com.omnidroid.touchinput.radial.OmnidroidPadTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadColorTest {
    @Test
    fun `keys include default, accent, and black in order`() {
        assertEquals(listOf(GamepadColor.DEFAULT, GamepadColor.ACCENT, GamepadColor.BLACK), GamepadColor.keys)
    }

    @Test
    fun `default and unknown values keep the gray pad`() {
        assertNull(GamepadColor.tint(null, null))
        assertNull(GamepadColor.tint(GamepadColor.DEFAULT, "blue"))
        assertNull(GamepadColor.tint(GamepadColor.BLACK, "blue"))
        assertNull(GamepadColor.tint("orange", "blue"))
    }

    @Test
    fun `accent follows the app accent, green when unset`() {
        assertEquals(AccentPreset.BLUE.dark, GamepadColor.tint(GamepadColor.ACCENT, "blue"))
        assertEquals(AccentPreset.GREEN.dark, GamepadColor.tint(GamepadColor.ACCENT, null))
    }

    @Test
    fun `padTheme builds the correct pad theme for each option`() {
        val accentColor = AccentPreset.BLUE.dark
        val onAccentColor = Color.White

        val grayTheme = GamepadColor.padTheme(GamepadColor.DEFAULT, accentColor, onAccentColor)
        assertEquals(OmnidroidPadTheme.gray(), grayTheme)
        assertNull(grayTheme.tint)
        assertFalse(grayTheme.isBlack)

        val accentTheme = GamepadColor.padTheme(GamepadColor.ACCENT, accentColor, onAccentColor)
        assertEquals(OmnidroidPadTheme.accent(accentColor, onAccentColor), accentTheme)
        assertEquals(accentColor, accentTheme.tint)
        assertFalse(accentTheme.isBlack)

        val blackTheme = GamepadColor.padTheme(GamepadColor.BLACK, accentColor, onAccentColor)
        assertEquals(OmnidroidPadTheme.black(), blackTheme)
        assertTrue(blackTheme.isBlack)
    }
}
