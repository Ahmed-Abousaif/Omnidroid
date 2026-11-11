package com.omnidroid.app.mobile.feature.settings.general

import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import com.omnidroid.R

internal enum class SettingsSection(
    @param:StringRes val titleId: Int,
) {
    LIBRARY(R.string.settings_category_library),
    DISPLAY(R.string.settings_category_display),
    CONTROLS(R.string.settings_category_controllers),
    SAVES(R.string.settings_category_saves),
    EMULATION(R.string.settings_category_emulation),
    ADVANCED(R.string.settings_category_advanced),
    ABOUT(R.string.settings_category_about),
}

internal enum class SettingsSubscreen(
    val section: SettingsSection,
    @param:StringRes val titleId: Int,
) {
    UNRECOGNIZED_FILES(SettingsSection.LIBRARY, R.string.settings_title_unrecognized_files),
    INPUT_DEVICES(SettingsSection.CONTROLS, R.string.settings_title_gamepad_settings),
    SAVE_SYNC(SettingsSection.SAVES, R.string.settings_title_saves_backup),
    CORES_SELECTION(SettingsSection.EMULATION, R.string.settings_title_open_cores_selection),
    GRAPHICS_API(SettingsSection.EMULATION, R.string.settings_title_graphics_api_selection),
    BIOS(SettingsSection.EMULATION, R.string.settings_title_display_bios_info),
    DEVICE_PROFILE(SettingsSection.EMULATION, R.string.settings_title_device_profile),
}

/** Nested pages use this callback to open another settings page without owning shell state. */
internal val LocalSettingsNavigation = staticCompositionLocalOf<(SettingsSubscreen) -> Unit> {
    { error("Settings navigation requires the settings host") }
}
