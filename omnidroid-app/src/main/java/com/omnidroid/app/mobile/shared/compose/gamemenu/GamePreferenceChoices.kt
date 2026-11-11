package com.omnidroid.app.mobile.shared.compose.gamemenu

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.sidemenu.SideMenuPageScope
import com.omnidroid.app.mobile.shared.compose.sidemenu.MenuOption
import com.omnidroid.app.mobile.shared.compose.sidemenu.MenuChoice
import com.omnidroid.lib.savesync.GameCloudSyncOverride

@Composable
fun MenuDrawableIcon(@DrawableRes drawable: Int) = Icon(painterResource(drawable), contentDescription = null)

@Composable
fun SideMenuPageScope.FrameSpeedMenuChoice(value: Int, onSelected: (Int) -> Unit) {
    val labels = stringArrayResource(R.array.game_menu_fast_forward_speeds)
    val values = stringArrayResource(R.array.game_menu_fast_forward_speed_values).map { it.toInt() }
    val selected = value.takeIf { it in values } ?: values.first()
    MenuChoice(
        id = "frame-speed", titleRes = R.string.game_menu_fast_forward,
        options = values.zip(labels.toList()) { raw, label -> MenuOption(raw, label) }, value = selected,
        useSelectedValueAsSubtitle = false,
        subtitle = { Text(labels[values.indexOf(selected)] + " - " + stringResource(R.string.game_menu_fast_forward_note)) },
        icon = { MenuDrawableIcon(R.drawable.ic_menu_fast_forward) }, onSelected = onSelected,
    )
}

@Composable
fun SideMenuPageScope.CloudSaveMenuChoice(value: GameCloudSyncOverride, onSelected: (GameCloudSyncOverride) -> Unit) {
    val labels = listOf(
        stringResource(R.string.game_cloud_save_inherit),
        stringResource(R.string.game_cloud_save_always),
        stringResource(R.string.game_cloud_save_never),
    )
    MenuChoice(
        id = "cloud-override", titleRes = R.string.game_cloud_save,
        options = GameCloudSyncOverride.entries.zip(labels) { raw, label -> MenuOption(raw, label) }, value = value,
        icon = { Icon(Icons.Outlined.CloudSync, contentDescription = null) }, onSelected = onSelected,
    )
}
