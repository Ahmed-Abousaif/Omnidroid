package com.omnidroid.app.utils.settings

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
    val state =
        remember(key, preferences) {
            SafeStringPreferenceSettingValueState(
                preferences = preferences,
                key = key,
                defaultValue = defaultValue,
            )
        }
    DisposableEffect(state, preferences, key) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == key) state.reloadFromPreferences()
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
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

    fun reloadFromPreferences() {
        _value = preferences.getString(key, defaultValue) ?: defaultValue
    }

    override fun reset() {
        value = defaultValue
    }
}
