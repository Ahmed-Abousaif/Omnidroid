package com.omnidroid.app.mobile.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryLayoutTest {
    @Test
    fun `one layout for both orientations unless separate layouts are on`() {
        val shown = LibraryLayout.forOrientation(true, false, LibraryLayout.GRID, LibraryLayout.LIST)
        assertEquals(LibraryLayout.GRID, shown)
    }

    @Test
    fun `with separate layouts portrait shows its own`() {
        val inPortrait = LibraryLayout.forOrientation(true, true, LibraryLayout.GRID, LibraryLayout.LIST)
        val inLandscape = LibraryLayout.forOrientation(false, true, LibraryLayout.GRID, LibraryLayout.LIST)
        assertEquals(LibraryLayout.LIST, inPortrait)
        assertEquals(LibraryLayout.GRID, inLandscape)
    }

    @Test
    fun `unknown or missing names fall back to Console`() {
        assertEquals(LibraryLayout.CONSOLE, LibraryLayout.fromName(null))
        assertEquals(LibraryLayout.CONSOLE, LibraryLayout.fromName("HOME"))
        assertEquals(LibraryLayout.LIST, LibraryLayout.fromName("LIST"))
    }
}
