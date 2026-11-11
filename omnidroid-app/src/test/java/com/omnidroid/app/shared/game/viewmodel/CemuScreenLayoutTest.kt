package com.omnidroid.app.shared.game.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CemuScreenLayoutTest {
    @Test
    fun screenButtonAlternatesTvAndDualWithoutStoppingAtPad() {
        assertEquals("side_by_side", CemuScreenLayout.nextView("tv"))
        assertEquals("tv", CemuScreenLayout.nextView("side_by_side"))
        assertEquals("side_by_side", CemuScreenLayout.nextView("pad"))
        assertEquals("tv", CemuScreenLayout.nextView("Side by Side"))
        assertEquals("side_by_side", CemuScreenLayout.nextView(null))
    }

    @Test
    fun landscapeHasHalfWidthAndHalfHeightGamePadUnderTv() {
        assertEquals(1280f / 1080f, CemuScreenLayout.aspectRatio("side_by_side", true), 0.00001f)
        val region = CemuScreenLayout.touchRegion("side_by_side", true)!!
        assertEquals(0.25f, region.left, 0f)
        assertEquals(0.75f, region.right, 0f)
        assertEquals(720f / 1080f, region.top, 0.00001f)
        assertEquals(1f, region.bottom, 0f)
        assertEquals("stacked", CemuScreenLayout.nativeLayout(true))
    }

    @Test
    fun portraitRetainsItsExistingDualComposition() {
        assertEquals(32f / 9f, CemuScreenLayout.aspectRatio("side_by_side", false), 0f)
        assertEquals(TouchScreenRegion(0.5f, 0f, 1f, 1f), CemuScreenLayout.touchRegion("side_by_side", false))
        assertEquals("side_by_side", CemuScreenLayout.nativeLayout(false))
    }

    @Test
    fun tvViewHasNoGamepadTouchAreaAndPadOnlyUsesWholeImage() {
        assertNull(CemuScreenLayout.touchRegion("tv", true))
        assertNull(CemuScreenLayout.touchRegion(null, false))
        assertEquals(TouchScreenRegion.FULL, CemuScreenLayout.touchRegion("pad", true))
        assertEquals(16f / 9f, CemuScreenLayout.aspectRatio("tv", true), 0f)
    }
}
