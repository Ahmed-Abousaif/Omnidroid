package com.omnidroid.app.utils.settings

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import com.alorma.compose.settings.storage.base.SettingValueState

internal class PreferenceSettingState<T>(
    private val preferences: SharedPreferences,
    private val key: String,
    private val defaultValue: T,
    private val read: () -> T,
    private val write: (SharedPreferences.Editor, T) -> Unit,
) : SettingValueState<T> {
    private var current by mutableStateOf(read())

    override var value: T
        get() = current
        set(value) {
            current = value
            preferences.edit().also { write(it, value) }.apply()
        }

    fun reload() { current = read() }

    override fun reset() { value = defaultValue }
}

@Composable
internal fun <T> rememberPreferenceSettingState(
    preferences: SharedPreferences,
    key: String,
    defaultValue: T,
    read: () -> T,
    write: (SharedPreferences.Editor, T) -> Unit,
): SettingValueState<T> {
    val state = remember(preferences, key, defaultValue) {
        PreferenceSettingState(preferences, key, defaultValue, read, write)
    }
    DisposableEffect(state) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == null || changedKey == key) state.reload()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        // Close the gap between the initial read and listener registration.
        state.reload()
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

@Composable
fun rememberSafePreferenceBooleanSettingState(
    key: String,
    defaultValue: Boolean,
    preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(LocalContext.current),
): SettingValueState<Boolean> = rememberPreferenceSettingState(
    preferences, key, defaultValue,
    { preferences.safeGetBoolean(key, defaultValue) },
    { editor, value -> editor.putBoolean(key, value) },
)

@Composable
fun rememberSafePreferenceIntSettingState(
    key: String,
    defaultValue: Int,
    preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(LocalContext.current),
): SettingValueState<Int> = rememberPreferenceSettingState(
    preferences, key, defaultValue,
    { preferences.safeGetInt(key, defaultValue) },
    { editor, value -> editor.putInt(key, value) },
)

@Composable
fun rememberSafePreferenceStringSettingState(
    key: String,
    defaultValue: String,
    preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(LocalContext.current),
): SettingValueState<String> = rememberPreferenceSettingState(
    preferences, key, defaultValue,
    { preferences.safeGetString(key, defaultValue) ?: defaultValue },
    { editor, value -> editor.putString(key, value) },
)

@Composable
fun rememberSafePreferenceStringsSetSettingState(
    key: String,
    defaultValue: Set<String>,
    preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(LocalContext.current),
): SettingValueState<Set<String>> = rememberPreferenceSettingState(
    preferences, key, defaultValue.toSet(),
    { preferences.safeGetStringSet(key, defaultValue)?.toSet() ?: defaultValue.toSet() },
    { editor, value -> editor.putStringSet(key, value.toSet()) },
)

@Composable
fun rememberSafePreferenceIndexSettingState(
    key: String,
    values: List<String>,
    defaultValue: String,
    preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(LocalContext.current),
): SettingValueState<Int> {
    val stored = rememberSafePreferenceStringSettingState(key, defaultValue, preferences)
    return remember(stored, values, defaultValue) {
        object : SettingValueState<Int> {
            override var value: Int
                get() = values.indexOf(stored.value)
                set(index) { values.getOrNull(index)?.let { stored.value = it } }
            override fun reset() { stored.value = defaultValue }
        }
    }
}
