package com.omnidroid.app.mobile.feature.settings.general

import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.omnidroid.R
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsGroup
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsList
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsMenuLink
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsSwitch
import com.omnidroid.app.utils.android.settings.SettingsHorizontalDivider
import com.omnidroid.app.utils.android.settings.booleanPreferenceState
import com.omnidroid.app.utils.android.settings.indexPreferenceState

@Composable
internal fun AdvancedSettings(
    cacheState: SettingsViewModel.CacheState?,
    viewModel: SettingsViewModel,
) {
    val factoryResetDialogState = remember { mutableStateOf(false) }

    if (cacheState != null) {
        OmnidroidSettingsGroup(
            titleRes = R.string.settings_section_performance,
        ) {
            OmnidroidSettingsSwitch(
                state = booleanPreferenceState(R.string.pref_key_low_latency_audio, true),
                titleRes = R.string.settings_title_low_latency_audio,
                subtitleRes = R.string.settings_description_low_latency_audio,
            )
            OmnidroidSettingsList(
                titleRes = R.string.settings_title_maximum_cache_usage,
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
                titleRes = R.string.settings_title_direct_game_load,
                subtitleRes = R.string.settings_description_direct_game_load,
            )
        }
        SettingsHorizontalDivider()
    }

    OmnidroidSettingsGroup {
        OmnidroidSettingsMenuLink(
            titleRes = R.string.settings_title_reset_settings,
            subtitleRes = R.string.settings_description_reset_settings,
            onClick = { factoryResetDialogState.value = true },
        )
    }

    if (factoryResetDialogState.value) {
        FactoryResetDialog(factoryResetDialogState, viewModel)
    }
}

@Composable
private fun FactoryResetDialog(
    factoryResetDialogState: MutableState<Boolean>,
    viewModel: SettingsViewModel,
) {
    val onDismiss = { factoryResetDialogState.value = false }
    AlertDialog(
        title = { Text(stringResource(id = R.string.reset_settings_warning_message_title)) },
        text = { Text(stringResource(id = R.string.reset_settings_warning_message_description)) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.resetAllSettings()
                    factoryResetDialogState.value = false
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
