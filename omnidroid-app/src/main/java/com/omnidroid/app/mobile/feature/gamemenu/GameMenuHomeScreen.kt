package com.omnidroid.app.mobile.feature.gamemenu

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.gamemenu.tilt.TiltConfigurationMenuEntry
import com.omnidroid.app.mobile.shared.compose.gamemenu.MenuDrawableIcon
import com.omnidroid.app.mobile.shared.compose.gamemenu.FrameSpeedMenuChoice
import com.omnidroid.app.mobile.shared.compose.gamemenu.CloudSaveMenuChoice
import com.omnidroid.app.mobile.shared.compose.sidemenu.MenuOption
import com.omnidroid.app.mobile.shared.compose.sidemenu.SideMenuPage
import com.omnidroid.app.mobile.shared.compose.sidemenu.MenuSection
import com.omnidroid.app.mobile.shared.compose.sidemenu.MenuAction
import com.omnidroid.app.mobile.shared.compose.sidemenu.MenuChoice
import com.omnidroid.app.mobile.shared.compose.sidemenu.MenuToggle
import com.omnidroid.lib.savesync.GameCloudSyncPreferences

@Composable
fun GameMenuHomeScreen(
    navController: NavController,
    gameMenuRequest: GameMenuRequest,
    onCommand: (InGameMenuCommand) -> Unit,
    saveSyncSupported: Boolean = false,
) {
    val request = gameMenuRequest
    var audioEnabled by remember(request.audioEnabled) { mutableStateOf(request.audioEnabled) }
    val hasSettings = request.advancedCoreOptions.isNotEmpty() || request.coreOptions.isNotEmpty() ||
        request.coreConfig.controllerConfigs.values.any { it.size >= 2 }
    val hasControls = request.editTouchControlsSupported || saveSyncSupported || hasSettings || request.allTiltConfigurations.isNotEmpty()
    SideMenuPage("game-home") {
        MenuSection("commands") {
            if (request.coreConfig.statesSupported) {
                MenuAction("save", titleRes = R.string.game_menu_save, icon = { MenuDrawableIcon(R.drawable.ic_menu_save) }) {
                    navController.navigateToRoute(GameMenuRoute.SAVE)
                }
                MenuAction("load", titleRes = R.string.game_menu_load, icon = { MenuDrawableIcon(R.drawable.ic_menu_load) }) {
                    navController.navigateToRoute(GameMenuRoute.LOAD)
                }
            }
            MenuAction("restart", titleRes = R.string.game_menu_restart, icon = { MenuDrawableIcon(R.drawable.ic_menu_restart) }) {
                onCommand(InGameMenuCommand.Restart)
            }
            MenuAction("quit", titleRes = R.string.game_menu_quit, icon = { MenuDrawableIcon(R.drawable.ic_menu_quit) }) {
                onCommand(InGameMenuCommand.Quit)
            }
        }
        MenuSection("playback") {
            MenuToggle("mute", titleRes = R.string.game_menu_mute_audio, value = !audioEnabled,
                icon = { MenuDrawableIcon(R.drawable.ic_menu_mute) },
            ) { audioEnabled = !it; onCommand(InGameMenuCommand.Audio(!it)) }
            if (request.fastForwardSupported) {
                FrameSpeedMenuChoice(request.frameSpeed) { onCommand(InGameMenuCommand.Speed(it)) }
            }
            if (request.numDisks > 1) {
                MenuChoice(
                    id = "disc", titleRes = R.string.game_menu_change_disk_button,
                    options = (0 until request.numDisks).map { MenuOption(it, stringResource(R.string.game_menu_change_disk_disk, it + 1)) },
                    value = request.currentDisk, useSelectedValueAsSubtitle = false,
                    icon = { MenuDrawableIcon(R.drawable.ic_menu_disk) },
                ) { onCommand(InGameMenuCommand.Disc(it)) }
            }
        }
        MenuSection("controls", visible = hasControls) {
            if (request.editTouchControlsSupported) {
                MenuAction("touch-controls", titleRes = R.string.game_menu_edit_touch_controls,
                    icon = { MenuDrawableIcon(R.drawable.ic_menu_controls) },
                ) { onCommand(InGameMenuCommand.EditTouchControls) }
            }
            if (saveSyncSupported) {
                val context = LocalContext.current
                val prefs = remember(context) { GameCloudSyncPreferences(context) }
                val changes = remember(prefs, request.game.id) { prefs.observeOverride(request.game.id) }
                val override by changes.collectAsState(prefs.getOverride(request.game.id))
                CloudSaveMenuChoice(override) { prefs.setOverride(request.game.id, it) }
            }
            if (hasSettings) {
                MenuAction("options", titleRes = R.string.game_menu_settings, icon = { MenuDrawableIcon(R.drawable.ic_menu_settings) }) {
                    navController.navigateToRoute(GameMenuRoute.OPTIONS)
                }
            }
            if (request.allTiltConfigurations.isNotEmpty()) {
                val entries = request.allTiltConfigurations.map { TiltConfigurationMenuEntry.fromTiltConfiguration(it) }
                MenuChoice(
                    id = "tilt", titleRes = R.string.game_menu_tilt_sensor,
                    options = entries.map { MenuOption(it.configuration, stringResource(it.descriptionId)) },
                    value = request.currentTiltConfiguration, useSelectedValueAsSubtitle = false,
                    icon = { Icon(Icons.Default.Sensors, contentDescription = null) },
                ) { onCommand(InGameMenuCommand.Tilt(it)) }
            }
        }
    }
}
