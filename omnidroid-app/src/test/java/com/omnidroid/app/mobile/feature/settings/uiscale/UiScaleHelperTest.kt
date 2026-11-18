package com.omnidroid.app.mobile.feature.settings.uiscale

import org.junit.Assert.assertEquals
import org.junit.Test

class UiScaleHelperTest {
    @Test
    fun `step 0 is 100 percent, so saved values keep their meaning`() {
        assertEquals(1f, UiScaleHelper.scaleForStep(0), 0.0001f)
        assertEquals(100, UiScaleHelper.percentageForStep(0))
        assertEquals(125, UiScaleHelper.percentageForStep(5))
    }

    @Test
    fun `negative steps make buttons smaller`() {
        assertEquals(80, UiScaleHelper.percentageForStep(UiScaleHelper.MIN_STEP))
        assertEquals(95, UiScaleHelper.percentageForStep(-1))
    }

    @Test
    fun `steps outside the range are clamped`() {
        assertEquals(80, UiScaleHelper.percentageForStep(-50))
        assertEquals(140, UiScaleHelper.percentageForStep(50))
        assertEquals(UiScaleHelper.MAX_STEP, UiScaleHelper.clampStep(99))
    }
}
