package com.omnidroid.app.mobile.feature.settings.general

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsBackStackTest {
    @Test
    fun backReturnsToImmediateParentAtAnyDepth() {
        val root = SettingsBackStack<String>()
        val path = root.push("section").push("subsection").push("deep")
        assertEquals("deep", path.current)
        assertEquals("subsection", path.parent)
        assertEquals("subsection", path.pop().current)
        assertEquals("section", path.pop().pop().current)
        assertNull(path.pop().pop().pop().current)
        assertNull(root.current)
    }

    @Test
    fun backAtRootIsSafeAndDoesNotMutatePreviousPaths() {
        val root = SettingsBackStack<String>()
        val child = root.push("child")
        assertNull(root.pop().current)
        assertEquals("child", child.current)
        assertNull(child.parent)
    }
}
