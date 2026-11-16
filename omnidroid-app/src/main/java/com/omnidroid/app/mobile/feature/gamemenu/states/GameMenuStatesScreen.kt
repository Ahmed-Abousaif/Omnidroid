package com.omnidroid.app.mobile.feature.gamemenu.states

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.gamemenu.ConfirmProgressLossDialog
import com.omnidroid.app.mobile.shared.compose.gamemenu.MenuDrawableIcon
import com.omnidroid.app.mobile.shared.compose.sidemenu.MenuAction
import com.omnidroid.app.mobile.shared.compose.sidemenu.MenuSection
import com.omnidroid.app.mobile.shared.compose.sidemenu.SideMenuPage

/** What a click on a slot does, which decides when to ask before acting. */
enum class StatesMenuMode {
    /** Saving: confirm only when the slot already holds a save. */
    SAVE,

    /** Loading: always confirm, since it replaces the current progress. */
    LOAD,
}

/** A slot click waiting for confirmation; [slot] is null for the autosave. */
private data class PendingSlot(val slot: Int?, val title: String, val description: String)

@Composable
fun GameMenuStatesScreen(
    viewModel: GameMenuStatesViewModel,
    mode: StatesMenuMode,
    onStateClicked: (Int) -> Unit,
    onAutoSaveClicked: () -> Unit = {},
    onNewStateClicked: () -> Unit = {},
) {
    val state = viewModel.uiStates.collectAsState()
    var pending by remember { mutableStateOf<PendingSlot?>(null) }
    val autoSaveTitle = stringResource(R.string.game_menu_autosave)

    pending?.let { target ->
        val isLoad = mode == StatesMenuMode.LOAD
        ConfirmProgressLossDialog(
            title =
                stringResource(
                    if (isLoad) R.string.game_menu_load_confirm_title else R.string.game_menu_save_overwrite_title,
                    target.title,
                ),
            confirmLabel = stringResource(if (isLoad) R.string.game_menu_load else R.string.game_menu_overwrite),
            message =
                if (isLoad) {
                    stringResource(R.string.game_menu_confirm_progress_loss)
                } else {
                    stringResource(R.string.game_menu_save_overwrite_message, target.description)
                },
            onDismiss = { pending = null },
        ) {
            pending = null
            target.slot?.let(onStateClicked) ?: onAutoSaveClicked()
        }
    }

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
                ) { pending = PendingSlot(null, autoSaveTitle, autoSave.description) }
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
                        val preview = entry.preview
                        if (preview != null) {
                            Image(
                                modifier = Modifier.size(48.dp),
                                bitmap = preview.asImageBitmap(),
                                contentScale = ContentScale.Crop,
                                contentDescription = null,
                            )
                        } else {
                            // Keeps empty slots aligned with filled ones.
                            MenuDrawableIcon(R.drawable.ic_menu_save)
                        }
                    },
                ) {
                    val needsConfirm = mode == StatesMenuMode.LOAD || entry.exists
                    if (needsConfirm) {
                        pending = PendingSlot(entry.slot, entry.title, entry.description)
                    } else {
                        onStateClicked(entry.slot)
                    }
                }
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
