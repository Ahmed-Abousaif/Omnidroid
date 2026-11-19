package com.omnidroid.app.shared.game

import androidx.compose.ui.graphics.Color
import com.omnidroid.app.mobile.shared.compose.ui.AccentPreset
import com.omnidroid.touchinput.radial.OmnidroidPadTheme

/**
 * The on-screen gamepad colour setting (Settings > Controls > On-screen):
 * - [DEFAULT]: the see-through gray pad.
 * - [ACCENT]: the app's accent colour (Display > Accent color).
 * - [BLACK]: high-contrast outlined black pad.
 *
 * Pure so it can be unit tested.
 */
object GamepadColor {
    const val DEFAULT = "default"
    const val ACCENT = "accent"
    const val BLACK = "black"

    /** The options in settings order. */
    val keys: List<String> = listOf(DEFAULT, ACCENT, BLACK)

    /** Builds the appropriate pad theme for the saved [key]. */
    fun padTheme(
        key: String?,
        accent: Color,
        onAccent: Color? = null,
    ): OmnidroidPadTheme =
        when (key) {
            ACCENT -> OmnidroidPadTheme.accent(accent, onAccent)
            BLACK -> OmnidroidPadTheme.black()
            else -> OmnidroidPadTheme.gray()
        }

    /** The pad tint for a saved value, or null for the gray or black pad. The game is always dark. */
    fun tint(
        key: String?,
        accentKey: String?,
    ): Color? = if (key == ACCENT) AccentPreset.fromKey(accentKey).dark else null
}
