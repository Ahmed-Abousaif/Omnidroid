package com.omnidroid.touchinput.radial

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Colours of the on-screen gamepad.
 * - Gray (default): see-through grey pads with black/white icons.
 * - Accent: light fills take the app's accent colour with proper high-contrast icons.
 * - Black: high-contrast dark pads with distinct button outlines.
 */
data class OmnidroidPadTheme(
    val tint: Color? = null,
    val iconTint: Color? = null,
    val isBlack: Boolean = false,
    val foregroundPadding: Dp = 8.dp,
    val padding: Dp = 4.dp,
    val level0CornerRadius: Dp = 0.dp,
    val level0Fill: Color = if (isBlack) Color.Transparent else gray(1.0f, 0.05f),
    val level0Border: Color = Color.Transparent,
    val level0BorderWidth: Dp = 0.dp,
    val level0Shadow: Color = if (isBlack) Color.Transparent else DefaultShadowColor.copy(0.10f),
    val level0ShadowWidth: Dp = 2.dp,
    val level1Fill: Color = if (isBlack) Color.Transparent else (tint?.copy(alpha = 0.10f) ?: gray(1.0f, 0.10f)),
    val level1Border: Color = if (isBlack) Color(1.0f, 1.0f, 1.0f, 0.15f) else Color.Transparent,
    val level1BorderWidth: Dp = if (isBlack) 1.dp else 0.dp,
    val level1Shadow: Color = if (isBlack) Color.Transparent else DefaultShadowColor.copy(0.10f),
    val level1ShadowWidth: Dp = 4.dp,
    val level2Fill: Color = if (isBlack) Color.Transparent else (tint?.copy(alpha = 0.125f) ?: gray(1.0f, 0.125f)),
    val level2FillPressed: Color =
        if (isBlack) {
            Color(1.0f, 1.0f, 1.0f, 0.25f)
        } else {
            tint?.copy(alpha = 0.25f) ?: gray(0.0f, 0.125f)
        },
    val level2Border: Color = if (isBlack) Color(1.0f, 1.0f, 1.0f, 0.22f) else Color.Transparent,
    val level2BorderWidth: Dp = if (isBlack) 1.dp else 0.dp,
    val level2Shadow: Color = if (isBlack) Color.Transparent else DefaultShadowColor.copy(0.05f),
    val level2ShadowWidth: Dp = 4.dp,
    val level3Fill: Color =
        if (isBlack) Color(0.0f, 0.0f, 0.0f, 0.50f) else (tint?.copy(alpha = 0.50f) ?: gray(1.0f, 0.50f)),
    val level3FillPressed: Color =
        if (isBlack) {
            Color(1.0f, 1.0f, 1.0f, 0.30f)
        } else {
            tint?.copy(alpha = 0.75f) ?: gray(0.0f, 0.50f)
        },
    val level3Border: Color = if (isBlack) Color(1.0f, 1.0f, 1.0f, 0.45f) else Color.Transparent,
    val level3BorderPressed: Color = if (isBlack) Color(1.0f, 1.0f, 1.0f, 0.85f) else Color.Transparent,
    val level3BorderWidth: Dp = if (isBlack) 1.5.dp else 0.dp,
    val level3Shadow: Color = if (isBlack) Color.Transparent else DefaultShadowColor.copy(0.05f),
    val level3ShadowWidth: Dp = 4.dp,
    val iconColor: Color = calculateIconColor(isBlack, tint, iconTint),
    val iconColorPressed: Color =
        if (isBlack) {
            Color.White
        } else if (tint != null && iconTint != null) {
            iconTint
        } else {
            gray(1.0f, 0.50f)
        },
) {
    fun compositeFill(pressed: Boolean): Color = if (pressed) level2FillPressed else level2Fill

    fun compositeBorder(pressed: Boolean): Color = level2Border

    fun foregroundFill(pressed: Boolean): Color = if (pressed) level3FillPressed else level3Fill

    fun foregroundBorder(pressed: Boolean): Color = if (pressed) level3BorderPressed else level3Border

    fun icons(pressed: Boolean): Color = if (pressed) iconColorPressed else iconColor

    companion object {
        private fun gray(
            luminosity: Float,
            opacity: Float,
        ): Color = Color(luminosity, luminosity, luminosity, opacity)

        private fun calculateIconColor(
            isBlack: Boolean,
            tint: Color?,
            iconTint: Color?,
        ): Color {
            if (isBlack) return Color(1.0f, 1.0f, 1.0f, 0.70f)
            if (iconTint != null) return iconTint.copy(alpha = 0.65f)
            if (tint == null) return gray(0.0f, 0.50f)
            // WCAG relative luminance break-even point with black vs white is ~0.179f
            val isDark = tint.luminance() > 0.18f
            return gray(if (isDark) 0.0f else 1.0f, 0.65f)
        }

        fun gray(): OmnidroidPadTheme = OmnidroidPadTheme()

        fun accent(
            tint: Color,
            iconTint: Color? = null,
        ): OmnidroidPadTheme = OmnidroidPadTheme(tint = tint, iconTint = iconTint)

        fun black(): OmnidroidPadTheme = OmnidroidPadTheme(isBlack = true)
    }
}

val LocalOmnidroidPadTheme =
    compositionLocalOf<OmnidroidPadTheme> {
        error("OmnidroidPadTheme is missing")
    }
