package com.omnidroid.app.mobile.feature.settings.advanced

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.main.MainRoute
import com.omnidroid.app.shared.covers.RawgCoverStore
import com.omnidroid.app.shared.library.LibraryIndexScheduler
import com.omnidroid.app.utils.android.settings.OmnidroidCardSettingsGroup
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsList
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsMenuLink
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsPage
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsSlider
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsSwitch
import com.omnidroid.app.utils.android.settings.booleanPreferenceState
import com.omnidroid.app.utils.android.settings.indexPreferenceState
import com.omnidroid.app.utils.android.settings.intPreferenceState
import com.omnidroid.app.utils.android.settings.stringPreferenceState
import kotlinx.coroutines.delay

@Composable
fun AdvancedSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: AdvancedSettingsViewModel,
    navController: NavHostController,
) {
    val uiState =
        viewModel.uiState
            .collectAsState()
            .value

    OmnidroidSettingsPage(
        modifier = modifier.fillMaxSize(),
    ) {
        if (uiState?.cache == null) {
            return@OmnidroidSettingsPage
        }

        InputSettings()
        GeneralSettings(uiState.cache, viewModel, navController)
    }
}

@Composable
private fun InputSettings() {
    OmnidroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_input)) },
    ) {
        val rumbleEnabled = booleanPreferenceState(R.string.pref_key_enable_rumble, false)
        OmnidroidSettingsSwitch(
            state = rumbleEnabled,
            title = { Text(text = stringResource(id = R.string.settings_title_enable_rumble)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_enable_rumble)) },
        )
        OmnidroidSettingsSwitch(
            enabled = rumbleEnabled.value,
            state = booleanPreferenceState(R.string.pref_key_enable_device_rumble, false),
            title = { Text(text = stringResource(id = R.string.settings_title_enable_device_rumble)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_enable_device_rumble)) },
        )
        OmnidroidSettingsSlider(
            state =
                intPreferenceState(
                    key = stringResource(id = R.string.pref_key_vibration_intensity),
                    default = 50,
                ),
            steps = 100,
            valueRange = 0f..100f,
            enabled = true,
            title = { Text(text = stringResource(R.string.settings_title_vibration_intensity)) },
            subtitle = { Text(text = stringResource(R.string.settings_description_vibration_intensity)) },
            valueText = { value ->
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
        )
        OmnidroidSettingsSlider(
            state =
                intPreferenceState(
                    key = stringResource(id = R.string.pref_key_tilt_sensitivity_index),
                    default = 6,
                ),
            steps = 10,
            valueRange = 0f..10f,
            enabled = true,
            title = { Text(text = stringResource(R.string.settings_title_tilt_sensitivity)) },
        )
    }
}

@Composable
private fun GeneralSettings(
    cacheState: AdvancedSettingsViewModel.CacheState,
    viewModel: AdvancedSettingsViewModel,
    navController: NavController,
) {
    val factoryResetDialogState = remember { mutableStateOf(false) }
    val context = LocalContext.current
    val rawgEnabled = booleanPreferenceState(R.string.pref_key_enable_rawg_metadata, false)
    val rawgApiKey = stringPreferenceState(R.string.pref_key_rawg_api_key, "")
    var previousRawgEnabled by remember { mutableStateOf(rawgEnabled.value) }
    var previousRawgApiKey by remember { mutableStateOf(rawgApiKey.value.trim()) }

    LaunchedEffect(rawgEnabled.value) {
        if (rawgEnabled.value == previousRawgEnabled) return@LaunchedEffect
        previousRawgEnabled = rawgEnabled.value
        if (rawgEnabled.value) {
            if (rawgApiKey.value.trim().isNotEmpty()) {
                LibraryIndexScheduler.scheduleLibrarySync(context.applicationContext)
            }
        } else {
            RawgCoverStore.clear()
        }
    }

    LaunchedEffect(rawgApiKey.value, rawgEnabled.value) {
        val trimmed = rawgApiKey.value.trim()
        if (!rawgEnabled.value) {
            previousRawgApiKey = trimmed
            return@LaunchedEffect
        }
        if (trimmed == previousRawgApiKey) return@LaunchedEffect
        val becameAvailable = previousRawgApiKey.isEmpty() && trimmed.isNotEmpty()
        previousRawgApiKey = trimmed
        if (!becameAvailable) return@LaunchedEffect
        // Debounce while the user is still typing/pasting the key.
        delay(600)
        if (rawgEnabled.value && rawgApiKey.value.trim() == trimmed) {
            LibraryIndexScheduler.scheduleLibrarySync(context.applicationContext)
        }
    }

    OmnidroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_general)) },
    ) {
        OmnidroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_low_latency_audio, false),
            title = { Text(text = stringResource(id = R.string.settings_title_low_latency_audio)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_low_latency_audio)) },
        )
        OmnidroidSettingsSwitch(
            state = rawgEnabled,
            title = { Text(text = stringResource(id = R.string.settings_title_enable_rawg_metadata)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_enable_rawg_metadata)) },
        )
        if (rawgEnabled.value) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 40.dp, end = 16.dp, top = 0.dp, bottom = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_title_rawg_api_key),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_description_rawg_api_key),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )
                OutlinedTextField(
                    value = rawgApiKey.value,
                    onValueChange = { rawgApiKey.value = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(text = stringResource(R.string.settings_hint_rawg_api_key)) },
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        }
        OmnidroidSettingsList(
            title = { Text(text = stringResource(R.string.settings_title_maximum_cache_usage)) },
            items = cacheState.displayNames,
            state =
                indexPreferenceState(
                    R.string.pref_key_max_cache_size,
                    cacheState.default,
                    cacheState.values,
                ),
        )
        OmnidroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_allow_direct_game_load, true),
            title = { Text(text = stringResource(id = R.string.settings_title_direct_game_load)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_direct_game_load)) },
        )
        OmnidroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_reset_settings)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_reset_settings)) },
            onClick = { factoryResetDialogState.value = true },
        )
    }

    if (factoryResetDialogState.value) {
        FactoryResetDialog(factoryResetDialogState, viewModel, navController)
    }
}

@Composable
private fun FactoryResetDialog(
    factoryResetDialogState: MutableState<Boolean>,
    viewModel: AdvancedSettingsViewModel,
    navController: NavController,
) {
    val onDismiss = {
        factoryResetDialogState.value = false
    }
    AlertDialog(
        title = { Text(stringResource(id = R.string.reset_settings_warning_message_title)) },
        text = { Text(stringResource(id = R.string.reset_settings_warning_message_description)) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.resetAllSettings()
                    navController.popBackStack(MainRoute.SETTINGS.route, false)
                },
            ) {
                Text(text = stringResource(id = R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        },
    )
}
