package com.omnidroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/**
 * How far into the reported cutout safe area content sits. That area spans the whole status-bar
 * depth, well past the camera hole itself, so content only needs [fraction] of it.
 */
enum class CutoutClearance(val fraction: Float) {
    /** Library, settings and other app chrome. */
    Chrome(0.5f),

    /** On-screen pads and the game view during play. */
    InGame(0.7f),
}

/**
 * Display-cutout (camera hole) insets for the given [sides], reduced to the [clearance]
 * fraction of the reported depth.
 *
 * Omnidroid draws edge to edge, so anything pinned to a screen edge - on-screen pads, side
 * rails, full-bleed bars - can end up underneath the hole. The cutout follows the rotation, so
 * the sides a caller needs depend on the current orientation.
 *
 * Devices without a cutout report zero insets, so this is a no-op there.
 */
@Composable
fun rememberHuggingCutoutInsets(
    sides: WindowInsetsSides,
    clearance: CutoutClearance = CutoutClearance.Chrome,
): WindowInsets {
    val cutout = WindowInsets.displayCutout
    return remember(cutout, sides, clearance) { ScaledWindowInsets(cutout.only(sides), clearance.fraction) }
}

/** Keeps this content clear of the display cutout on [sides]. See [rememberHuggingCutoutInsets]. */
@Composable
fun Modifier.hugDisplayCutout(
    sides: WindowInsetsSides,
    clearance: CutoutClearance = CutoutClearance.Chrome,
): Modifier = windowInsetsPadding(rememberHuggingCutoutInsets(sides, clearance))

@Stable
private class ScaledWindowInsets(
    private val insets: WindowInsets,
    private val fraction: Float,
) : WindowInsets {
    override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int =
        (insets.getLeft(density, layoutDirection) * fraction).roundToInt()

    override fun getTop(density: Density): Int = (insets.getTop(density) * fraction).roundToInt()

    override fun getRight(density: Density, layoutDirection: LayoutDirection): Int =
        (insets.getRight(density, layoutDirection) * fraction).roundToInt()

    override fun getBottom(density: Density): Int = (insets.getBottom(density) * fraction).roundToInt()

    override fun equals(other: Any?): Boolean =
        other is ScaledWindowInsets && other.insets == insets && other.fraction == fraction

    override fun hashCode(): Int = 31 * insets.hashCode() + fraction.hashCode()
}
