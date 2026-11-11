package com.omnidroid.app.mobile.feature.gamemenu

import android.content.Intent
import com.omnidroid.app.shared.GameMenuContract as Contract
import com.omnidroid.touchinput.radial.sensors.TiltConfiguration
import java.io.Serializable

/** Explicit host commands; preference-only rows never dispatch one of these. */
sealed interface InGameMenuCommand {
    data object Restart : InGameMenuCommand
    data object Quit : InGameMenuCommand
    data object EditTouchControls : InGameMenuCommand
    data class Save(val slot: Int) : InGameMenuCommand
    data class Load(val slot: Int) : InGameMenuCommand
    data object LoadAutoSave : InGameMenuCommand
    data object SaveNew : InGameMenuCommand
    data class Audio(val enabled: Boolean) : InGameMenuCommand
    data class Speed(val multiplier: Int) : InGameMenuCommand
    data class Disc(val index: Int) : InGameMenuCommand
    data class Tilt(val configuration: TiltConfiguration) : InGameMenuCommand

    fun extras(): Map<String, Any> = when (this) {
        Restart -> mapOf(Contract.RESULT_RESET to true)
        Quit -> mapOf(Contract.RESULT_QUIT to true)
        EditTouchControls -> mapOf(Contract.RESULT_EDIT_TOUCH_CONTROLS to true)
        is Save -> mapOf(Contract.RESULT_SAVE to slot)
        is Load -> mapOf(Contract.RESULT_LOAD to slot)
        LoadAutoSave -> mapOf(Contract.RESULT_LOAD_AUTOSAVE to true)
        SaveNew -> mapOf(Contract.RESULT_SAVE_NEW to true)
        is Audio -> mapOf(Contract.RESULT_ENABLE_AUDIO to enabled)
        is Speed -> mapOf(Contract.RESULT_SET_FRAME_SPEED to multiplier, Contract.RESULT_ENABLE_FAST_FORWARD to (multiplier > 1))
        is Disc -> mapOf(Contract.RESULT_CHANGE_DISK to index)
        is Tilt -> mapOf(Contract.RESULT_CHANGE_TILT_CONFIG to configuration)
    }

    fun writeTo(intent: Intent) {
        extras().forEach { (key, value) ->
            when (value) {
                is Boolean -> intent.putExtra(key, value)
                is Int -> intent.putExtra(key, value)
                is Serializable -> intent.putExtra(key, value)
            }
        }
    }
}
