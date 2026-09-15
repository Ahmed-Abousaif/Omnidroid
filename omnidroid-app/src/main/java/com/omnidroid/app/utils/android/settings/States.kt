package com.omnidroid.app.utils.android.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.omnidroid.app.utils.settings.rememberSafePreferenceBooleanSettingState
import com.omnidroid.app.utils.settings.rememberSafePreferenceIndexSettingState
import com.omnidroid.app.utils.settings.rememberSafePreferenceIntSettingState
import com.omnidroid.app.utils.settings.rememberSafePreferenceStringsSetSettingState
import com.omnidroid.lib.preferences.SharedPreferencesHelper

@Composable
fun booleanPreferenceState(
    id: Int,
    default: Boolean,
) = booleanPreferenceState(stringResource(id = id), default)

@Composable
fun booleanPreferenceState(
    key: String,
    default: Boolean,
) = rememberSafePreferenceBooleanSettingState(
    key = key,
    defaultValue = default,
    preferences = SharedPreferencesHelper.getSharedPreferences(LocalContext.current),
)

@Composable
fun indexPreferenceState(
    id: Int,
    default: String,
    values: List<String>,
) = indexPreferenceState(stringResource(id), default, values)

@Composable
fun indexPreferenceState(
    key: String,
    default: String,
    values: List<String>,
) = rememberSafePreferenceIndexSettingState(
    key = key,
    values = values,
    defaultValue = default,
    preferences = SharedPreferencesHelper.getSharedPreferences(LocalContext.current),
)

@Composable
fun stringsSetPreferenceState(
    key: String,
    default: Set<String>,
) = rememberSafePreferenceStringsSetSettingState(
    key = key,
    defaultValue = default,
    preferences = SharedPreferencesHelper.getSharedPreferences(LocalContext.current),
)

@Composable
fun intPreferenceState(
    key: String,
    default: Int,
) = rememberSafePreferenceIntSettingState(
    key = key,
    defaultValue = default,
    preferences = SharedPreferencesHelper.getSharedPreferences(LocalContext.current),
)
