package com.omnidroid.touchinput.radial.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sign

/** Retains raw held directions so a policy change can take effect without another touch. */
class AnalogDpadState {
    private val directions = mutableMapOf<Int, Offset>()
    private var allowDiagonals = true

    fun update(
        id: Int,
        direction: Offset,
    ): Offset {
        if (direction.isSpecified && direction.x.isFinite() && direction.y.isFinite()) {
            directions[id] = direction
        } else {
            directions.remove(id)
        }
        return quantizeAnalogDpad(direction, allowDiagonals)
    }

    fun setAllowDiagonals(allow: Boolean): Map<Int, Offset> {
        if (allowDiagonals == allow) return emptyMap()
        allowDiagonals = allow
        return directions.mapValues { (_, direction) -> quantizeAnalogDpad(direction, allow) }
    }
}

/** Input and output both use PadKit's positive-up coordinate convention. */
internal fun quantizeAnalogDpad(
    direction: Offset,
    allowDiagonals: Boolean,
): Offset {
    if (!direction.isSpecified || !direction.x.isFinite() || !direction.y.isFinite()) return Offset.Zero
    val (x, y) = direction
    if (hypot(x, y) < 0.25f) return Offset.Zero
    if (!allowDiagonals) {
        return if (abs(x) >= abs(y)) Offset(sign(x), 0f) else Offset(0f, sign(y))
    }
    val angle = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()
    return when {
        angle in -22.5f..22.5f -> Offset(1f, 0f)
        angle in 22.5f..67.5f -> Offset(1f, 1f)
        angle in 67.5f..112.5f -> Offset(0f, 1f)
        angle in 112.5f..157.5f -> Offset(-1f, 1f)
        angle in -67.5f..-22.5f -> Offset(1f, -1f)
        angle in -112.5f..-67.5f -> Offset(0f, -1f)
        angle in -157.5f..-112.5f -> Offset(-1f, -1f)
        else -> Offset(-1f, 0f)
    }
}
