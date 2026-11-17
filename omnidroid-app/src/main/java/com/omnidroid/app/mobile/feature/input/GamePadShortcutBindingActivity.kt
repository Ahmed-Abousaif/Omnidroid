package com.omnidroid.app.mobile.feature.input

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.dialog.DialogActionRole
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialog
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogButton
import com.omnidroid.app.mobile.shared.compose.ui.AppTheme
import com.omnidroid.app.shared.input.InputDeviceManager
import com.omnidroid.app.shared.input.InputKey
import com.omnidroid.app.shared.input.ShortcutBindingUpdater
import com.omnidroid.app.shared.settings.GameShortcutType
import com.omnidroid.lib.android.RetrogradeComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GamePadShortcutBindingActivity : RetrogradeComponentActivity() {
    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    private lateinit var shortcutBindingUpdater: ShortcutBindingUpdater

    /** Keys that another shortcut already uses, waiting for Replace / Cancel. */
    private data class Conflict(val keys: List<InputKey>, val other: GameShortcutType)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        shortcutBindingUpdater = ShortcutBindingUpdater(inputDeviceManager, intent)

        setContent {
            AppTheme(syncSystemBars = false) {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val focusRequester = remember { FocusRequester() }
                var captured by remember { mutableStateOf<List<InputKey>>(emptyList()) }
                var conflict by remember { mutableStateOf<Conflict?>(null) }
                var busy by remember { mutableStateOf(false) }
                var comboWindow by remember { mutableStateOf<Job?>(null) }

                fun finishWith(action: suspend () -> Unit) {
                    busy = true
                    scope.launch {
                        action()
                        finish()
                    }
                }

                fun commit(keys: List<InputKey>) {
                    busy = true
                    scope.launch {
                        val other = shortcutBindingUpdater.conflictFor(keys)
                        if (other == null) {
                            shortcutBindingUpdater.assign(keys)
                            finish()
                        } else {
                            conflict = Conflict(keys, other)
                            busy = false
                        }
                    }
                }

                fun onKey(event: KeyEvent): Boolean {
                    if (!shortcutBindingUpdater.consumes(event)) return false
                    val key = shortcutBindingUpdater.releasedKey(event) ?: return true
                    if (busy || conflict != null) return true
                    val first = captured.firstOrNull()
                    when {
                        first == null -> {
                            // First key: wait briefly for a second one, else save a single-key shortcut.
                            captured = listOf(key)
                            comboWindow =
                                scope.launch {
                                    delay(ShortcutBindingUpdater.COMBO_WINDOW_MS)
                                    commit(listOf(key))
                                }
                        }
                        first != key -> {
                            comboWindow?.cancel()
                            captured = listOf(first, key)
                            commit(captured)
                        }
                    }
                    return true
                }

                val pending = conflict
                if (pending == null) {
                    OmnidroidDialog(
                        handleControllerBack = false,
                        // Every key from the controller is a candidate binding, so focus stays on
                        // the dialog itself; the buttons below are for touch.
                        modifier =
                            Modifier
                                .focusRequester(focusRequester)
                                .focusable()
                                .onKeyEvent { onKey(it.nativeKeyEvent) }
                                .onGloballyPositioned { focusRequester.requestFocus() },
                        title = { Text(text = shortcutBindingUpdater.getTitle(context)) },
                        text = {
                            Column {
                                Text(text = shortcutBindingUpdater.getMessage(context))
                                if (captured.isNotEmpty()) {
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = captured.joinToString(" + ") { it.displayName() },
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                            }
                        },
                        onDismissRequest = { finish() },
                        confirmButton = {
                            OmnidroidDialogButton(
                                onClick = {
                                    comboWindow?.cancel()
                                    finishWith { shortcutBindingUpdater.clear() }
                                },
                                role = DialogActionRole.Alternative,
                            ) { Text(stringResource(R.string.gamepad_binding_clear)) }
                        },
                        dismissButton = {
                            OmnidroidDialogButton(onClick = { finish() }) { Text(stringResource(R.string.cancel)) }
                        },
                    )
                } else {
                    val keysLabel = pending.keys.joinToString(" + ") { it.displayName() }
                    OmnidroidDialog(
                        title = { Text(stringResource(R.string.gamepad_binding_conflict_title, keysLabel)) },
                        text = {
                            Text(
                                stringResource(
                                    R.string.shortcut_binding_conflict_message,
                                    keysLabel,
                                    pending.other.displayName(),
                                ),
                            )
                        },
                        onDismissRequest = { finish() },
                        confirmButton = {
                            OmnidroidDialogButton(
                                onClick = { finishWith { shortcutBindingUpdater.assign(pending.keys, replacing = pending.other) } },
                            ) { Text(stringResource(R.string.gamepad_binding_conflict_replace)) }
                        },
                        dismissButton = {
                            OmnidroidDialogButton(onClick = { finish() }) { Text(stringResource(R.string.cancel)) }
                        },
                    )
                }
            }
        }
    }
}
