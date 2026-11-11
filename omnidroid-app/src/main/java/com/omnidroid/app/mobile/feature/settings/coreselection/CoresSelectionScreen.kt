package com.omnidroid.app.mobile.feature.settings.coreselection

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsGroup
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsList
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsPage

@Composable
fun CoresSelectionScreen(
    modifier: Modifier = Modifier,
    viewModel: CoresSelectionViewModel,
) {
    val applicationContext = LocalContext.current.applicationContext

    val cores = viewModel.selectedCores.collectAsState(emptyList()).value

    val indexingInProgress = viewModel.indexingInProgress.collectAsState(false).value

    OmnidroidSettingsPage(modifier = modifier.fillMaxWidth()) {
        OmnidroidSettingsGroup {
            cores.forEach { (system, core) ->
                androidx.compose.runtime.key(system.id) {

                    OmnidroidSettingsList(
                        selectedIndex = system.systemCoreConfigs.indexOf(core),
                        title = { Text(text = stringResource(system.titleResId)) },
                        items = system.systemCoreConfigs.map { it.coreID.coreDisplayName },
                        enabled = !indexingInProgress,
                        onItemSelected = { index, _ ->
                            viewModel.changeCore(system, system.systemCoreConfigs[index], applicationContext)
                        },
                    )
                }
            }
        }
    }
}
