package com.omnidroid.app.utils.android

import android.app.Activity
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogAction
import com.omnidroid.app.mobile.shared.compose.dialog.showOmnidroidDialog

// TODO COMPOSE... How do they look in the post compose world?
fun Activity.displayErrorDialog(
    messageId: Int,
    actionLabelId: Int,
    action: () -> Unit,
) {
    displayErrorDialog(resources.getString(messageId), resources.getString(actionLabelId), action)
}

fun Activity.displayErrorDialog(
    message: String,
    actionLabel: String,
    action: () -> Unit,
) {
    showOmnidroidDialog(
        activity = this,
        message = message,
        cancelable = false,
        actions = listOf(OmnidroidDialogAction(actionLabel, onClick = action)),
    )
}
