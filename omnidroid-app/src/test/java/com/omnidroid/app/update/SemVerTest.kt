package com.omnidroid.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {
    @Test
    fun stripsLeadingVAndComparesNumerically() {
        assertTrue(SemVer.isNewerThan("v2.6.1", "2.6.0"))
        assertFalse(SemVer.isNewerThan("v2.6.0", "2.6.1"))
        assertEquals(0, SemVer.compare("v1.2.3", "1.2.3"))
    }

    @Test
    fun stripsDebugSuffixFromVersionName() {
        assertTrue(SemVer.isNewerThan("2.7.0", "2.6.0-DEBUG"))
        assertEquals(0, SemVer.compare("2.6.0-DEBUG", "v2.6.0"))
    }

    @Test
    fun comparesUnevenComponentLengths() {
        assertTrue(SemVer.isNewerThan("1.2.1", "1.2"))
        assertTrue(SemVer.isNewerThan("1.10.0", "1.9.9"))
        assertFalse(SemVer.isNewerThan("1.9.9", "1.10.0"))
    }

    @Test
    fun normalizeBasics() {
        assertEquals("2.6.0", SemVer.normalize("v2.6.0"))
        assertEquals("2.6.0", SemVer.normalize("V2.6.0-DEBUG"))
        assertEquals("1.0.0", SemVer.normalize("1.0.0+build.1"))
    }
}
