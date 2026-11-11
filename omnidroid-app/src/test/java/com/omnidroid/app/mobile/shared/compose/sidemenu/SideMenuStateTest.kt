package com.omnidroid.app.mobile.shared.compose.sidemenu

import org.junit.Assert.*
import org.junit.Test

class SideMenuStateTest {
    @Test fun closeBeforeOpeningCompletesExactlyOnce() {
        val gate = SideMenuCloseGate()
        val events = mutableListOf<String>()
        assertTrue(gate.request { events += "action" })
        gate.complete { events += "closed" }
        gate.complete { events += "duplicate" }
        assertEquals(listOf("closed", "action"), events)
    }

    @Test fun repeatedDismissCannotReplaceAnAcceptedCommand() {
        val gate = SideMenuCloseGate()
        var result = "none"
        assertTrue(gate.request { result = "save" })
        assertFalse(gate.request { result = "quit" })
        gate.complete {}
        assertEquals("save", result)
        assertFalse(gate.request { result = "late" })
    }

    @Test fun completionBeforeCloseDoesNothing() {
        val gate = SideMenuCloseGate()
        gate.complete { fail("No close requested") }
        assertTrue(gate.request {})
    }

    @Test fun widthIsAdaptiveAndNeverExceedsTheViewport() {
        assertEquals(200f, sideMenuWidth(200f), 0f)
        assertEquals(320f, sideMenuWidth(400f), 0f)
        assertEquals(420f, sideMenuWidth(1000f), 0f)
        assertEquals(440f, sideMenuWidth(2000f), 0f)
        assertEquals(0f, sideMenuWidth(0f), 0f)
    }
}
