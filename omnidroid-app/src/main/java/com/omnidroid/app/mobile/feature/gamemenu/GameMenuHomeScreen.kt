package com.omnidroid.app.mobile.feature.gamemenu

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.alorma.compose.settings.storage.memory.rememberMemoryBooleanSettingState
import com.alorma.compose.settings.storage.memory.rememberMemoryIntSettingState
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.gamemenu.tilt.TiltConfigurationMenuEntry
import com.omnidroid.app.shared.GameMenuContract
import com.omnidroid.app.utils.android.settings.OmnidroidCardSettingsGroup
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsList
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsMenuLink
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsPage
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsSwitch
import com.omnidroid.lib.savesync.GameCloudSyncOverride
import com.omnidroid.lib.savesync.GameCloudSyncPreferences

@Composable
fun GameMenuHomeScreen(
    navController: NavController,
    gameMenuRequest: GameMenuActivity.GameMenuRequest,
    onResult: (Intent.() -> Unit) -> Unit,
    saveSyncSupported: Boolean = false,
) {
    OmnidroidSettingsPage {
        OmnidroidCardSettingsGroup {
            if (gameMenuRequest.coreConfig.statesSupported) {
                OmnidroidSettingsMenuLink(
                    title = { Text(text = stringResource(id = R.string.game_menu_save)) },
                    icon = {
                        Icon(
                            painterResource(R.drawable.ic_menu_save),
                            contentDescription = stringResource(id = R.string.game_menu_save),
                        )
                    },
                    onClick = { navController.navigateToRoute(GameMenuRoute.SAVE) },
                )

                OmnidroidSettingsMenuLink(
                    title = { Text(text = stringResource(id = R.string.game_menu_load)) },
                    icon = {
                        Icon(
                            painterResource(R.drawable.ic_menu_load),
                            contentDescription = stringResource(id = R.string.game_menu_load),
                        )
                    },
                    onClick = { navController.navigateToRoute(GameMenuRoute.LOAD) },
                )
            }

            OmnidroidSettingsMenuLink(
                title = { Text(text = stringResource(id = R.string.game_menu_restart)) },
                icon = {
                    Icon(
                        painterResource(R.drawable.ic_menu_restart),
                        contentDescription = stringResource(id = R.string.game_menu_restart),
                    )
                },
                onClick = {
                    onResult { putExtra(GameMenuContract.RESULT_RESET, true) }
                },
            )

            OmnidroidSettingsMenuLink(
                title = { Text(text = stringResource(id = R.string.game_menu_quit)) },
                icon = {
                    Icon(
                        painterResource(R.drawable.ic_menu_quit),
                        contentDescription = stringResource(id = R.string.game_menu_quit),
                    )
                },
                onClick = {
                    onResult { putExtra(GameMenuContract.RESULT_QUIT, true) }
                },
            )
        }

        OmnidroidCardSettingsGroup {
            OmnidroidSettingsSwitch(
                title = { Text(text = stringResource(id = R.string.game_menu_mute_audio)) },
                icon = {
                    Icon(
                        painterResource(R.drawable.ic_menu_mute),
                        contentDescription = stringResource(id = R.string.game_menu_mute_audio),
                    )
                },
                state = rememberMemoryBooleanSettingState(!gameMenuRequest.audioEnabled),
                onCheckedChange = {
                    onResult { putExtra(GameMenuContract.RESULT_ENABLE_AUDIO, !it) }
                },
            )

            if (gameMenuRequest.fastForwardSupported) {
                val speedLabels = stringArrayResource(R.array.game_menu_fast_forward_speeds).toList()
                val speedValues =
                    stringArrayResource(R.array.game_menu_fast_forward_speed_values)
                        .map { it.toInt() }

                val selectedIndex =
                    speedValues.indexOf(gameMenuRequest.frameSpeed).let { if (it >= 0) it else 0 }

                OmnidroidSettingsList(
                    title = { Text(text = stringResource(id = R.string.game_menu_fast_forward)) },
                    items = speedLabels,
                    useSelectedValueAsSubtitle = false,
                    subtitle = {
                        Text(
                            text =
                                speedLabels[selectedIndex] +
                                    " - " +
                                    stringResource(R.string.game_menu_fast_forward_note),
                        )
                    },
                    icon = {
                        Icon(
                            painterResource(R.drawable.ic_menu_fast_forward),
                            contentDescription = stringResource(id = R.string.game_menu_fast_forward),
                        )
                    },
                    state = rememberMemoryIntSettingState(selectedIndex),
                    onItemSelected = { index, _ ->
                        val speed = speedValues[index]
                        onResult {
                            putExtra(GameMenuContract.RESULT_SET_FRAME_SPEED, speed)
                            putExtra(GameMenuContract.RESULT_ENABLE_FAST_FORWARD, speed > 1)
                        }
                    },
                )
            }

            if (gameMenuRequest.numDisks > 1) {
                OmnidroidSettingsList(
                    title = { Text(text = stringResource(id = R.string.game_menu_change_disk_button)) },
                    items =
                        (1..gameMenuRequest.numDisks).map {
                            stringResource(R.string.game_menu_change_disk_disk, it)
                        },
                    useSelectedValueAsSubtitle = false,
                    icon = {
                        Icon(
                            painterResource(R.drawable.ic_menu_disk),
                            contentDescription = stringResource(id = R.string.game_menu_change_disk_button),
                        )
                    },
                    state = rememberMemoryIntSettingState(gameMenuRequest.currentDisk),
                    onItemSelected = { index, _ ->
                        onResult { putExtra(GameMenuContract.RESULT_CHANGE_DISK, index) }
                    },
                )
            }
        }

        OmnidroidCardSettingsGroup {
            OmnidroidSettingsMenuLink(
                title = { Text(text = stringResource(id = R.string.game_menu_edit_touch_controls)) },
                icon = {
                    Icon(
                        painterResource(R.drawable.ic_menu_controls),
                        contentDescription = stringResource(id = R.string.game_menu_edit_touch_controls),
                    )
                },
                onClick = {
                    onResult { putExtra(GameMenuContract.RESULT_EDIT_TOUCH_CONTROLS, true) }
                },
            )

            if (saveSyncSupported) {
                val context = LocalContext.current
                val prefs = remember { GameCloudSyncPreferences(context) }
                val current = prefs.getOverride(gameMenuRequest.game.id)
                val items =
                    listOf(
                        stringResource(R.string.game_cloud_save_inherit),
                        stringResource(R.string.game_cloud_save_always),
                        stringResource(R.string.game_cloud_save_never),
                    )
                val values = GameCloudSyncOverride.values()
                OmnidroidSettingsList(
                    title = { Text(text = stringResource(id = R.string.game_cloud_save)) },
                    items = items,
                    useSelectedValueAsSubtitle = true,
                    state = rememberMemoryIntSettingState(values.indexOf(current).coerceAtLeast(0)),
                    onItemSelected = { index, _ ->
                        prefs.setOverride(gameMenuRequest.game.id, values[index])
                    },
                )
            }

            if (gameMenuRequest.advancedCoreOptions.isNotEmpty() || gameMenuRequest.coreOptions.isNotEmpty()) {
                OmnidroidSettingsMenuLink(
                    title = { Text(text = stringResource(id = R.string.game_menu_settings)) },
                    icon = {
                        Icon(
                            painterResource(R.drawable.ic_menu_settings),
                            contentDescription = stringResource(id = R.string.game_menu_settings),
                        )
                    },
                    onClick = { navController.navigateToRoute(GameMenuRoute.OPTIONS) },
                )
            }

            if (gameMenuRequest.allTiltConfigurations.isNotEmpty()) {
                val tiltConfigurationEntries =
                    gameMenuRequest.allTiltConfigurations
                        .map { TiltConfigurationMenuEntry.fromTiltConfiguration(it) }

                val selectedIndex =
                    gameMenuRequest.allTiltConfigurations
                        .indexOf(gameMenuRequest.currentTiltConfiguration)

                OmnidroidSettingsList(
                    title = { Text(text = stringResource(id = R.string.game_menu_tilt_sensor)) },
                    items = tiltConfigurationEntries.map { stringResource(it.descriptionId) },
                    useSelectedValueAsSubtitle = false,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = stringResource(id = R.string.game_menu_tilt_sensor),
                        )
                    },
                    state = rememberMemoryIntSettingState(selectedIndex),
                    onItemSelected = { index, _ ->
                        onResult {
                            putExtra(
                                GameMenuContract.RESULT_CHANGE_TILT_CONFIG,
                                tiltConfigurationEntries[index].configuration,
                            )
                        }
                    },
                )
            }
        }
    }
}
