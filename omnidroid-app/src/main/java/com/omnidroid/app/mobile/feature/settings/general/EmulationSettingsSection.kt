package com.omnidroid.app.mobile.feature.settings.general

import androidx.compose.runtime.Composable
import com.omnidroid.R
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsGroup
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsMenuLink

@Composable
internal fun EmulationSettings(
    indexingInProgress: Boolean,
    onNavigateSubscreen: (SettingsSubscreen) -> Unit,
) {
    OmnidroidSettingsGroup {
        OmnidroidSettingsMenuLink(
            titleRes = R.string.settings_title_open_cores_selection,
            subtitleRes = R.string.settings_description_open_cores_selection,
            onClick = { onNavigateSubscreen(SettingsSubscreen.CORES_SELECTION) },
        )
        OmnidroidSettingsMenuLink(
            titleRes = R.string.settings_title_graphics_api_selection,
            subtitleRes = R.string.settings_description_graphics_api_selection,
            onClick = { onNavigateSubscreen(SettingsSubscreen.GRAPHICS_API) },
        )
        OmnidroidSettingsMenuLink(
            titleRes = R.string.settings_title_display_bios_info,
            subtitleRes = R.string.settings_description_display_bios_info,
            enabled = !indexingInProgress,
            onClick = { onNavigateSubscreen(SettingsSubscreen.BIOS) },
        )
        OmnidroidSettingsMenuLink(
            titleRes = R.string.settings_title_device_profile,
            subtitleRes = R.string.settings_description_device_profile,
            onClick = { onNavigateSubscreen(SettingsSubscreen.DEVICE_PROFILE) },
        )
    }
}
