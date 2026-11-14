package com.omnidroid.app.shared.displays

import android.content.Context
import com.omnidroid.lib.preferences.SharedPreferencesHelper

/** Available in either process without binding the main-process coordinator. */
internal class DisplayPreferences(private val context: Context) {
    private val shared by lazy { SharedPreferencesHelper.getSharedPreferences(context) }

    var automatic: Boolean
        get() =
            if (shared.contains(AUTOMATIC)) {
                shared.getBoolean(AUTOMATIC, true)
            } else {
                // Preserve settings written by the original main-process-only display coordinator.
                context.getSharedPreferences("secondary_displays", Context.MODE_PRIVATE).getBoolean("automatic", true)
            }
        set(value) {
            shared.edit().putBoolean(AUTOMATIC, value).apply()
        }

    private companion object {
        const val AUTOMATIC = "secondary_displays_automatic"
    }
}
