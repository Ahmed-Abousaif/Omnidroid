package com.omnidroid.app.mobile.shared.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StickPressFilterTest {
    private fun presses(
        filter: StickPressFilter,
        samples: List<Triple<Float, Float, Long>>,
    ): List<StickPressFilter.Press> = samples.mapNotNull { (x, y, t) -> filter.update(x, y, t) }

    @Test
    fun `a flick right and back is one press`() {
        val filter = StickPressFilter()
        val result =
            presses(
                filter,
                listOf(
                    Triple(0.3f, 0f, 0L),
                    Triple(0.8f, 0f, 16L),
                    Triple(1f, 0f, 32L),
                    Triple(0.4f, 0f, 48L),
                    Triple(0.1f, 0f, 64L),
                    // Spring back overshoots to the left.
                    Triple(-0.7f, 0f, 80L),
                    Triple(-0.2f, 0f, 96L),
                    Triple(0f, 0f, 112L),
                ),
            )
        assertEquals(listOf(StickPressFilter.Press(1, 0)), result)
    }

    @Test
    fun `jitter around the threshold does not press again`() {
        val filter = StickPressFilter()
        val result =
            presses(
                filter,
                listOf(0.67f, 0.6f, 0.66f, 0.55f, 0.68f).mapIndexed { i, x -> Triple(x, 0f, i * 16L) },
            )
        assertEquals(1, result.size)
    }

    @Test
    fun `two separate flicks the same way are two presses`() {
        val filter = StickPressFilter()
        val result =
            presses(
                filter,
                listOf(
                    Triple(0.9f, 0f, 0L),
                    Triple(0f, 0f, 100L),
                    Triple(0.9f, 0f, 250L),
                    Triple(0f, 0f, 350L),
                ),
            )
        assertEquals(2, result.size)
    }

    @Test
    fun `left after the rebound window is a real press`() {
        val filter = StickPressFilter()
        val result =
            presses(
                filter,
                listOf(
                    Triple(0.9f, 0f, 0L),
                    Triple(0f, 0f, 100L),
                    Triple(-0.9f, 0f, 600L),
                ),
            )
        assertEquals(listOf(StickPressFilter.Press(1, 0), StickPressFilter.Press(-1, 0)), result)
    }

    @Test
    fun `a quick deliberate flick back counts`() {
        val filter = StickPressFilter()
        val result =
            presses(
                filter,
                listOf(
                    Triple(1f, 0f, 0L),
                    Triple(0.1f, 0f, 40L),
                    // Pushed hard the other way right after: a real press, not a rebound.
                    Triple(-1f, 0f, 70L),
                    Triple(0f, 0f, 110L),
                ),
            )
        assertEquals(listOf(StickPressFilter.Press(1, 0), StickPressFilter.Press(-1, 0)), result)
    }

    @Test
    fun `a shallow reverse right after letting go is the spring`() {
        val filter = StickPressFilter()
        assertEquals(StickPressFilter.Press(1, 0), filter.update(1f, 0f, 0L))
        assertNull(filter.update(0.1f, 0f, 30L))
        assertNull(filter.update(-0.7f, 0f, 50L))
        assertNull(filter.update(-0.8f, 0f, 120L))
        assertNull(filter.update(-0.1f, 0f, 140L))
    }

    @Test
    fun `a deep reverse right after letting go counts`() {
        val filter = StickPressFilter()
        filter.update(0f, 1f, 0L)
        filter.update(0f, 0.1f, 30L)
        assertEquals(StickPressFilter.Press(0, -1), filter.update(0f, -0.9f, 50L))
    }

    @Test
    fun `a reverse still out after the rebound window counts on the next report`() {
        val filter = StickPressFilter()
        filter.update(0f, 1f, 0L)
        filter.update(0f, 0.1f, 30L)
        assertNull(filter.update(0f, -0.7f, 50L))
        assertEquals(StickPressFilter.Press(0, -1), filter.update(0f, -0.72f, 190L))
    }

    @Test
    fun `a diagonal presses only the stronger direction`() {
        val filter = StickPressFilter()
        assertEquals(StickPressFilter.Press(0, 1), filter.update(0.65f, 0.8f, 0L))
        assertNull(filter.update(0.7f, 0.8f, 16L))
    }

    @Test
    fun `holding never presses again`() {
        val filter = StickPressFilter()
        val result = presses(filter, (0..100).map { Triple(1f, 0f, it * 20L) })
        assertEquals(listOf(StickPressFilter.Press(1, 0)), result)
    }
}
