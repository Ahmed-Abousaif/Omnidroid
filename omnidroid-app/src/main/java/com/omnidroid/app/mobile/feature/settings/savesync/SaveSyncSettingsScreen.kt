package com.omnidroid.app.mobile.feature.settings.savesync

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.omnidroid.R
import com.omnidroid.app.shared.savesync.SaveBackupManager
import com.omnidroid.app.shared.savesync.SaveBackupWork
import com.omnidroid.app.shared.savesync.SaveSyncWork
import com.omnidroid.app.utils.android.settings.OmnidroidCardSettingsGroup
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsList
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsListMultiSelect
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsMenuLink
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsPage
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsSwitch
import com.omnidroid.app.utils.android.settings.booleanPreferenceState
import com.omnidroid.app.utils.android.settings.indexPreferenceState
import com.omnidroid.app.utils.android.settings.stringsSetPreferenceState
import com.omnidroid.lib.savesync.CloudSaveProviderInfo
import com.omnidroid.lib.savesync.ConflictResolution

@Composable
fun SaveSyncSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SaveSyncSettingsViewModel,
    showConflicts: Boolean = false,
) {
    val context = LocalContext.current
    val saveSyncState = viewModel.uiState.collectAsState().value
    val isSyncInProgress = viewModel.saveSyncInProgress.collectAsState(true).value
    var signOutProvider by remember { mutableStateOf<CloudSaveProviderInfo?>(null) }
    var showingConflicts by remember { mutableStateOf(showConflicts) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
            uri?.let { SaveBackupWork.enqueueExport(context, it) }
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { SaveBackupWork.enqueueImport(context, it) }
        }

    OmnidroidSettingsPage(modifier = modifier.fillMaxSize()) {
        saveSyncState.providers.forEach { provider ->
            OmnidroidCardSettingsGroup(title = { Text(provider.displayName) }) {
                OmnidroidSettingsMenuLink(
                    title = { Text(provider.accountLabel.ifBlank { stringResource(R.string.settings_save_sync_not_configured) }) },
                    subtitle = {
                        val parts = mutableListOf<String>()
                        if (provider.remoteUsage.isNotBlank()) parts += provider.remoteUsage
                        parts += provider.lastSync
                        Text(parts.joinToString("\n"))
                    },
                    enabled = !isSyncInProgress,
                    onClick = {
                        if (!provider.configured && provider.signInActivity != null) {
                            context.startActivity(Intent(context, provider.signInActivity))
                        }
                    },
                )
                if (!provider.lastError.isNullOrBlank()) {
                    OmnidroidSettingsMenuLink(
                        title = { Text(stringResource(R.string.settings_save_sync_last_error, provider.lastError!!), color = MaterialTheme.colorScheme.error) },
                        enabled = false,
                        onClick = {},
                    )
                }
                if (provider.configured) {
                    OmnidroidSettingsMenuLink(
                        title = { Text(stringResource(R.string.settings_save_sync_sign_out)) },
                        enabled = !isSyncInProgress,
                        onClick = { signOutProvider = provider },
                    )
                } else {
                    OmnidroidSettingsMenuLink(
                        title = { Text(stringResource(R.string.settings_save_sync_connect)) },
                        enabled = !isSyncInProgress && provider.signInActivity != null,
                        onClick = {
                            provider.signInActivity?.let { context.startActivity(Intent(context, it)) }
                        },
                    )
                }
            }
        }

        OmnidroidCardSettingsGroup {
            val configured = saveSyncState.isConfigured
            OmnidroidSettingsSwitch(
                state = booleanPreferenceState(R.string.pref_key_save_sync_enable, default = false),
                title = { Text(text = stringResource(id = R.string.settings_save_sync_include_saves)) },
                subtitle = {
                    Text(
                        text =
                            stringResource(
                                id = R.string.settings_save_sync_include_saves_description,
                                saveSyncState.savesSpace,
                            ),
                    )
                },
                enabled = configured && !isSyncInProgress,
            )
            OmnidroidSettingsListMultiSelect(
                state =
                    stringsSetPreferenceState(
                        stringResource(R.string.pref_key_save_sync_cores),
                        emptySet(),
                    ),
                title = { Text(text = stringResource(id = R.string.settings_save_sync_include_states)) },
                subtitle = { Text(text = stringResource(id = R.string.settings_save_sync_include_states_description)) },
                entryValues = saveSyncState.coreNames,
                entries = saveSyncState.coreVisibleNames,
                enabled = configured && !isSyncInProgress,
                confirmButton = stringResource(id = R.string.ok),
            )
            OmnidroidSettingsSwitch(
                state = booleanPreferenceState(R.string.pref_key_save_sync_auto, default = false),
                title = { Text(text = stringResource(id = R.string.settings_save_sync_enable_auto)) },
                subtitle = { Text(text = stringResource(id = R.string.settings_save_sync_enable_auto_description)) },
                enabled = configured && !isSyncInProgress,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        SaveSyncWork.enqueueAutoWork(context)
                    } else {
                        SaveSyncWork.cancelAutoWork(context)
                    }
                },
            )
            val intervalValues = stringArrayResource(R.array.pref_key_save_sync_interval_values).toList()
            val intervalNames = stringArrayResource(R.array.pref_key_save_sync_interval_display_names).toList()
            OmnidroidSettingsList(
                state = indexPreferenceState(R.string.pref_key_save_sync_interval, "3h", intervalValues),
                title = { Text(stringResource(R.string.settings_save_sync_interval)) },
                items = intervalNames,
                enabled = configured && !isSyncInProgress,
                onItemSelected = { _, _ -> SaveSyncWork.enqueueAutoWork(context) },
            )
            OmnidroidSettingsSwitch(
                state = booleanPreferenceState(R.string.pref_key_save_sync_before_game, default = true),
                title = { Text(stringResource(R.string.settings_save_sync_before_game)) },
                subtitle = { Text(stringResource(R.string.settings_save_sync_before_game_description)) },
                enabled = configured && !isSyncInProgress,
            )
            OmnidroidSettingsMenuLink(
                title = { Text(text = stringResource(id = R.string.settings_save_sync_refresh)) },
                subtitle = {
                    Text(
                        text =
                            stringResource(
                                id = R.string.settings_save_sync_refresh_description,
                                saveSyncState.lastSyncInfo,
                            ),
                    )
                },
                enabled = configured && !isSyncInProgress,
                onClick = { SaveSyncWork.enqueueManualWork(context) },
            )
            OmnidroidSettingsMenuLink(
                title = { Text(stringResource(R.string.settings_save_sync_conflicts)) },
                subtitle = {
                    Text(
                        if (saveSyncState.conflicts.isEmpty()) {
                            stringResource(R.string.settings_save_sync_conflicts_none)
                        } else {
                            stringResource(R.string.settings_save_sync_conflicts_count, saveSyncState.conflicts.size)
                        },
                    )
                },
                enabled = !isSyncInProgress,
                onClick = { showingConflicts = true },
            )
            OmnidroidSettingsMenuLink(
                title = { Text(stringResource(R.string.settings_save_sync_export)) },
                subtitle = { Text(stringResource(R.string.settings_save_sync_export_description)) },
                enabled = !isSyncInProgress,
                onClick = { exportLauncher.launch(SaveBackupManager.suggestedFileName()) },
            )
            OmnidroidSettingsMenuLink(
                title = { Text(stringResource(R.string.settings_save_sync_import)) },
                subtitle = { Text(stringResource(R.string.settings_save_sync_import_description)) },
                enabled = !isSyncInProgress,
                onClick = { importLauncher.launch(arrayOf("application/zip", "*/*")) },
            )
        }
    }

    signOutProvider?.let { provider ->
        AlertDialog(
            onDismissRequest = { signOutProvider = null },
            title = { Text(stringResource(R.string.settings_save_sync_sign_out)) },
            text = { Text(stringResource(R.string.settings_save_sync_sign_out_confirm, provider.displayName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.signOut(provider.id)
                        signOutProvider = null
                    },
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { signOutProvider = null }) {
                    Text(stringResource(R.string.pre_game_sync_cancel))
                }
            },
        )
    }

    if (showingConflicts) {
        AlertDialog(
            onDismissRequest = { showingConflicts = false },
            title = { Text(stringResource(R.string.settings_save_sync_conflicts)) },
            text = {
                if (saveSyncState.conflicts.isEmpty()) {
                    Text(stringResource(R.string.settings_save_sync_conflicts_none))
                } else {
                    androidx.compose.foundation.layout.Column {
                        saveSyncState.conflicts.forEach { conflict ->
                            Text(conflict.relativePath, style = MaterialTheme.typography.titleSmall)
                            androidx.compose.foundation.layout.Row {
                                TextButton(onClick = { viewModel.resolveConflict(conflict.id, ConflictResolution.USE_LOCAL) }) {
                                    Text(stringResource(R.string.settings_save_sync_conflict_use_local))
                                }
                                TextButton(onClick = { viewModel.resolveConflict(conflict.id, ConflictResolution.USE_CLOUD) }) {
                                    Text(stringResource(R.string.settings_save_sync_conflict_use_cloud))
                                }
                                TextButton(onClick = { viewModel.resolveConflict(conflict.id, ConflictResolution.KEEP_BOTH) }) {
                                    Text(stringResource(R.string.settings_save_sync_conflict_keep_both))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showingConflicts = false }) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}
