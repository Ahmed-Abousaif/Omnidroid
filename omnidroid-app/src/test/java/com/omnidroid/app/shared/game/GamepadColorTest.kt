package com.omnidroid.app.shared.game

import com.omnidroid.app.mobile.shared.compose.ui.AccentPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GamepadColorTest {
    @Test
    fun `default and unknown values keep the gray pad`() {
        assertNull(GamepadColor.tint(null, null))
        assertNull(GamepadColor.tint(GamepadColor.DEFAULT, "blue"))
        assertNull(GamepadColor.tint("orange", "blue"))
    }

    @Test
    fun `accent follows the app accent, green when unset`() {
        assertEquals(AccentPreset.BLUE.dark, GamepadColor.tint(GamepadColor.ACCENT, "blue"))
        assertEquals(AccentPreset.GREEN.dark, GamepadColor.tint(GamepadColor.ACCENT, null))
    }
}
