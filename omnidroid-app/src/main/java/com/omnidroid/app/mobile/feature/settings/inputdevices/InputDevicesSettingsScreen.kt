package com.omnidroid.app.mobile.feature.settings.inputdevices

import android.content.Context
import android.content.Intent
import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
        state.bindings.forEach { (device, bindings) ->
            DeviceBindingCategory(device, bindings)
            SettingsHorizontalDivider()
        }
        GeneralOptionsCategory(viewModel)
    }
}

@Composable
private fun DeviceBindingCategory(
    device: InputDevice,
    bindings: InputDevicesSettingsViewModel.BindingsView,
) {
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
        subtitle = { Text(text = shortcut.name) },
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
    OmnidroidSettingsGroup(titleRes = R.string.settings_gamepad_category_general) {
        OmnidroidSettingsMenuLink(
            titleRes = R.string.settings_gamepad_title_reset_bindings,
            onClick = { viewModel.resetAllBindings() },
        )
    }
}
