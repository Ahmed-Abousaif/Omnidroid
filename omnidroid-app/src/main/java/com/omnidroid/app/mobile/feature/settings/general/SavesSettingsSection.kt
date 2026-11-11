package com.omnidroid.app.mobile.feature.settings.general

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.omnidroid.R
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsGroup
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsMenuLink
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsSwitch
import com.omnidroid.app.utils.android.settings.booleanPreferenceState

@Composable
internal fun SavesSettings(
    isSaveSyncSupported: Boolean,
    onNavigateSubscreen: (SettingsSubscreen) -> Unit,
) {
    OmnidroidSettingsGroup {
        OmnidroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_autosave, true),
            titleRes = R.string.settings_title_enable_autosave,
            subtitleRes = R.string.settings_description_enable_autosave,
        )
        OmnidroidSettingsMenuLink(
            titleRes = R.string.settings_title_saves_backup,
            subtitle = {
                Text(
                    text =
                        stringResource(
                            id =
                                if (isSaveSyncSupported) {
                                    R.string.settings_description_save_sync
                                } else {
                                    R.string.settings_description_saves_backup
                                },
                        ),
                )
            },
            onClick = { onNavigateSubscreen(SettingsSubscreen.SAVE_SYNC) },
        )
    }
}
