package com.omnidroid.app.mobile.shared.controller

import org.junit.Assert.assertEquals
import org.junit.Test

class ControllerHintFitTest {
    private fun fit(
        left: List<Int>,
        right: List<Int>,
        max: Int,
    ) = fitControllerHints(left, right, gap = 10, clusterGap = 20, maxWidth = max)

    @Test
    fun `everything fits`() {
        // 100 + 10 + 50 = 160 left, 20 between, 40 + 10 + 40 = 90 right -> 270
        assertEquals(2 to 2, fit(listOf(100, 50), listOf(40, 40), max = 270))
    }

    @Test
    fun `drops the end of the left cluster first`() {
        assertEquals(1 to 2, fit(listOf(100, 50), listOf(40, 40), max = 269))
    }

    @Test
    fun `keeps the first left hint while trimming the right cluster`() {
        // 100 + 20 + 40 = 160
        assertEquals(1 to 1, fit(listOf(100, 50), listOf(40, 40), max = 160))
    }

    @Test
    fun `drops the left cluster entirely before the last right hint`() {
        assertEquals(0 to 1, fit(listOf(100, 50), listOf(40, 40), max = 159))
        assertEquals(0 to 0, fit(listOf(100, 50), listOf(40, 40), max = 39))
    }

    @Test
    fun `no gap between clusters when one side is empty`() {
        assertEquals(0 to 2, fit(emptyList(), listOf(40, 40), max = 90))
        assertEquals(2 to 0, fit(listOf(100, 50), emptyList(), max = 160))
    }
}
