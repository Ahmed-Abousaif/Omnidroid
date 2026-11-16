package com.omnidroid.app.mobile.shared.compose.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverLetterboxTest {
    private val card = 2f / 3f

    @Test
    fun boxArtCloseToTheCardShapeStillCrops() {
        assertFalse(shouldLetterboxCover(2f / 3f, card))
        assertFalse(shouldLetterboxCover(0.72f, card)) // ~8% wider than 2:3
    }

    @Test
    fun squareAndLandscapeCoversAreShownWhole() {
        assertTrue(shouldLetterboxCover(1f, card)) // PS1 jewel case, Switch icon
        assertTrue(shouldLetterboxCover(4f / 3f, card)) // SNES / Genesis landscape box
    }

    @Test
    fun unknownSizesDoNotLetterbox() {
        assertFalse(shouldLetterboxCover(0f, card))
        assertFalse(shouldLetterboxCover(1f, 0f))
    }
}
