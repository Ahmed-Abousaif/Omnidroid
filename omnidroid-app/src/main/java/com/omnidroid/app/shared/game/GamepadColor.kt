package com.omnidroid.app.shared.game

import androidx.compose.ui.graphics.Color
import com.omnidroid.app.mobile.shared.compose.ui.AccentPreset

/**
 * The on-screen gamepad colour setting (Settings > Controls > On-screen): [DEFAULT], the
 * see-through gray pad, or [ACCENT], the app's accent colour (Display > Accent color). Pure so it
 * can be unit tested.
 */
object GamepadColor {
    const val DEFAULT = "default"
    const val ACCENT = "accent"

    /** The options in settings order. */
    val keys: List<String> = listOf(DEFAULT, ACCENT)

    /** The pad tint for a saved value, or null for the gray pad. The game is always dark. */
    fun tint(
        key: String?,
        accentKey: String?,
    ): Color? = if (key == ACCENT) AccentPreset.fromKey(accentKey).dark else null
}
