package com.omnidroid.app.mobile.feature.settings.bios

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.omnidroid.R
import com.omnidroid.app.utils.android.settings.OmnidroidCardSettingsGroup
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsMenuLink
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsPage
import com.omnidroid.lib.bios.Bios
import com.omnidroid.lib.bios.Ps2Bios
import com.omnidroid.lib.library.SystemID

@Composable
fun BiosScreen(
    modifier: Modifier = Modifier,
    viewModel: BiosSettingsViewModel,
) {
    val uiState =
        viewModel.uiState
            .collectAsState()
            .value
    val ps2 = uiState.detected.filter { it.systemID == SystemID.PS2 }
    val downloading =
        viewModel.downloading
            .collectAsState()
            .value

    OmnidroidSettingsPage(modifier = modifier.fillMaxSize()) {
        Ps2Choices(
            detected = ps2.map { it.libretroFileName }.toSet(),
            selection = uiState.ps2Selection,
            downloading = downloading,
            onSelect = viewModel::selectPs2Bios,
            onDownload = viewModel::downloadPs2Bios,
        )
        if (uiState.detected.isNotEmpty()) {
            DetectedEntries(uiState.detected)
        }
        if (uiState.notDetected.isNotEmpty()) {
            SupportedEntries(uiState.notDetected)
        }
    }
}

@Composable
private fun Ps2Choices(
    detected: Set<String>,
    selection: String?,
    downloading: String?,
    onSelect: (String?) -> Unit,
    onDownload: (String) -> Unit,
) {
    val options =
        listOf(
            Ps2Bios.USA to R.string.settings_ps2_bios_usa,
            Ps2Bios.EUROPE to R.string.settings_ps2_bios_europe,
            Ps2Bios.JAPAN_SERVED to R.string.settings_ps2_bios_japan,
        )
    OmnidroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_bios_category_ps2)) },
    ) {
        OmnidroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_ps2_bios_auto)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_ps2_bios_auto_subtitle)) },
            enabled = selection != null,
            onClick = { onSelect(null) },
        )
        options.forEach { (fileName, title) ->
            val present = fileName in detected
            val pinned = selection == fileName
            val inProgress = downloading == fileName
            OmnidroidSettingsMenuLink(
                title = { Text(text = stringResource(id = title)) },
                subtitle = {
                    Text(
                        text =
                            when {
                                inProgress -> stringResource(id = R.string.settings_ps2_bios_downloading)
                                pinned -> stringResource(id = R.string.settings_ps2_bios_pinned)
                                present -> fileName
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
    OmnidroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_bios_category_detected)) },
    ) {
        detected.forEach {
            BiosEntry(it, true)
        }
    }
}

@Composable
private fun SupportedEntries(supported: List<Bios>) {
    OmnidroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_bios_category_not_detected)) },
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
