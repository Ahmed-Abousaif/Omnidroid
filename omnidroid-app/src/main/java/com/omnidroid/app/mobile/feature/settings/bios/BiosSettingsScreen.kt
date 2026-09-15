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

@Composable
fun BiosScreen(
    modifier: Modifier = Modifier,
    viewModel: BiosSettingsViewModel,
) {
    val uiState =
        viewModel.uiState
            .collectAsState()
            .value

    OmnidroidSettingsPage(modifier = modifier.fillMaxSize()) {
        if (uiState.detected.isNotEmpty()) {
            DetectedEntries(uiState.detected)
        }
        if (uiState.notDetected.isNotEmpty()) {
            SupportedEntries(uiState.notDetected)
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
