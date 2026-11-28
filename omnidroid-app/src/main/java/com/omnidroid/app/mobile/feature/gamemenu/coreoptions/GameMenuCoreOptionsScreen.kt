package com.omnidroid.app.mobile.feature.gamemenu.coreoptions

import android.widget.Toast
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
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.ra.HardcoreDecision

@Composable
fun GameMenuCoreOptionsScreen(
    viewModel: GameMenuCoreOptionsViewModel,
    gameMenuRequest: GameMenuRequest,
    canApplyCoreOption: (key: String, value: String) -> HardcoreDecision = { _, _ -> HardcoreDecision.Allowed },
) {
    val context = LocalContext.current
    val request = gameMenuRequest
    val options = remember(request) { (request.coreOptions + request.advancedCoreOptions).filter { it.getEntriesValues().isNotEmpty() && it.getEntries(context).isNotEmpty() } }
    // Every configurable port, not just connected pads, so player 2+ can be set up before plugging in.
    val ports = request.coreConfig.controllerConfigs.toSortedMap().mapNotNull { (port, configs) ->
        configs.takeIf { it.size >= 2 }?.let { port to it }
    }
    // Core options are stored per system (computeSharedPreferenceKey(key, systemId)), so say so.
    val systemTitle = remember(request.game.systemId) {
        runCatching { context.getString(GameSystem.findById(request.game.systemId).titleResId) }.getOrNull()
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
        MenuSection(
            "core",
            visible = options.isNotEmpty(),
            title = systemTitle?.let { { Text(stringResource(R.string.core_settings_scope, it)) } },
        ) {
            options.forEach { option ->
                key(option.getKey()) {
                    val values = option.getEntriesValues()
                    val entries = option.getEntries(context)
                    val prefKey = CoreVariablesManager.computeSharedPreferenceKey(option.getKey(), request.game.systemId)
                    if (values.toSet() == CoreOptionsPreferenceHelper.BOOLEAN_SET) {
                        val pref = booleanPreferenceState(prefKey, option.getCurrentValue() == "enabled")
                        MenuToggle(
                            id = "core-${option.getKey()}",
                            title = { Text(option.getDisplayName(context)) },
                            value = pref.value,
                        ) { checked ->
                            val nextVal = if (checked) "enabled" else "disabled"
                            if (canApplyCoreOption(option.getKey(), nextVal) is HardcoreDecision.Blocked) {
                                val message =
                                    context.getString(
                                        R.string.ra_hardcore_blocked_core_option,
                                        option.getKey(),
                                        nextVal,
                                    )
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            } else {
                                pref.value = checked
                            }
                        }
                    } else {
                        // Default to the value the core is running with, not the first entry.
                        val pref = stringPreferenceState(prefKey, option.getCurrentValue())
                        MenuChoice(
                            id = "core-${option.getKey()}", title = { Text(option.getDisplayName(context)) },
                            options = values.zip(entries) { value, label -> MenuOption(value, label) }, value = pref.value,
                            // Shown only when the stored value isn't one of the listed entries.
                            subtitle = { Text(stringResource(R.string.core_option_current_value, pref.value)) },
                        ) { nextVal ->
                            if (canApplyCoreOption(option.getKey(), nextVal) is HardcoreDecision.Blocked) {
                                val message =
                                    context.getString(
                                        R.string.ra_hardcore_blocked_core_option,
                                        option.getKey(),
                                        nextVal,
                                    )
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            } else {
                                pref.value = nextVal
                            }
                        }
                    }
                }
            }
        }
    }
}
