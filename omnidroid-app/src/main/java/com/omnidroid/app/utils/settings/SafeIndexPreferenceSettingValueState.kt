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
fun rememberSafePreferenceIndexSettingState(
    key: String,
    values: List<String>,
    defaultValue: String,
    preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(LocalContext.current),
): SafeIndexPreferenceSettingValueState {
    val state =
        remember {
            SafeIndexPreferenceSettingValueState(preferences, key, values, defaultValue)
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

class SafeIndexPreferenceSettingValueState(
    private val preferences: SharedPreferences,
    val key: String,
    private val values: List<String>,
    private val defaultValue: String,
) : SettingValueState<Int> {
    private var _value by mutableStateOf(preferences.safeGetString(key, defaultValue))

    override var value: Int
        set(index) {
            _value = values[index]
            preferences.edit { putString(key, _value) }
        }
        get() = values.indexOf(_value)

    fun reloadFromPreferences() {
        _value = preferences.safeGetString(key, defaultValue)
    }

    override fun reset() {
        value = values.indexOf(defaultValue)
    }
}
