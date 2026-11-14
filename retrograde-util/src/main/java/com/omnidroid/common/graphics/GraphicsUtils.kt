package com.omnidroid.common.graphics

import android.content.Context
import android.util.DisplayMetrics
import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

object GraphicsUtils {
    fun convertDpToPixel(
        dp: Float,
        context: Context,
    ): Float {
        return dp * (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
    }

    fun rotatePoint(
        offset: Offset,
        angleInDegrees: Double,
    ): Offset {
        val angle = Math.toRadians(angleInDegrees)
        val cosTheta = cos(angle)
        val sinTheta = sin(angle)
        val xNew = offset.x * cosTheta - offset.y * sinTheta
        val yNew = offset.x * sinTheta + offset.y * cosTheta
        return Offset(xNew.toFloat(), yNew.toFloat())
    }
}
