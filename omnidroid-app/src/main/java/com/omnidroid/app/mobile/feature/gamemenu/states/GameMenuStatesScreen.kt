package com.omnidroid.app.mobile.feature.gamemenu.states

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.sidemenu.MenuAction
import com.omnidroid.app.mobile.shared.compose.sidemenu.MenuSection
import com.omnidroid.app.mobile.shared.compose.sidemenu.SideMenuPage

@Composable
fun GameMenuStatesScreen(
    viewModel: GameMenuStatesViewModel,
    onStateClicked: (Int) -> Unit,
    onAutoSaveClicked: () -> Unit = {},
    onNewStateClicked: () -> Unit = {},
) {
    val state = viewModel.uiStates.collectAsState()
    SideMenuPage("game-slots") {
        MenuSection("error", visible = state.value.loadFailed) {
            MenuAction(
                "retry",
                titleRes = R.string.game_menu_states_retry,
                subtitle = { Text(stringResource(R.string.game_menu_states_load_failed)) },
                onClick = viewModel::retry,
            )
        }
        state.value.autoSave?.let { autoSave ->
            MenuSection("autosave") {
                MenuAction(
                    "autosave",
                    titleRes = R.string.game_menu_autosave,
                    subtitle = { Text(autoSave.description) },
                    enabled = autoSave.enabled,
                    onClick = onAutoSaveClicked,
                )
            }
        }
        MenuSection("slots", visible = state.value.entries.isNotEmpty()) {
            state.value.entries.forEach { entry ->
                MenuAction(
                    id = "slot-${entry.slot}",
                    title = { Text(entry.title) },
                    subtitle = { Text(entry.description) },
                    enabled = entry.enabled,
                    icon = {
                        entry.preview?.let {
                            Image(
                                modifier = Modifier.size(48.dp),
                                bitmap = it.asImageBitmap(),
                                contentScale = ContentScale.Crop,
                                contentDescription = null,
                            )
                        }
                    },
                ) { onStateClicked(entry.slot) }
            }
        }
        MenuSection("new-slot", visible = state.value.canAddSlot) {
            MenuAction(
                "new-slot",
                titleRes = R.string.game_menu_save_new,
                subtitle = { Text(stringResource(R.string.game_menu_save_new_description)) },
                onClick = onNewStateClicked,
            )
        }
    }
}
