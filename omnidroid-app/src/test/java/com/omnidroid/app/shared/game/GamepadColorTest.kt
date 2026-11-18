package com.omnidroid.app.shared.game

import com.omnidroid.app.mobile.shared.compose.ui.AccentPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GamepadColorTest {
    @Test
    fun `default and unknown values keep the grey pad`() {
        assertNull(GamepadColor.tint(null, null))
        assertNull(GamepadColor.tint(GamepadColor.DEFAULT, "blue"))
        assertNull(GamepadColor.tint("no-such-colour", "blue"))
    }

    @Test
    fun `accent follows the app accent, green when unset`() {
        assertEquals(AccentPreset.BLUE.dark, GamepadColor.tint(GamepadColor.ACCENT, "blue"))
        assertEquals(AccentPreset.GREEN.dark, GamepadColor.tint(GamepadColor.ACCENT, null))
    }

    @Test
    fun `a preset key picks that colour`() {
        assertEquals(AccentPreset.ORANGE.dark, GamepadColor.tint("orange", "blue"))
    }
}
