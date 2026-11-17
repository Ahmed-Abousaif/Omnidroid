package com.omnidroid.app.shared.covers

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomCoverSizingTest {
    @Test
    fun smallImagesAreNotUpscaled() {
        assertEquals(600 to 900, CustomCoverManager.fitWithin(600, 900, 1024))
    }

    @Test
    fun portraitBoxArtKeepsItsShape() {
        assertEquals(683 to 1024, CustomCoverManager.fitWithin(1200, 1800, 1024))
    }

    @Test
    fun landscapeBoxArtCapsTheWidth() {
        assertEquals(1024 to 768, CustomCoverManager.fitWithin(2048, 1536, 1024))
    }

    @Test
    fun extremeAspectNeverCollapsesToZero() {
        assertEquals(1024 to 1, CustomCoverManager.fitWithin(20000, 10, 1024))
    }
}
