package com.omnidroid.app.shared.input

import android.content.Context
import android.content.Intent
import android.view.InputDevice
import android.view.KeyEvent
import com.omnidroid.R
import com.omnidroid.app.shared.settings.GameShortcutType

class ShortcutBindingUpdater(private val inputDeviceManager: InputDeviceManager, intent: Intent) {
    val extras = parseExtras(intent)

    fun getTitle(context: Context): String {
        return context.getString(R.string.shortcut_binding_update_title, extras.shortcutType.displayName())
    }

    fun getMessage(context: Context): String {
        return context.getString(R.string.shortcut_binding_update_description, extras.device.name)
    }

    /** The physical key released on the targeted device, or null if the event isn't for us. */
    fun releasedKey(event: KeyEvent): InputKey? {
        if (event.action != KeyEvent.ACTION_UP || !isTargetedDevice(event.device)) return null
        return InputKey(event.keyCode)
    }

    fun consumes(event: KeyEvent): Boolean = isTargetedDevice(event.device)

    /** Another shortcut on this device already using exactly [keys], if any. */
    suspend fun conflictFor(keys: List<InputKey>): GameShortcutType? {
        val codes = keys.map { it.keyCode }.toSet()
        return inputDeviceManager.getCurrentShortcuts(extras.device)
            .firstOrNull { it.type != extras.shortcutType && it.keys.isNotEmpty() && it.keys == codes }
            ?.type
    }

    /** Saves [keys] for this shortcut; [replacing] is a conflicting shortcut to clear first. */
    suspend fun assign(
        keys: List<InputKey>,
        replacing: GameShortcutType? = null,
    ) {
        replacing?.let { inputDeviceManager.clearShortcutBinding(extras.device, it) }
        inputDeviceManager.updateShortcutBinding(extras.device, extras.shortcutType, keys)
    }

    suspend fun clear() = inputDeviceManager.clearShortcutBinding(extras.device, extras.shortcutType)

    private fun isTargetedDevice(device: InputDevice?): Boolean {
        return device != null && extras.device.name == device.name
    }

    private fun parseExtras(intent: Intent): IntentExtras {
        val device =
            intent.extras?.getParcelable<InputDevice>(REQUEST_DEVICE)
                ?: throw IllegalArgumentException("REQUEST_DEVICE has not been passed")

        val shortcutType =
            intent.extras?.getString(REQUEST_SHORTCUT_TYPE)
                ?: throw IllegalArgumentException("REQUEST_SHORTCUT_TYPE has not been passed")

        return IntentExtras(device, GameShortcutType.valueOf(shortcutType))
    }

    data class IntentExtras(val device: InputDevice, val shortcutType: GameShortcutType)

    companion object {
        const val REQUEST_DEVICE = "REQUEST_DEVICE"
        const val REQUEST_SHORTCUT_TYPE = "REQUEST_SHORTCUT_TYPE"

        /** After the first key, wait this long for a second one before saving a single-key shortcut. */
        const val COMBO_WINDOW_MS = 1500L
    }
}
