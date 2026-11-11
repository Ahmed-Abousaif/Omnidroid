package com.omnidroid.app.mobile.feature.gamemenu.coreoptions

import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.gamemenu.GameMenuRequest
import com.omnidroid.app.mobile.shared.compose.sidemenu.MenuOption
import com.omnidroid.app.mobile.shared.compose.sidemenu.SideMenuPage
import com.omnidroid.app.mobile.shared.compose.sidemenu.MenuSection
import com.omnidroid.app.mobile.shared.compose.sidemenu.MenuChoice
import com.omnidroid.app.mobile.shared.compose.sidemenu.MenuToggle
import com.omnidroid.app.shared.coreoptions.CoreOptionsPreferenceHelper
import com.omnidroid.app.shared.settings.ControllerConfigsManager
import com.omnidroid.app.utils.android.settings.booleanPreferenceState
import com.omnidroid.app.utils.android.settings.stringPreferenceState
import com.omnidroid.lib.core.CoreVariablesManager

@Composable
fun GameMenuCoreOptionsScreen(viewModel: GameMenuCoreOptionsViewModel, gameMenuRequest: GameMenuRequest) {
    val context = LocalContext.current
    val connected by viewModel.connectedGamePads.collectAsState(0)
    val request = gameMenuRequest
    val options = remember(request) { (request.coreOptions + request.advancedCoreOptions).filter { it.getEntriesValues().isNotEmpty() && it.getEntries(context).isNotEmpty() } }
    val ports = (0 until maxOf(1, connected)).mapNotNull { port ->
        request.coreConfig.controllerConfigs[port]?.takeIf { it.size >= 2 }?.let { port to it }
    }
    SideMenuPage("game-options") {
        MenuSection("controllers", visible = ports.isNotEmpty(), titleRes = R.string.core_settings_category_controllers) {
            ports.forEach { (port, configs) ->
                key(port) {
                    val pref = stringPreferenceState(
                        ControllerConfigsManager.getSharedPreferencesId(request.game.systemId, request.coreConfig.coreID, port),
                        configs.first().name,
                    )
                    MenuChoice(
                        id = "controller-$port", title = { Text(stringResource(R.string.core_settings_controller, (port + 1).toString())) },
                        options = configs.map { MenuOption(it.name, stringResource(it.displayName)) }, value = pref.value,
                    ) { pref.value = it }
                }
            }
        }
        MenuSection("core", visible = options.isNotEmpty()) {
            options.forEach { option ->
                key(option.getKey()) {
                    val values = option.getEntriesValues()
                    val entries = option.getEntries(context)
                    val prefKey = CoreVariablesManager.computeSharedPreferenceKey(option.getKey(), request.game.systemId)
                    if (values.toSet() == CoreOptionsPreferenceHelper.BOOLEAN_SET) {
                        val pref = booleanPreferenceState(prefKey, option.getCurrentValue() == "enabled")
                        MenuToggle(id = "core-${option.getKey()}", title = { Text(option.getDisplayName(context)) }, value = pref.value) {
                            pref.value = it
                        }
                    } else {
                        val pref = stringPreferenceState(prefKey, values.first())
                        MenuChoice(
                            id = "core-${option.getKey()}", title = { Text(option.getDisplayName(context)) },
                            options = values.zip(entries) { value, label -> MenuOption(value, label) }, value = pref.value,
                        ) { pref.value = it }
                    }
                }
            }
        }
    }
}
