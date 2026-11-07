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
fun rememberSafePreferenceIntSettingState(
    key: String,
    defaultValue: Int,
    preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(LocalContext.current),
): SafeIntPreferenceSettingValueState {
    val state =
        remember {
            SafeIntPreferenceSettingValueState(
                preferences = preferences,
                key = key,
                defaultValue = defaultValue,
            )
        }
    DisposableEffect(key, preferences) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == key) {
                    state.sync()
                }
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

class SafeIntPreferenceSettingValueState(
    private val preferences: SharedPreferences,
    val key: String,
    val defaultValue: Int = 0,
) : SettingValueState<Int> {
    private var _value by mutableStateOf(preferences.safeGetInt(key, defaultValue))

    fun sync() {
        _value = preferences.safeGetInt(key, defaultValue)
    }

    override var value: Int
        set(value) {
            _value = value
            preferences.edit { putInt(key, value) }
        }
        get() = _value

    override fun reset() {
        value = defaultValue
    }
}
