package com.omnidroid.app.mobile.feature.settings.general

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsPage
import com.omnidroid.app.utils.android.settings.SettingsHorizontalDivider
import com.omnidroid.lib.library.MetaSystemID

@Composable
internal fun SettingsContentPane(
    section: SettingsSection,
    activeSubscreen: SettingsSubscreen?,
    parentTitleId: Int,
    onNavigateSubscreen: (SettingsSubscreen) -> Unit,
    onBackToSection: () -> Unit,
    state: SettingsViewModel.State,
    cacheState: SettingsViewModel.CacheState?,
    scanInProgress: Boolean,
    indexingInProgress: Boolean,
    viewModel: SettingsViewModel,
    navController: NavController,
    availableConsoles: List<MetaSystemID>,
    modifier: Modifier = Modifier,
) {
    if (activeSubscreen != null && activeSubscreen.section == section) {
        Column(modifier = modifier.fillMaxSize()) {
            SettingsBackHeader(
                sectionTitle = stringResource(parentTitleId),
                onBack = onBackToSection,
            )
            SettingsHorizontalDivider()
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                CompositionLocalProvider(LocalSettingsNavigation provides onNavigateSubscreen) {
                    SettingsSubscreenContent(
                        subscreen = activeSubscreen,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        return
    }

    OmnidroidSettingsPage(modifier = modifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        when (section) {
            SettingsSection.LIBRARY -> {
                RomsSettings(state, { viewModel.changeLocalStorageFolder() }, indexingInProgress, scanInProgress)
                SettingsHorizontalDivider()
                StartupScreenSettings(availableConsoles)
                SettingsHorizontalDivider()
                LibraryMaintenanceSettings(onNavigateSubscreen)
            }
            SettingsSection.DISPLAY -> DisplaySettings(navController)
            SettingsSection.CONTROLS -> ControllerSettings(onNavigateSubscreen)
            SettingsSection.SAVES -> SavesSettings(state.isSaveSyncSupported, onNavigateSubscreen)
            SettingsSection.EMULATION -> EmulationSettings(indexingInProgress, onNavigateSubscreen)
            SettingsSection.ADVANCED -> AdvancedSettings(cacheState, viewModel)
            SettingsSection.ABOUT -> AboutSettings()
        }
    }
}
