package org.yuzu.yuzu_emu.features.input.model

import androidx.annotation.Keep

@Keep
data class PlayerInput(
    var connected: Boolean = false,
    var buttons: Array<String> = emptyArray(),
    var analogs: Array<String> = emptyArray(),
    var motions: Array<String> = emptyArray(),
    var vibrationEnabled: Boolean = false,
    var vibrationStrength: Int = 100,
    var bodyColorLeft: Long = 0,
    var bodyColorRight: Long = 0,
    var buttonColorLeft: Long = 0,
    var buttonColorRight: Long = 0,
    var profileName: String = "",
    var useSystemVibrator: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PlayerInput

        if (connected != other.connected) return false
        if (!buttons.contentEquals(other.buttons)) return false
        if (!analogs.contentEquals(other.analogs)) return false
        if (!motions.contentEquals(other.motions)) return false
        if (vibrationEnabled != other.vibrationEnabled) return false
        if (vibrationStrength != other.vibrationStrength) return false
        if (bodyColorLeft != other.bodyColorLeft) return false
        if (bodyColorRight != other.bodyColorRight) return false
        if (buttonColorLeft != other.buttonColorLeft) return false
        if (buttonColorRight != other.buttonColorRight) return false
        if (profileName != other.profileName) return false
        return useSystemVibrator == other.useSystemVibrator
    }

    override fun hashCode(): Int {
        var result = connected.hashCode()
        result = 31 * result + buttons.contentHashCode()
        result = 31 * result + analogs.contentHashCode()
        result = 31 * result + motions.contentHashCode()
        result = 31 * result + vibrationEnabled.hashCode()
        result = 31 * result + vibrationStrength
        result = 31 * result + bodyColorLeft.hashCode()
        result = 31 * result + bodyColorRight.hashCode()
        result = 31 * result + buttonColorLeft.hashCode()
        result = 31 * result + buttonColorRight.hashCode()
        result = 31 * result + profileName.hashCode()
        result = 31 * result + useSystemVibrator.hashCode()
        return result
    }
}

