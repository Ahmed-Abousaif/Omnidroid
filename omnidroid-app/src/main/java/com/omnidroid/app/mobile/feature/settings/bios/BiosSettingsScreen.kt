package com.omnidroid.app.mobile.feature.settings.bios
import android.content.Context
import com.omnidroid.lib.backend.ConsoleBackendRegistry
import com.omnidroid.app.shared.game.backend.RequirementImporter
import com.omnidroid.lib.backend.RequirementAction
import com.omnidroid.lib.backend.RequirementDescriptor
import com.omnidroid.lib.backend.RequirementItem
import com.omnidroid.lib.library.SystemCoreConfig
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.omnidroid.app.OmnidroidApplication
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.omnidroid.R
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsGroup
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsMenuLink
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsPage
import com.omnidroid.app.utils.android.settings.SettingsHorizontalDivider
import com.omnidroid.lib.bios.Bios
import com.omnidroid.lib.bios.Ps2Bios
import com.omnidroid.lib.library.SystemID

@Composable
fun BiosScreen(
    modifier: Modifier = Modifier,
    viewModel: BiosSettingsViewModel,
) {
    val context = LocalContext.current
    var revision by remember { mutableStateOf(0) }
    var currentImportAction by remember { mutableStateOf<RequirementAction?>(null) }
    val keysPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val action = currentImportAction
                if (action != null) {
                    OmnidroidApplication.scope(context.applicationContext).launch {
                        val result = withContext(Dispatchers.IO) {
                            RequirementImporter.import(context.applicationContext, uri, action)
                        }
                        revision++
                        val isFirmware = when (action) {
                            is RequirementAction.ArchiveImport -> true
                            is RequirementAction.FileImport -> action.destinationDir.contains("registered")
                            else -> false
                        }
                        if (result.success) {
                            val msg = if (isFirmware) {
                                context.getString(R.string.settings_switch_firmware_import_success)
                            } else {
                                context.getString(R.string.settings_switch_keys_import_success)
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        } else {
                            val msg = if (isFirmware) {
                                context.getString(R.string.settings_switch_firmware_import_failed)
                            } else {
                                context.getString(R.string.settings_switch_keys_import_failed)
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    val uiState =
        viewModel.uiState
            .collectAsState()
            .value
    val ps2 = uiState.detected.filter { it.systemID == SystemID.PS2 }
    val downloading =
        viewModel.downloading
            .collectAsState()
            .value
    val downloadFailed =
        viewModel.downloadFailed
            .collectAsState()
            .value
    OmnidroidSettingsPage(modifier = modifier.fillMaxSize()) {
        Ps2Choices(
            detected = ps2.map { it.libretroFileName }.toSet(),
            selection = uiState.ps2Selection,
            downloading = downloading,
            downloadFailed = downloadFailed,
            onSelect = viewModel::selectPs2Bios,
            onDownload = viewModel::downloadPs2Bios,
        )
        ConsoleBackendRegistry.allDescriptors().forEach { descriptor ->
            val firstCore = descriptor.ownedCoreIDs.firstOrNull() ?: return@forEach
            val reqs = descriptor.requirementsFor(SystemCoreConfig(firstCore, hashMapOf())) ?: return@forEach
            SettingsHorizontalDivider()
            RequirementSection(
                descriptor = reqs,
                context = context,
                revision = revision,
                onAction = { item ->
                    when (val action = item.action) {
                        is RequirementAction.FileImport, is RequirementAction.ArchiveImport -> {
                            currentImportAction = action
                            keysPickerLauncher.launch(arrayOf("*/*"))
                        }
                        else -> {}
                    }
                },
            )
        }
        if (uiState.detected.isNotEmpty()) {
            SettingsHorizontalDivider()
            DetectedEntries(uiState.detected)
        }
        if (uiState.notDetected.isNotEmpty()) {
            SettingsHorizontalDivider()
            SupportedEntries(uiState.notDetected)
        }
    }
}

@Composable
private fun RequirementSection(
    descriptor: RequirementDescriptor,
    context: Context,
    revision: Int,
    onAction: (RequirementItem) -> Unit,
) {
    OmnidroidSettingsGroup(
        title = { Text(text = descriptor.title) },
    ) {
        descriptor.items.forEach { item ->
            val status by produceState<Pair<Boolean, String?>?>(null, item.id, revision) {
                value = withContext(Dispatchers.IO) {
                    item.isFulfilled(context) to item.statusSummary?.invoke(context)
                }
            }
            val isFulfilled = status?.first == true
            val statusSummary = status?.second
            OmnidroidSettingsMenuLink(
                title = { Text(text = item.title) },
                subtitle = {
                    Text(
                        text =
                            statusSummary ?: stringResource(
                                id =
                                    if (isFulfilled) {
                                        R.string.settings_switch_keys_installed
                                    } else {
                                        R.string.settings_switch_keys_missing
                                    },
                            ),
                    )
                },
                onClick = { onAction(item) },
            )
        }
    }
}

@Composable
private fun Ps2Choices(
    detected: Set<String>,
    selection: String?,
    downloading: String?,
    downloadFailed: String?,
    onSelect: (String?) -> Unit,
    onDownload: (String) -> Unit,
) {
    val options =
        listOf(
            Ps2Bios.USA to R.string.settings_ps2_bios_usa,
            Ps2Bios.EUROPE to R.string.settings_ps2_bios_europe,
            Ps2Bios.JAPAN_SERVED to R.string.settings_ps2_bios_japan,
        )
    OmnidroidSettingsGroup(
        titleRes = R.string.settings_bios_category_ps2,
    ) {
        OmnidroidSettingsMenuLink(
            titleRes = R.string.settings_ps2_bios_auto,
            subtitleRes = R.string.settings_ps2_bios_auto_subtitle,
            enabled = selection != null,
            onClick = { onSelect(null) },
        )
        options.forEach { (fileName, title) ->
            val present = fileName in detected
            val pinned = selection == fileName
            val inProgress = downloading == fileName
            val failed = downloadFailed == fileName
            OmnidroidSettingsMenuLink(
                title = { Text(text = stringResource(id = title)) },
                subtitle = {
                    Text(
                        text =
                            when {
                                inProgress -> stringResource(id = R.string.settings_ps2_bios_downloading)
                                pinned -> stringResource(id = R.string.settings_ps2_bios_pinned)
                                present -> fileName
                                failed -> stringResource(id = R.string.settings_ps2_bios_download_failed)
                                else -> stringResource(id = R.string.settings_ps2_bios_download)
                            },
                    )
                },
                enabled = !pinned && !inProgress && downloading == null,
                onClick = {
                    if (present) onSelect(fileName) else onDownload(fileName)
                },
            )
        }
    }
}

@Composable
private fun DetectedEntries(detected: List<Bios>) {
    OmnidroidSettingsGroup(
        titleRes = R.string.settings_bios_category_detected,
    ) {
        detected.forEach {
            BiosEntry(it, true)
        }
    }
}

@Composable
private fun SupportedEntries(supported: List<Bios>) {
    OmnidroidSettingsGroup(
        titleRes = R.string.settings_bios_category_not_detected,
    ) {
        supported.forEach {
            BiosEntry(it, false)
        }
    }
}

@Composable
fun BiosEntry(
    bios: Bios,
    detected: Boolean,
) {
    OmnidroidSettingsMenuLink(
        title = { Text(text = bios.description) },
        subtitle = { Text(text = bios.displayName()) },
        enabled = detected,
        onClick = { },
    )
}
