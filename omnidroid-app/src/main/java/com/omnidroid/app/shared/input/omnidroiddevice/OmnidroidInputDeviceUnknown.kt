package com.omnidroid.app.shared.input.omnidroiddevice

import android.content.Context
import com.omnidroid.app.shared.input.InputKey
import com.omnidroid.app.shared.input.RetroKey
import com.omnidroid.app.shared.settings.GameShortcutType

object OmnidroidInputDeviceUnknown : OmnidroidInputDevice {
    override fun getDefaultBindings(): Map<InputKey, RetroKey> = emptyMap()

    override fun isSupported(): Boolean = false

    override fun isEnabledByDefault(appContext: Context): Boolean = false

    override fun getSupportedShortcuts(): List<GameShortcutType> = emptyList()

    override fun getCustomizableKeys(): List<RetroKey> = emptyList()
}
