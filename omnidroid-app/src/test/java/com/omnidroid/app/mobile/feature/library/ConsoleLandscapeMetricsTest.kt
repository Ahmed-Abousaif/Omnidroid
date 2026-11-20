package com.omnidroid.app.mobile.feature.library

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleLandscapeMetricsTest {
    @Test
    fun `standard landscape height uses 60 percent for small cards and full info`() {
        val metrics = LandscapeMetrics(maxWidth = 840.dp, maxHeight = 400.dp)
        assertEquals(20.dp, metrics.edge)
        assertEquals(360.dp, metrics.bigHeight)
        // 360 * 0.60 = 216dp; 360 - 12 - 96 = 252dp -> min is 216dp
        assertEquals(216f, metrics.smallHeight.value, 0.01f)
        // infoHeight = 360 - 216 - 12 = 132dp (> 100dp, so not compact)
        assertEquals(132f, metrics.infoHeight.value, 0.01f)
        assertFalse(metrics.compactInfo)
    }

    @Test
    fun `short landscape carousel shrinks small cards to keep info height and enables compact info`() {
        val metrics = LandscapeMetrics(maxWidth = 800.dp, maxHeight = 268.dp)
        assertEquals(16.dp, metrics.edge)
        assertEquals(236.dp, metrics.bigHeight)
        // 236 * 0.60 = 141.6dp; 236 - 12 - 96 = 128dp -> min is 128dp
        assertEquals(128.dp, metrics.smallHeight)
        // infoHeight = 236 - 128 - 12 = 96dp
        assertEquals(LandscapeInfoMinHeight, metrics.infoHeight)
        assertTrue("compactInfo must be true when infoHeight is at min height", metrics.compactInfo)
    }
}
