package com.omnidroid.touchinput.radial.utils

import androidx.compose.ui.geometry.Offset
import com.omnidroid.common.math.computeSizeOfItemsAroundCircumference
import com.omnidroid.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.anchors.Anchor
import gg.padkit.anchors.buildGridAnchors
import gg.padkit.ids.Id
import kotlinx.collections.immutable.PersistentList
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

fun buildCentral6ButtonsAnchors(
    rotation: Float,
    id0: Int,
    id1: Int,
    id2: Int,
    id3: Int,
): PersistentList<Anchor<Id.Key>> {
    val buttonSize = computeSizeOfItemsAroundCircumference(12)
    val distance = 3f * buttonSize

    val rotationAngle = rotation * TouchControllerSettingsManager.MAX_ROTATION.toDouble()

    val delta = Offset(-tan(Math.toRadians(15.0)).toFloat(), 1f) * distance * 1.25f
    val topLeftLine = Offset(0f, -1f - distance)
    val topRightLine = Offset(sin(Math.toRadians(30.0)).toFloat(), -cos(Math.toRadians(30.0)).toFloat())

    // Console-specific spacing stays here; PadKit owns anchor generation and rotation.
    return buildGridAnchors(
        rows = listOf(listOf(Id.Key(id0), Id.Key(id1)), listOf(Id.Key(id2), Id.Key(id3))),
        buttonSize = buttonSize,
        origin = topLeftLine + delta,
        columnStep = delta,
        rowStep = topRightLine * (1f + distance) - topLeftLine,
        rotationDegrees = rotationAngle.toFloat(),
    )
}
