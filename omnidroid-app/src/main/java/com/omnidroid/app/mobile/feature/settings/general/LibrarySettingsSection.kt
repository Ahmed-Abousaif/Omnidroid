package com.omnidroid.app.mobile.feature.settings.general

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.produceState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.documentfile.provider.DocumentFile
import com.omnidroid.R
import com.omnidroid.app.OmnidroidApplication
import com.omnidroid.app.mobile.feature.library.LibraryStartupStore
import com.omnidroid.app.mobile.feature.library.StartupDestinationPickerDialog
import com.omnidroid.app.mobile.feature.library.startupDestinationLabel
import com.omnidroid.app.mobile.feature.library.startupDestinations
import com.omnidroid.app.shared.library.LibraryIndexScheduler
import com.omnidroid.app.shared.library.LibraryPeriodicScanScheduler
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsGroup
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsMenuLink
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsSwitch
import com.omnidroid.app.utils.android.settings.booleanPreferenceState
import com.omnidroid.lib.library.MetaSystemID
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun StartupScreenSettings(availableConsoles: List<MetaSystemID>) {
    val context = LocalContext.current
    val startupStore = remember { LibraryStartupStore(context) }
    val selected by startupStore.observe().collectAsState(initial = startupStore.get())
    var showPicker by remember { mutableStateOf(false) }

    val destinations = remember(availableConsoles) { startupDestinations(availableConsoles) }

    OmnidroidSettingsGroup(
        titleRes = R.string.settings_section_startup,
    ) {
        OmnidroidSettingsMenuLink(
            titleRes = R.string.settings_title_startup_destination,
            subtitle = { Text(text = startupDestinationLabel(selected)) },
            onClick = { showPicker = true },
        )
    }

    if (!showPicker) return

    StartupDestinationPickerDialog(
        destinations = destinations,
        selected = selected,
        onSelected = { destination ->
            startupStore.set(destination)
            showPicker = false
        },
        onDismiss = { showPicker = false },
    )
}

@Composable
internal fun LibraryMaintenanceSettings(
    onNavigateSubscreen: (SettingsSubscreen) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clearMissingDialogState = remember { mutableStateOf(false) }
    val periodicScan = booleanPreferenceState(R.string.pref_key_periodic_library_scan, false)

    OmnidroidSettingsGroup(
        titleRes = R.string.settings_section_maintenance,
    ) {
        OmnidroidSettingsSwitch(
            state = periodicScan,
            titleRes = R.string.settings_title_periodic_library_scan,
            subtitleRes = R.string.settings_description_periodic_library_scan,
        )
        LaunchedEffect(periodicScan.value) {
            LibraryPeriodicScanScheduler.syncWithPreference(context)
        }
        OmnidroidSettingsMenuLink(
            titleRes = R.string.settings_title_unrecognized_files,
            subtitleRes = R.string.settings_description_unrecognized_files,
            onClick = { onNavigateSubscreen(SettingsSubscreen.UNRECOGNIZED_FILES) },
        )
        OmnidroidSettingsMenuLink(
            titleRes = R.string.settings_title_clear_missing_games,
            subtitleRes = R.string.settings_description_clear_missing_games,
            onClick = { clearMissingDialogState.value = true },
        )
    }

    if (clearMissingDialogState.value) {
        AlertDialog(
            title = { Text(stringResource(id = R.string.settings_title_clear_missing_games)) },
            text = { Text(stringResource(id = R.string.settings_description_clear_missing_games)) },
            onDismissRequest = { clearMissingDialogState.value = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearMissingDialogState.value = false
                        scope.launch {
                            val removed =
                                withContext(Dispatchers.IO) {
                                    val db =
                                        (context.applicationContext as OmnidroidApplication)
                                            .retrogradeDb
                                    val missing = db.gameDao().selectUnavailable()
                                    missing.forEach { game ->
                                        game.customCoverPath?.let { path ->
                                            runCatching { File(path.substringBefore('?')).delete() }
                                        }
                                    }
                                    if (missing.isNotEmpty()) {
                                        db.gameDao().delete(missing)
                                    }
                                    missing.size
                                }
                            val message =
                                if (removed == 0) {
                                    context.getString(R.string.settings_clear_missing_games_none)
                                } else {
                                    context.getString(R.string.settings_clear_missing_games_done, removed)
                                }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Text(text = stringResource(id = R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { clearMissingDialogState.value = false }) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            },
        )
    }
}

@Composable
internal fun RomsSettings(
    state: SettingsViewModel.State,
    onChangeFolder: () -> Unit,
    indexingInProgress: Boolean,
    scanInProgress: Boolean,
) {
    val context = LocalContext.current

    val currentDirectory = state.currentDirectory
    val emptyDirectory = stringResource(R.string.none)

    val currentDirectoryName by produceState(emptyDirectory, context, currentDirectory, emptyDirectory) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                DocumentFile.fromTreeUri(context, Uri.parse(currentDirectory))?.name
            }.getOrNull() ?: emptyDirectory
        }
    }

    OmnidroidSettingsGroup(
        titleRes = R.string.settings_section_folder,
    ) {
        OmnidroidSettingsMenuLink(
            titleRes = R.string.directory,
            subtitle = { Text(text = currentDirectoryName) },
            onClick = { onChangeFolder() },
            enabled = !indexingInProgress,
        )
        if (scanInProgress) {
            OmnidroidSettingsMenuLink(
                titleRes = R.string.stop,
                onClick = { LibraryIndexScheduler.cancelLibrarySync(context) },
            )
        } else {
            OmnidroidSettingsMenuLink(
                titleRes = R.string.rescan,
                onClick = { LibraryIndexScheduler.scheduleLibrarySync(context) },
                enabled = !indexingInProgress,
            )
        }
    }
}
