package com.omnidroid.app.mobile.feature.settings.coreselection

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.alorma.compose.settings.storage.memory.rememberMemoryIntSettingState
import com.omnidroid.app.utils.android.settings.OmnidroidCardSettingsGroup
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsList
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsPage

@Composable
fun CoresSelectionScreen(
    modifier: Modifier = Modifier,
    viewModel: CoresSelectionViewModel,
) {
    val applicationContext = LocalContext.current.applicationContext

    val cores = viewModel.getSelectedCores().collectAsState(emptyList()).value

    val indexingInProgress = viewModel.indexingInProgress.collectAsState(false).value

    OmnidroidSettingsPage(modifier = modifier.fillMaxWidth()) {
        OmnidroidCardSettingsGroup {
            cores.forEach { (system, core) ->
                androidx.compose.runtime.key(system.id) {
                    val state = rememberMemoryIntSettingState(system.systemCoreConfigs.indexOf(core))

                    OmnidroidSettingsList(
                        state = state,
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
