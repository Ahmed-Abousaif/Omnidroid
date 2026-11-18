package com.omnidroid.touchinput.radial

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Colours of the on-screen gamepad. With no [tint] the pad is the default see-through grey. With a
 * tint, the light fills take that colour at the same opacities, so the pad keeps its look and
 * press feedback, and icons switch to black or white, whichever reads better on the tint.
 */
class OmnidroidPadTheme(
    private val tint: Color? = null,
) {
    private fun gray(
        luminosity: Float,
        opacity: Float,
    ): Color {
        return Color(luminosity, luminosity, luminosity, opacity)
    }

    /** The light fill colour at [opacity]: white by default, else the tint. */
    private fun light(opacity: Float): Color = tint?.copy(alpha = opacity) ?: gray(1.0f, opacity)

    /** Icons on a tinted fill: black on light tints, white on dark ones. */
    private val iconBase: Float = if (tint == null || tint.luminance() > 0.4f) 0.0f else 1.0f

    val foregroundPadding: Dp = 8.dp
    val padding: Dp = 4.dp

    private val icons = gray(iconBase, 0.50f)
    private val iconsPressed = gray(1.0f, 0.50f)

    private val level3Fill = light(0.50f)
    private val level3FillPressed = gray(0.0f, 0.50f)
    val level3Shadow = DefaultShadowColor.copy(0.05f)
    val level3ShadowWidth = 4.dp

    private val level2Fill = light(0.125f)
    private val level2FillPressed = gray(0.0f, 0.125f)
    val level2Shadow = DefaultShadowColor.copy(0.05f)
    val level2ShadowWidth = 4.dp

    val level1Fill = light(0.10f)
    val level1Shadow = DefaultShadowColor.copy(0.10f)
    val level1ShadowWidth = 4.dp

    val level0CornerRadius = 0.dp
    val level0Fill = light(0.05f)
    val level0Shadow = DefaultShadowColor.copy(0.10f)
    val level0ShadowWidth = 2.dp

    fun compositeFill(pressed: Boolean): Color {
        return if (pressed) {
            level2FillPressed
        } else {
            level2Fill
        }
    }

    fun foregroundFill(pressed: Boolean): Color {
        return if (pressed) {
            level3FillPressed
        } else {
            level3Fill
        }
    }

    fun icons(pressed: Boolean): Color {
        return if (pressed) {
            iconsPressed
        } else {
            icons
        }
    }
}

val LocalOmnidroidPadTheme =
    compositionLocalOf<OmnidroidPadTheme> {
        error("OmnidroidPadTheme is missing")
    }
