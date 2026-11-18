package com.omnidroid.app.mobile.feature.settings.general

import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.documentfile.provider.DocumentFile
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.dialog.DialogActionRole
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialog
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogButton
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsGroup
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsMenuLink
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsSwitch
import com.omnidroid.app.utils.android.settings.booleanPreferenceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun SavesSettings(
    state: SettingsViewModel.State,
    onChangeSavesFolder: () -> Unit,
    onResetSavesFolder: () -> Unit,
    onDismissMigrationError: () -> Unit = {},
    onNavigateSubscreen: (SettingsSubscreen) -> Unit,
) {
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }

    val customSavesDir = state.customSavesDirectory
    val defaultDirectoryLabel = stringResource(R.string.settings_save_directory_default)

    val currentDirectoryName by produceState(
        initialValue = defaultDirectoryLabel,
        key1 = context,
        key2 = customSavesDir,
        key3 = defaultDirectoryLabel,
    ) {
        value =
            withContext(Dispatchers.IO) {
                if (customSavesDir.isBlank()) {
                    defaultDirectoryLabel
                } else {
                    runCatching {
                        val uri = Uri.parse(customSavesDir)
                        if (uri.scheme == "content") {
                            DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment
                        } else {
                            val path =
                                if (customSavesDir.startsWith("file://")) {
                                    uri.path ?: customSavesDir
                                } else {
                                    customSavesDir
                                }
                            val file = File(path)
                            file.name.ifBlank { file.path }
                        }
                    }.getOrNull()?.takeIf { it.isNotBlank() } ?: customSavesDir
                }
            }
    }

    OmnidroidSettingsGroup {
        OmnidroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_autosave, true),
            titleRes = R.string.settings_title_enable_autosave,
            subtitleRes = R.string.settings_description_enable_autosave,
        )
        OmnidroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_confirm_game_menu_actions, true),
            titleRes = R.string.settings_title_confirm_game_menu_actions,
            subtitleRes = R.string.settings_description_confirm_game_menu_actions,
        )
        OmnidroidSettingsMenuLink(
            titleRes = R.string.settings_title_saves_backup,
            subtitle = {
                Text(
                    text =
                        stringResource(
                            id =
                                if (state.isSaveSyncSupported) {
                                    R.string.settings_description_save_sync
                                } else {
                                    R.string.settings_description_saves_backup
                                },
                        ),
                )
            },
            onClick = { onNavigateSubscreen(SettingsSubscreen.SAVE_SYNC) },
        )
        OmnidroidSettingsMenuLink(
            titleRes = R.string.settings_title_custom_save_directory,
            subtitle = {
                Text(text = currentDirectoryName)
            },
            onClick = { onChangeSavesFolder() },
        )
        if (customSavesDir.isNotBlank()) {
            OmnidroidSettingsMenuLink(
                titleRes = R.string.settings_action_reset_save_directory,
                subtitleRes = R.string.settings_description_reset_save_directory,
                onClick = { showResetDialog = true },
            )
        }
    }

    if (showResetDialog) {
        OmnidroidDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.settings_confirm_reset_save_directory_title)) },
            text = { Text(stringResource(R.string.settings_confirm_reset_save_directory_message)) },
            confirmButton = {
                OmnidroidDialogButton(
                    role = DialogActionRole.Destructive,
                    onClick = {
                        onResetSavesFolder()
                        showResetDialog = false
                    },
                ) {
                    Text(stringResource(R.string.settings_confirm_reset_save_directory_action))
                }
            },
            dismissButton = {
                OmnidroidDialogButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (state.isMigrating) {
        OmnidroidDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.settings_migrating_saves_title)) },
            text = { Text(stringResource(R.string.settings_migrating_saves_message)) },
            confirmButton = {},
        )
    }

    if (state.migrationError) {
        OmnidroidDialog(
            onDismissRequest = onDismissMigrationError,
            title = { Text(stringResource(R.string.settings_migrating_saves_failed_title)) },
            text = { Text(stringResource(R.string.settings_migrating_saves_failed)) },
            confirmButton = {
                OmnidroidDialogButton(onClick = onDismissMigrationError) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
}
