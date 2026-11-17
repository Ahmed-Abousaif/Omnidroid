package com.omnidroid.app.mobile.feature.gamemenu

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.dialog.DialogActionRole
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialog
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogButton
import com.omnidroid.app.utils.android.settings.booleanPreferenceState

/** Settings → Saves → "Confirm before losing progress" (on by default). */
@Composable
fun confirmGameMenuActionsEnabled(): Boolean =
    booleanPreferenceState(R.string.pref_key_confirm_game_menu_actions, true).value

/**
 * Confirmation for in-game menu actions that throw away progress (restart, load, overwrite a
 * save). Destructive, so controller focus starts on Cancel.
 */
@Composable
fun ConfirmProgressLossDialog(
    title: String,
    confirmLabel: String,
    message: String = stringResource(R.string.game_menu_confirm_progress_loss),
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    OmnidroidDialog(
        destructive = true,
        title = { Text(title) },
        text = { Text(message) },
        onDismissRequest = onDismiss,
        confirmButton = {
            OmnidroidDialogButton(onClick = onConfirm, role = DialogActionRole.Destructive) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            OmnidroidDialogButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
