package com.omnidroid.app.mobile.shared.compose.sidemenu

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuOptionTest {
    private val options = listOf(MenuOption("disabled", "Off"), MenuOption("Enabled", "On"))

    @Test
    fun exactMatchWins() {
        assertEquals(1, options.indexOfMenuValue("Enabled"))
    }

    @Test
    fun stringsMatchIgnoringCase() {
        assertEquals(1, options.indexOfMenuValue("enabled"))
        assertEquals(0, options.indexOfMenuValue("DISABLED"))
    }

    @Test
    fun unknownValueIsMinusOne() {
        assertEquals(-1, options.indexOfMenuValue("auto"))
    }

    @Test
    fun nonStringValuesMatchExactlyOnly() {
        val ints = listOf(MenuOption(1, "One"), MenuOption(2, "Two"))
        assertEquals(1, ints.indexOfMenuValue(2))
        assertEquals(-1, ints.indexOfMenuValue(3))
    }
}
