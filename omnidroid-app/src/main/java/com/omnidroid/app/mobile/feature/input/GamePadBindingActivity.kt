package com.omnidroid.app.mobile.feature.input

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.focusable
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialog
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogButton
import com.omnidroid.app.mobile.shared.compose.ui.AppTheme
import com.omnidroid.app.shared.input.InputBindingUpdater
import com.omnidroid.app.shared.input.InputDeviceManager
import com.omnidroid.lib.android.RetrogradeComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber

@AndroidEntryPoint
class GamePadBindingActivity : RetrogradeComponentActivity() {
    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    private lateinit var inputBindingUpdater: InputBindingUpdater

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        inputBindingUpdater = InputBindingUpdater(inputDeviceManager, intent)

        setContent {
            AppTheme(followUserPreference = false) {
                val focusRequester = remember { FocusRequester() }

                OmnidroidDialog(
                    handleControllerBack = false,
                    modifier =
                        Modifier
                            .focusRequester(focusRequester)
                            .focusable()
                            .onKeyEvent { handleKeyEvent(it.nativeKeyEvent) }
                            .onGloballyPositioned { focusRequester.requestFocus() },
                    title = { Text(text = inputBindingUpdater.getTitle(applicationContext)) },
                    text = { Text(text = inputBindingUpdater.getMessage(applicationContext)) },
                    onDismissRequest = { finish() },
                    dismissButton = {
                        OmnidroidDialogButton(onClick = { finish() }) { Text(stringResource(android.R.string.cancel)) }
                    },
                )
            }
        }
    }

    private fun handleKeyEvent(event: KeyEvent): Boolean {
        Timber.i("Received key event: $event")
        val result = inputBindingUpdater.handleKeyEvent(event)

        if (event.action == KeyEvent.ACTION_UP && result) {
            finish()
        }

        return result
    }
}
