package com.omnidroid.app.mobile.shared.compose.dialog

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.omnidroid.app.mobile.shared.compose.ui.AppTheme

data class OmnidroidDialogAction(
    val label: String,
    val role: DialogActionRole = DialogActionRole.Confirm,
    val dismissOnClick: Boolean = true,
    val onClick: () -> Unit = {},
)

/** An imperative handle for game/native callbacks; it owns no visual implementation. */
class OmnidroidDialogHandle internal constructor(private val close: () -> Unit) {
    @Volatile internal var isDismissed = false
        private set

    @Synchronized
    fun dismiss() {
        if (isDismissed) return
        isDismissed = true
        close()
    }
}

/**
 * Mount the same Compose dialog in an existing Activity. The tiny host inherits its
 * lifecycle from the decor view and is removed/disposed when the dialog closes.
 */
fun showOmnidroidDialog(
    activity: Activity,
    title: String? = null,
    message: String? = null,
    actions: List<OmnidroidDialogAction>,
    cancelable: Boolean = true,
    forceDark: Boolean = false,
    width: DialogWidth = DialogWidth.Content,
    onCancel: () -> Unit = {},
    onDismiss: () -> Unit = {},
    content: (@Composable () -> Unit)? = null,
): OmnidroidDialogHandle? {
    if (activity.isFinishing || activity.isDestroyed) return null
    val parent = activity.window.decorView as ViewGroup
    val host = ComposeView(activity)
    host.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    val handle = OmnidroidDialogHandle {
        // Native/game callbacks may dismiss outside the UI thread.
        host.post {
            host.disposeComposition()
            parent.removeView(host)
            onDismiss()
        }
    }
    host.setContent {
        AppTheme(followUserPreference = !forceDark, syncSystemBars = false) {
            OmnidroidDialog(
                width = width,
                onDismissRequest = { onCancel(); handle.dismiss() },
                title = title?.let { { Text(it) } },
                text = {
                    message?.let { Text(it) }
                    content?.invoke()
                },
                dismissOnBackPress = cancelable,
                dismissOnClickOutside = cancelable,
                destructive = actions.any { it.role == DialogActionRole.Destructive },
                confirmButton = {
                    actions.forEach { action ->
                        OmnidroidDialogButton(
                            role = action.role,
                            onClick = {
                                if (!handle.isDismissed) {
                                    action.onClick()
                                    if (action.dismissOnClick) handle.dismiss()
                                }
                            },
                        ) { Text(action.label) }
                    }
                },
            )
        }
    }
    parent.addView(host, ViewGroup.LayoutParams(0, 0))
    return handle
}

internal fun Context.dialogActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
