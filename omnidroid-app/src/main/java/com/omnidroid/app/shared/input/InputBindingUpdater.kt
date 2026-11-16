package com.omnidroid.app.shared.input

import android.content.Context
import android.content.Intent
import android.view.InputDevice
import android.view.KeyEvent
import com.omnidroid.R

class InputBindingUpdater(private val inputDeviceManager: InputDeviceManager, intent: Intent) {
    val extras = parseExtras(intent)

    private val target get() = RetroKey(extras.retroKey)

    fun getTitle(context: Context): String {
        val keyName = InputKey(extras.retroKey).displayName()
        return context.getString(R.string.gamepad_binding_update_title, keyName)
    }

    fun getMessage(context: Context): String {
        return context.getString(R.string.gamepad_binding_update_description, extras.device.name)
    }

    /** The physical key released on the targeted device, or null if the event isn't for us. */
    fun releasedKey(event: KeyEvent): InputKey? {
        if (event.action != KeyEvent.ACTION_UP || !isTargetedDevice(event.device)) return null
        return InputKey(event.keyCode)
    }

    /** Whether the dialog should consume [event] (every key from the targeted device). */
    fun consumes(event: KeyEvent): Boolean = isTargetedDevice(event.device)

    /** The other button [key] already drives on this device, if any. */
    suspend fun conflictFor(key: InputKey): RetroKey? =
        BindingEdits.conflictingButton(inputDeviceManager.getCurrentBindings(extras.device), target, key)

    suspend fun assign(
        key: InputKey,
        resolution: BindingEdits.ConflictResolution = BindingEdits.ConflictResolution.REPLACE,
    ) = inputDeviceManager.updateBinding(extras.device, target, key, resolution)

    suspend fun clear() = inputDeviceManager.clearBinding(extras.device, target)

    private fun isTargetedDevice(device: InputDevice?): Boolean {
        return device != null && extras.device.name == device.name
    }

    private fun parseExtras(intent: Intent): IntentExtras {
        val device =
            intent.extras?.getParcelable<InputDevice>(REQUEST_DEVICE)
                ?: throw IllegalArgumentException("REQUEST_DEVICE has not been passed")

        val retroKey =
            intent.extras?.getInt(REQUEST_RETRO_KEY)
                ?: throw IllegalArgumentException("REQUEST_RETRO_KEY has not been passed")

        return IntentExtras(device, retroKey)
    }

    data class IntentExtras(val device: InputDevice, val retroKey: Int)

    companion object {
        const val REQUEST_DEVICE = "REQUEST_DEVICE"
        const val REQUEST_RETRO_KEY = "REQUEST_RETRO_KEY"
    }
}
