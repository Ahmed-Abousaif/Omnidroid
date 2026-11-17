package com.omnidroid.app.mobile.feature.settings.inputdevices

import android.content.Context
import android.content.Intent
import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.omnidroid.app.mobile.shared.compose.dialog.DialogActionRole
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialog
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.input.GamePadBindingActivity
import com.omnidroid.app.mobile.feature.input.GamePadShortcutBindingActivity
import com.omnidroid.app.shared.input.InputBindingUpdater
import com.omnidroid.app.shared.input.InputKey
import com.omnidroid.app.shared.input.ShortcutBindingUpdater
import com.omnidroid.app.shared.input.omnidroiddevice.getOmnidroidInputDevice
import com.omnidroid.app.shared.settings.GameShortcut
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsGroup
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsMenuLink
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsPage
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsSwitch
import com.omnidroid.app.utils.android.settings.SettingsHorizontalDivider
import com.omnidroid.app.utils.android.settings.booleanPreferenceState

@Composable
fun InputDevicesSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: InputDevicesSettingsViewModel,
) {
    val state =
        viewModel.uiState
            .collectAsState(InputDevicesSettingsViewModel.State())
            .value

    OmnidroidSettingsPage(modifier = modifier.fillMaxSize()) {
        if (state.devices.isNotEmpty()) {
            EnabledDeviceCategory(state)
            SettingsHorizontalDivider()
        }
        if (state.bindings.isEmpty()) {
            // Buttons and shortcuts are per controller, so there's nothing to edit until one connects.
            OmnidroidSettingsGroup {
                OmnidroidSettingsMenuLink(
                    titleRes = R.string.settings_gamepad_none_title,
                    subtitleRes = R.string.settings_gamepad_none_description,
                    onClick = {},
                )
            }
            SettingsHorizontalDivider()
        }
        state.bindings.forEach { (device, bindings) ->
            DeviceBindingCategory(device, bindings, onReset = { viewModel.resetBindings(device) })
            SettingsHorizontalDivider()
        }
        GeneralOptionsCategory(viewModel)
    }
}

@Composable
private fun DeviceBindingCategory(
    device: InputDevice,
    bindings: InputDevicesSettingsViewModel.BindingsView,
    onReset: () -> Unit,
) {
    var confirmReset by remember { mutableStateOf(false) }
    if (confirmReset) {
        ConfirmResetDialog(
            message = stringResource(R.string.settings_gamepad_reset_device_message, device.name),
            onDismiss = { confirmReset = false },
        ) {
            confirmReset = false
            onReset()
        }
    }
    val context = LocalContext.current
    val customizableKeys = device.getOmnidroidInputDevice().getCustomizableKeys()

    OmnidroidSettingsGroup(title = { Text(text = device.name) }) {
        customizableKeys.forEach { retroKey ->
            val inputKey = bindings.keys[retroKey] ?: InputKey(KeyEvent.KEYCODE_UNKNOWN)

            OmnidroidSettingsMenuLink(
                title = { Text(text = retroKey.displayName(LocalContext.current)) },
                subtitle = { Text(text = inputKey.displayName()) },
                onClick = {
                    val intent =
                        Intent(context, GamePadBindingActivity::class.java).apply {
                            putExtra(InputBindingUpdater.REQUEST_DEVICE, device)
                            putExtra(InputBindingUpdater.REQUEST_RETRO_KEY, retroKey.keyCode)
                        }
                    context.startActivity(intent)
                },
            )
        }

        bindings.shortcuts.forEach {
            DeviceShortcutBinding(context, device, it)
        }

        OmnidroidSettingsMenuLink(
            titleRes = R.string.settings_gamepad_title_reset_device,
            onClick = { confirmReset = true },
        )
    }
}

@Composable
private fun DeviceShortcutBinding(
    context: Context,
    device: InputDevice,
    shortcut: GameShortcut,
) {
    OmnidroidSettingsMenuLink(
        title = { Text(text = shortcut.type.displayName()) },
        subtitle = {
            Text(text = if (shortcut.isBound) shortcut.name else stringResource(R.string.shortcut_not_set))
        },
        onClick = {
            val intent =
                Intent(context, GamePadShortcutBindingActivity::class.java).apply {
                    putExtra(ShortcutBindingUpdater.REQUEST_DEVICE, device)
                    putExtra(ShortcutBindingUpdater.REQUEST_SHORTCUT_TYPE, shortcut.type.name)
                }
            context.startActivity(intent)
        },
    )
}

@Composable
private fun EnabledDeviceCategory(state: InputDevicesSettingsViewModel.State) {
    OmnidroidSettingsGroup(titleRes = R.string.settings_gamepad_category_enabled) {
        state.devices.forEach { device ->
            OmnidroidSettingsSwitch(
                state = booleanPreferenceState(key = device.key, default = device.enabledByDefault),
                title = { Text(text = device.name) },
            )
        }
    }
}

@Composable
private fun GeneralOptionsCategory(viewModel: InputDevicesSettingsViewModel) {
    var confirmReset by remember { mutableStateOf(false) }
    if (confirmReset) {
        ConfirmResetDialog(
            message = stringResource(R.string.settings_gamepad_reset_all_message),
            onDismiss = { confirmReset = false },
        ) {
            confirmReset = false
            viewModel.resetAllBindings()
        }
    }
    OmnidroidSettingsGroup(titleRes = R.string.settings_gamepad_category_general) {
        OmnidroidSettingsMenuLink(
            titleRes = R.string.settings_gamepad_title_reset_bindings,
            subtitleRes = R.string.settings_gamepad_description_reset_bindings,
            onClick = { confirmReset = true },
        )
    }
}

@Composable
private fun ConfirmResetDialog(
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    OmnidroidDialog(
        destructive = true,
        title = { Text(stringResource(R.string.settings_gamepad_reset_confirm_title)) },
        text = { Text(message) },
        onDismissRequest = onDismiss,
        confirmButton = {
            OmnidroidDialogButton(onClick = onConfirm, role = DialogActionRole.Destructive) {
                Text(stringResource(R.string.settings_gamepad_reset_confirm_button))
            }
        },
        dismissButton = {
            OmnidroidDialogButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
