package com.omnidroid.app.utils.settings

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.alorma.compose.settings.storage.base.SettingValueState

@Composable
fun rememberSafePreferenceStringSettingState(
    key: String,
    defaultValue: String,
    preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(LocalContext.current),
): SafeStringPreferenceSettingValueState {
    return remember {
        SafeStringPreferenceSettingValueState(
            preferences = preferences,
            key = key,
            defaultValue = defaultValue,
        )
    }
}

class SafeStringPreferenceSettingValueState(
    private val preferences: SharedPreferences,
    val key: String,
    val defaultValue: String = "",
) : SettingValueState<String> {
    private var _value by mutableStateOf(preferences.getString(key, defaultValue) ?: defaultValue)

    override var value: String
        set(value) {
            _value = value
            preferences.edit { putString(key, value) }
        }
        get() = _value

    override fun reset() {
        value = defaultValue
    }
}
