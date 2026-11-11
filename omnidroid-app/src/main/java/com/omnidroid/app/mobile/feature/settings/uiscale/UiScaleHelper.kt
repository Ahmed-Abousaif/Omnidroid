package com.omnidroid.app.mobile.feature.settings.uiscale

import kotlin.math.roundToInt

object UiScaleHelper {
    const val MIN_SCALE = 1.0f
    const val MAX_SCALE = 1.25f
    const val TOTAL_INCREMENTS = 5
    const val SCALE_STEP = 0.05f

    fun scaleForStep(step: Int): Float {
        return MIN_SCALE + step.coerceIn(0, TOTAL_INCREMENTS) * SCALE_STEP
    }

    fun percentageForStep(step: Int): Int {
        return (scaleForStep(step) * 100).roundToInt()
    }
}
