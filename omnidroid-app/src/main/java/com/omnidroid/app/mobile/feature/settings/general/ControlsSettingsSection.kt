package com.omnidroid.app.mobile.feature.settings.general

import com.omnidroid.app.utils.android.settings.stringPreferenceState
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsColorChoice
import com.omnidroid.app.shared.game.GamepadColor
import com.omnidroid.app.mobile.shared.compose.ui.AccentPreset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.storage.base.SettingValueState
import com.omnidroid.R
import com.omnidroid.app.tv.shared.TVHelper
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsGroup
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsSegmented
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsMenuLink
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsSlider
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsSwitch
import com.omnidroid.app.utils.android.settings.SettingsHorizontalDivider
import com.omnidroid.app.utils.android.settings.booleanPreferenceState
import com.omnidroid.app.utils.android.settings.indexPreferenceState
import com.omnidroid.app.utils.android.settings.intPreferenceState
import com.omnidroid.app.utils.android.stringListResource

@Composable
internal fun ControllerSettings(
    onNavigateSubscreen: (SettingsSubscreen) -> Unit,
) {
    val isTv = TVHelper.isTV(LocalContext.current)
    // Shared so the intensity slider reacts as soon as touch vibration is switched on or off.
    val touchFeedback =
        indexPreferenceState(
            R.string.pref_key_haptic_feedback_mode,
            "press",
            stringListResource(R.array.pref_key_haptic_feedback_mode_values),
        )
    // Index 0 is "none"; any other mode vibrates on touch at the configured intensity.
    val touchFeedbackOn = touchFeedback.value != 0
    if (isTv) {
        ControlsRumbleGroup(touchFeedbackOn)
        SettingsHorizontalDivider()
        ControlsGamepadsGroup(onNavigateSubscreen = onNavigateSubscreen)
        SettingsHorizontalDivider()
        ControlsOnScreenGroup(touchFeedback, showTvHint = true)
    } else {
        ControlsOnScreenGroup(touchFeedback, showTvHint = false)
        SettingsHorizontalDivider()
        ControlsRumbleGroup(touchFeedbackOn)
        SettingsHorizontalDivider()
        ControlsGamepadsGroup(onNavigateSubscreen = onNavigateSubscreen)
    }
}

@Composable
private fun ControlsOnScreenGroup(
    touchFeedback: SettingValueState<Int>,
    showTvHint: Boolean,
) {
    OmnidroidSettingsGroup(
        titleRes = R.string.settings_section_on_screen,
    ) {
        if (showTvHint) {
            Text(
                text = stringResource(id = R.string.settings_description_on_screen_tv),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        OmnidroidSettingsSegmented(
            state = touchFeedback,
            titleRes = R.string.settings_title_enable_touch_feedback,
            items = stringListResource(R.array.pref_key_haptic_feedback_mode_display_names),
        )
        OmnidroidSettingsSlider(
            state =
                intPreferenceState(
                    key = stringResource(id = R.string.pref_key_virtual_pad_opacity),
                    default = 100,
                ),
            steps = 99,
            valueRange = 0f..100f,
            enabled = true,
            titleRes = R.string.settings_title_virtual_pad_opacity,
            subtitleRes = R.string.settings_description_virtual_pad_opacity,
            valueText = { value ->
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
        )
        GamepadColorChoice()
        OmnidroidSettingsSlider(
            state =
                intPreferenceState(
                    key = stringResource(id = R.string.pref_key_tilt_sensitivity_index),
                    default = 6,
                ),
            steps = 9,
            valueRange = 0f..10f,
            enabled = true,
            titleRes = R.string.settings_title_tilt_sensitivity,
            valueText = { value ->
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
        )
    }
}

@Composable
private fun ControlsRumbleGroup(touchFeedbackOn: Boolean) {
    OmnidroidSettingsGroup(
        titleRes = R.string.settings_section_rumble,
    ) {
        val rumbleEnabled = booleanPreferenceState(R.string.pref_key_enable_rumble, false)
        OmnidroidSettingsSwitch(
            state = rumbleEnabled,
            titleRes = R.string.settings_title_enable_rumble,
            subtitleRes = R.string.settings_description_enable_rumble,
        )
        OmnidroidSettingsSwitch(
            enabled = rumbleEnabled.value,
            state = booleanPreferenceState(R.string.pref_key_enable_device_rumble, false),
            titleRes = R.string.settings_title_enable_device_rumble,
            subtitleRes = R.string.settings_description_enable_device_rumble,
        )
        OmnidroidSettingsSlider(
            state =
                intPreferenceState(
                    key = stringResource(id = R.string.pref_key_vibration_intensity),
                    default = 50,
                ),
            steps = 99,
            valueRange = 0f..100f,
            // Sets the strength of both rumble and on-screen touch vibration.
            enabled = rumbleEnabled.value || touchFeedbackOn,
            titleRes = R.string.settings_title_vibration_intensity,
            subtitleRes = R.string.settings_description_vibration_intensity,
            valueText = { value ->
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
        )
    }
}

@Composable
private fun ControlsGamepadsGroup(
    onNavigateSubscreen: (SettingsSubscreen) -> Unit,
) {
    OmnidroidSettingsGroup(
        titleRes = R.string.settings_section_gamepads,
    ) {
        OmnidroidSettingsMenuLink(
            titleRes = R.string.settings_title_gamepad_settings,
            subtitleRes = R.string.settings_description_gamepad_settings,
            onClick = { onNavigateSubscreen(SettingsSubscreen.INPUT_DEVICES) },
        )
    }
}

/** Gamepad colour swatches: default grey, the accent colour, then the accent presets. */
@Composable
private fun GamepadColorChoice() {
    val padColor = stringPreferenceState(R.string.pref_key_gamepad_color, GamepadColor.DEFAULT)
    val accentKey = stringPreferenceState(R.string.pref_key_accent_color, AccentPreset.GREEN.key).value
    val keys = GamepadColor.keys
    val presetLabels = AccentPreset.entries.associate { it.key to stringResource(it.labelRes()) }
    val defaultLabel = stringResource(R.string.gamepad_color_default)
    val accentLabel = stringResource(R.string.gamepad_color_accent)
    OmnidroidSettingsColorChoice(
        selectedIndex = keys.indexOf(padColor.value).coerceAtLeast(0),
        colors = keys.map { GamepadColor.tint(it, accentKey) ?: DefaultPadSwatch },
        labels =
            keys.map {
                when (it) {
                    GamepadColor.DEFAULT -> defaultLabel
                    GamepadColor.ACCENT -> accentLabel
                    else -> presetLabels[it].orEmpty()
                }
            },
        onSelect = { padColor.value = keys[it] },
        titleRes = R.string.settings_title_gamepad_color,
        subtitleRes = R.string.settings_description_gamepad_color,
    )
}

/** The default pad's light grey, as a swatch. */
private val DefaultPadSwatch = Color(0xFFBDBDBD)
