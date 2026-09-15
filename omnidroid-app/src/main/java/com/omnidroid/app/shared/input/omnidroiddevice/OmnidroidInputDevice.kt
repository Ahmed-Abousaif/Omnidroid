package com.omnidroid.app.shared.input.omnidroiddevice

import android.content.Context
import android.view.InputDevice
import com.omnidroid.app.shared.input.InputKey
import com.omnidroid.app.shared.input.RetroKey
import com.omnidroid.app.shared.settings.GameShortcutType

interface OmnidroidInputDevice {
    fun getCustomizableKeys(): List<RetroKey>

    fun getDefaultBindings(): Map<InputKey, RetroKey>

    fun isSupported(): Boolean

    fun isEnabledByDefault(appContext: Context): Boolean

    fun getSupportedShortcuts(): List<GameShortcutType>
}

fun InputDevice?.getOmnidroidInputDevice(): OmnidroidInputDevice {
    return when {
        this == null -> OmnidroidInputDeviceUnknown
        (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD -> OmnidroidInputDeviceGamePad(this)
        (sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD -> OmnidroidInputDeviceKeyboard(this)
        else -> OmnidroidInputDeviceUnknown
    }
}
