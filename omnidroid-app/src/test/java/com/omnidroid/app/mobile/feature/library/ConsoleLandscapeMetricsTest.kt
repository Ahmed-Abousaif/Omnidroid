package com.omnidroid.app.mobile.feature.library

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleLandscapeMetricsTest {
    @Test
    fun `standard landscape height uses 55 percent for small cards and full info`() {
        val metrics = LandscapeMetrics(maxWidth = 840.dp, maxHeight = 400.dp)
        assertEquals(20.dp, metrics.edge)
        assertEquals(360.dp, metrics.bigHeight)
        // 360 * 0.55 = 198dp; 360 - 12 - 112 = 236dp -> min is 198dp
        assertEquals(198.dp, metrics.smallHeight)
        // infoHeight = 360 - 198 - 12 = 150dp (> 116dp, so not compact)
        assertEquals(150.dp, metrics.infoHeight)
        assertFalse(metrics.compactInfo)
    }

    @Test
    fun `short landscape carousel shrinks small cards to keep info height and enables compact info`() {
        val metrics = LandscapeMetrics(maxWidth = 800.dp, maxHeight = 268.dp)
        assertEquals(16.dp, metrics.edge)
        assertEquals(236.dp, metrics.bigHeight)
        // 236 * 0.55 = 129.8dp; 236 - 12 - 112 = 112dp -> min is 112dp
        assertEquals(112.dp, metrics.smallHeight)
        // infoHeight = 236 - 112 - 12 = 112dp
        assertEquals(LandscapeInfoMinHeight, metrics.infoHeight)
        assertTrue("compactInfo must be true when infoHeight is at min height", metrics.compactInfo)
    }
}
