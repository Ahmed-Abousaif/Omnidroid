package com.omnidroid.app.mobile.feature.settings.uiscale

import kotlin.math.roundToInt

/**
 * Button UI scale, in 5% steps around 100%. The saved preference is the step, with 0 meaning 100%,
 * so values saved before smaller sizes existed keep their meaning.
 */
object UiScaleHelper {
    const val SCALE_STEP = 0.05f

    /** Smallest step: 80%, for small fingers or small screens. */
    const val MIN_STEP = -4

    /** Largest step: 140%. */
    const val MAX_STEP = 8

    const val DEFAULT_STEP = 0

    fun clampStep(step: Int): Int = step.coerceIn(MIN_STEP, MAX_STEP)

    fun scaleForStep(step: Int): Float {
        return 1f + clampStep(step) * SCALE_STEP
    }

    fun percentageForStep(step: Int): Int {
        return (scaleForStep(step) * 100).roundToInt()
    }
}
