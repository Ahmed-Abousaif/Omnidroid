package com.omnidroid.app.mobile.feature.game

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameScreenLayoutTest {
    private val viewport = Rect(450f, 0f, 1890f, 1080f)

    @Test
    fun clipsViewportToGapBetweenPads() {
        val left = Rect(0f, 500f, 700f, 1080f)
        val right = Rect(1640f, 500f, 2340f, 1080f)
        assertEquals(Rect(700f, 0f, 1640f, 1080f), GameScreenLayout.clipBetweenPads(viewport, left, right))
    }

    @Test
    fun padsOutsideViewportLeaveItUnchanged() {
        val left = Rect(0f, 500f, 300f, 1080f)
        val right = Rect(2000f, 500f, 2340f, 1080f)
        assertEquals(viewport, GameScreenLayout.clipBetweenPads(viewport, left, right))
    }

    @Test
    fun unknownPadBoundsLeaveViewportUnchanged() {
        assertEquals(viewport, GameScreenLayout.clipBetweenPads(viewport, null, null))
    }

    @Test
    fun overlappingPadsLeaveNoStrip() {
        val left = Rect(0f, 500f, 1300f, 1080f)
        val right = Rect(1200f, 500f, 2340f, 1080f)
        assertNull(GameScreenLayout.clipBetweenPads(viewport, left, right))
    }
}
