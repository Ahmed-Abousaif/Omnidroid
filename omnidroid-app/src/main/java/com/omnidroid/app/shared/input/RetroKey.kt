package com.omnidroid.app.shared.input

import android.content.Context
import com.omnidroid.R
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class RetroKey(val keyCode: Int) {
    fun displayName(context: Context): String {
        return context.resources.getString(
            R.string.settings_retropad_button_name,
            InputKey(keyCode).displayName(),
        )
    }
}
