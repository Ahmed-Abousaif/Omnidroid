package com.omnidroid.app.mobile.feature.settings.general

import androidx.compose.material3.MaterialTheme
import com.omnidroid.app.shared.settings.HDModeQuality
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.AccentPreset
import com.omnidroid.app.mobile.shared.compose.ui.LocalAppDarkTheme
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsColorChoice
import com.omnidroid.app.utils.android.settings.stringPreferenceState
import com.omnidroid.app.mobile.feature.main.MainRoute
import com.omnidroid.app.mobile.feature.main.navigateToRoute
import com.omnidroid.app.mobile.feature.library.LibraryViewModel
import com.omnidroid.app.shared.settings.rememberHdModeAllowed
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsGroup
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsList
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsSegmented
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsMenuLink
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsSlider
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsSwitch
import com.omnidroid.app.utils.android.settings.booleanPreferenceState
import com.omnidroid.app.utils.android.settings.indexPreferenceState
import com.omnidroid.app.utils.android.settings.intPreferenceState
import com.omnidroid.app.utils.android.stringListResource

@Composable
internal fun DisplaySettings(navController: NavController) {
    val hdMode = booleanPreferenceState(R.string.pref_key_hd_mode, false)
    val immersiveMode = booleanPreferenceState(R.string.pref_key_enable_immersive_mode, false)
    val hdModeAllowed = rememberHdModeAllowed()
    val uiScaleStep =
        intPreferenceState(
            key = stringResource(id = R.string.pref_key_button_ui_scale_step),
            default = com.omnidroid.app.mobile.feature.settings.uiscale.UiScaleHelper.DEFAULT_STEP,
        )
    val scalePercentage =
        com.omnidroid.app.mobile.feature.settings.uiscale.UiScaleHelper.percentageForStep(uiScaleStep.value)

    LaunchedEffect(hdModeAllowed) {
        if (!hdModeAllowed && hdMode.value) {
            hdMode.value = false
        }
    }

    OmnidroidSettingsGroup {
        OmnidroidSettingsSegmented(
            state =
                indexPreferenceState(
                    R.string.pref_key_app_theme,
                    "dark",
                    stringListResource(R.array.pref_key_app_theme_values).toList(),
                ),
            titleRes = R.string.settings_title_app_theme,
            items = stringListResource(R.array.pref_key_app_theme_display_names),
        )
        val accent = stringPreferenceState(R.string.pref_key_accent_color, AccentPreset.GREEN.key)
        val dark = LocalAppDarkTheme.current
        val presets = AccentPreset.entries
        OmnidroidSettingsColorChoice(
            selectedIndex = presets.indexOf(AccentPreset.fromKey(accent.value)),
            colors = presets.map { it.color(dark) },
            labels = presets.map { stringResource(it.labelRes()) },
            onSelect = { accent.value = presets[it].key },
            titleRes = R.string.settings_title_accent_color,
            subtitleRes = R.string.settings_description_accent_color,
        )
        OmnidroidSettingsMenuLink(
            titleRes = R.string.settings_title_button_ui_scale,
            subtitle = {
                Text(
                    text =
                        stringResource(
                            id = R.string.settings_description_button_ui_scale,
                            scalePercentage,
                        ),
                )
            },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_UI_SCALE) },
        )
        OmnidroidSettingsSwitch(
            state = immersiveMode,
            titleRes = R.string.settings_title_immersive_mode,
            subtitleRes = R.string.settings_description_immersive_mode,
        )
        OmnidroidSettingsSwitch(
            enabled = hdModeAllowed,
            state = hdMode,
            titleRes = R.string.settings_title_hd_mode,
            subtitle = {
                Text(
                    text =
                        stringResource(
                            if (hdModeAllowed) {
                                R.string.settings_description_hd_mode
                            } else {
                                R.string.settings_description_hd_mode_battery
                            },
                        ),
                )
            },
            onCheckedChange = { enabled ->
                if (!hdModeAllowed && enabled) {
                    hdMode.value = false
                }
            },
        )
        OmnidroidSettingsSlider(
            enabled = hdMode.value && hdModeAllowed,
            state =
                intPreferenceState(
                    key = stringResource(id = R.string.pref_key_hd_mode_quality),
                    default = 2,
                ),
            steps = 1,
            valueRange = 0f..2f,
            titleRes = R.string.settings_title_hd_quality,
            subtitleRes = R.string.settings_description_hd_quality,
            valueText = { value ->
                Text(
                    text =
                        stringResource(
                            when (HDModeQuality.parse(value)) {
                                HDModeQuality.LOW -> R.string.settings_hd_quality_low
                                HDModeQuality.MEDIUM -> R.string.settings_hd_quality_medium
                                HDModeQuality.HIGH -> R.string.settings_hd_quality_high
                            },
                        ),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
        )
        OmnidroidSettingsList(
            enabled = !hdMode.value,
            state =
                indexPreferenceState(
                    R.string.pref_key_shader_filter,
                    "auto",
                    stringListResource(R.array.pref_key_shader_filter_values).toList(),
                ),
            titleRes = R.string.display_filter,
            items = stringListResource(R.array.pref_key_shader_filter_display_names),
        )
        OmnidroidSettingsSwitch(
            state = booleanPreferenceState(LibraryViewModel.SEPARATE_LAYOUTS_KEY, false),
            titleRes = R.string.settings_title_layout_per_orientation,
            subtitleRes = R.string.settings_description_layout_per_orientation,
        )
    }
}

private fun AccentPreset.labelRes(): Int =
    when (this) {
        AccentPreset.GREEN -> R.string.accent_color_green
        AccentPreset.BLUE -> R.string.accent_color_blue
        AccentPreset.PURPLE -> R.string.accent_color_purple
        AccentPreset.PINK -> R.string.accent_color_pink
        AccentPreset.RED -> R.string.accent_color_red
        AccentPreset.ORANGE -> R.string.accent_color_orange
        AccentPreset.YELLOW -> R.string.accent_color_yellow
        AccentPreset.TEAL -> R.string.accent_color_teal
        AccentPreset.WHITE -> R.string.accent_color_white
    }
