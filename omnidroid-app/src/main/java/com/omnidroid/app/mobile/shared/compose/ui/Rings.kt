package com.omnidroid.app.mobile.shared.compose.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate

/** Where a ring sits relative to the element's bounds. */
enum class RingPlacement {
    /** Inside the bounds: never cut off by a parent that clips (lists, docks, cards). */
    Inside,

    /** Outside the bounds: leave room for it around the element. */
    Outside,
}

/**
 * Draws a ring following [shape], [gap] away from the bounds (inside or outside), and optionally a
 * thin [innerLine] just inside it that keeps the ring readable on a fill of its own colour.
 */
internal fun DrawScope.drawShapeRing(
    shape: Shape,
    color: Color,
    width: Float,
    gap: Float,
    placement: RingPlacement,
    innerLine: Color? = null,
) {
    val offset = (gap + width / 2f).let { if (placement == RingPlacement.Inside) it else -it }
    val ringSize = Size(size.width - offset * 2f, size.height - offset * 2f)
    if (ringSize.minDimension <= 0f) return
    translate(offset, offset) {
        drawOutline(shape.createOutline(ringSize, layoutDirection, this), color, style = Stroke(width = width))
    }
    if (innerLine == null) return
    val line = width / 2f
    val innerOffset = offset + width / 2f + line / 2f
    val innerSize = Size(size.width - innerOffset * 2f, size.height - innerOffset * 2f)
    if (innerSize.minDimension <= 0f) return
    translate(innerOffset, innerOffset) {
        drawOutline(shape.createOutline(innerSize, layoutDirection, this), innerLine, style = Stroke(width = line))
    }
}
