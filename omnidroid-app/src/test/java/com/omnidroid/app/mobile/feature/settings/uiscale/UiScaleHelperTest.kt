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
        assertEquals(UiScaleHelper.MIN_STEP, UiScaleHelper.clampStep(-99))
        assertEquals(UiScaleHelper.MAX_STEP, UiScaleHelper.clampStep(99))
    }

    @Test
    fun `scale matches expected bounds and clamps correctly`() {
        assertEquals(0.80f, UiScaleHelper.scaleForStep(UiScaleHelper.MIN_STEP), 0.0001f)
        assertEquals(1.40f, UiScaleHelper.scaleForStep(UiScaleHelper.MAX_STEP), 0.0001f)
        assertEquals(0.80f, UiScaleHelper.scaleForStep(-50), 0.0001f)
        assertEquals(1.40f, UiScaleHelper.scaleForStep(50), 0.0001f)
        assertEquals(UiScaleHelper.MIN_SCALE, UiScaleHelper.scaleForStep(UiScaleHelper.MIN_STEP), 0.0001f)
        assertEquals(UiScaleHelper.MAX_SCALE, UiScaleHelper.scaleForStep(UiScaleHelper.MAX_STEP), 0.0001f)
        assertEquals(12, UiScaleHelper.TOTAL_STEPS)
        assertEquals(11, UiScaleHelper.SLIDER_STEPS)
    }
}
