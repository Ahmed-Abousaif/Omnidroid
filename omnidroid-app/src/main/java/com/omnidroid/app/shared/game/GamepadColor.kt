package com.omnidroid.app.shared.game

import androidx.compose.ui.graphics.Color
import com.omnidroid.app.mobile.shared.compose.ui.AccentPreset

/**
 * The on-screen gamepad colour setting (Settings > Controls > On-screen). The saved value is
 * [DEFAULT] (the see-through grey pad), [ACCENT] (follow the app's accent colour) or the key of an
 * [AccentPreset]. Pure so it can be unit tested.
 */
object GamepadColor {
    const val DEFAULT = "default"
    const val ACCENT = "accent"

    /** The options in settings order. */
    val keys: List<String> = listOf(DEFAULT, ACCENT) + AccentPreset.entries.map { it.key }

    /** The pad tint for a saved value, or null for the default grey pad. The game is always dark. */
    fun tint(
        key: String?,
        accentKey: String?,
    ): Color? =
        when (key) {
            null, DEFAULT -> null
            ACCENT -> AccentPreset.fromKey(accentKey).dark
            else -> AccentPreset.entries.firstOrNull { it.key == key }?.dark
        }
}
