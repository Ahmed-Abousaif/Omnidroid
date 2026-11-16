package com.omnidroid.app.mobile.feature.input

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.focusable
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
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.dialog.DialogActionRole
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialog
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogButton
import com.omnidroid.app.mobile.shared.compose.ui.AppTheme
import com.omnidroid.app.shared.input.BindingEdits.ConflictResolution
import com.omnidroid.app.shared.input.InputBindingUpdater
import com.omnidroid.app.shared.input.InputDeviceManager
import com.omnidroid.app.shared.input.InputKey
import com.omnidroid.app.shared.input.RetroKey
import com.omnidroid.lib.android.RetrogradeComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GamePadBindingActivity : RetrogradeComponentActivity() {
    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    private lateinit var inputBindingUpdater: InputBindingUpdater

    /** A pressed key that another button already uses, waiting for Swap / Replace / Cancel. */
    private data class Conflict(val key: InputKey, val other: RetroKey)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        inputBindingUpdater = InputBindingUpdater(inputDeviceManager, intent)

        setContent {
            AppTheme(syncSystemBars = false) {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val focusRequester = remember { FocusRequester() }
                var conflict by remember { mutableStateOf<Conflict?>(null) }
                var busy by remember { mutableStateOf(false) }

                fun finishWith(action: suspend () -> Unit) {
                    busy = true
                    scope.launch {
                        action()
                        finish()
                    }
                }

                fun onKey(event: KeyEvent): Boolean {
                    if (!inputBindingUpdater.consumes(event)) return false
                    val key = inputBindingUpdater.releasedKey(event) ?: return true
                    if (busy) return true
                    busy = true
                    scope.launch {
                        val other = inputBindingUpdater.conflictFor(key)
                        if (other == null) {
                            inputBindingUpdater.assign(key)
                            finish()
                        } else {
                            conflict = Conflict(key, other)
                            busy = false
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
                        title = { Text(text = inputBindingUpdater.getTitle(context)) },
                        text = { Text(text = inputBindingUpdater.getMessage(context)) },
                        onDismissRequest = { finish() },
                        confirmButton = {
                            OmnidroidDialogButton(
                                onClick = { finishWith { inputBindingUpdater.clear() } },
                                role = DialogActionRole.Alternative,
                            ) { Text(stringResource(R.string.gamepad_binding_clear)) }
                        },
                        dismissButton = {
                            OmnidroidDialogButton(onClick = { finish() }) { Text(stringResource(R.string.cancel)) }
                        },
                    )
                } else {
                    OmnidroidDialog(
                        title = { Text(stringResource(R.string.gamepad_binding_conflict_title, pending.key.displayName())) },
                        text = {
                            Text(
                                stringResource(
                                    R.string.gamepad_binding_conflict_message,
                                    pending.key.displayName(),
                                    pending.other.displayName(context),
                                ),
                            )
                        },
                        onDismissRequest = { finish() },
                        confirmButton = {
                            OmnidroidDialogButton(
                                onClick = { finishWith { inputBindingUpdater.assign(pending.key, ConflictResolution.REPLACE) } },
                                role = DialogActionRole.Alternative,
                            ) { Text(stringResource(R.string.gamepad_binding_conflict_replace)) }
                            OmnidroidDialogButton(
                                onClick = { finishWith { inputBindingUpdater.assign(pending.key, ConflictResolution.SWAP) } },
                            ) { Text(stringResource(R.string.gamepad_binding_conflict_swap)) }
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
