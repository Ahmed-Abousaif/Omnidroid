package com.omnidroid.lib.controller

import com.omnidroid.touchinput.radial.sensors.TiltConfiguration
import com.omnidroid.touchinput.radial.settings.TouchControllerID
import java.io.Serializable

data class ControllerConfig(
    val name: String,
    val displayName: Int,
    val touchControllerID: TouchControllerID,
    val allowTouchRotation: Boolean = false,
    val allowTouchOverlay: Boolean = true,
    val allowDpadDiagonalsToggle: Boolean = true,
    val mergeDPADAndLeftStickEvents: Boolean = false,
    val hasAnalogStick: Boolean = false,
    val libretroDescriptor: String? = null,
    val libretroId: Int? = null,
    val tiltConfigurations: List<TiltConfiguration> = emptyList(),
) : Serializable {
    val allowDpadAsAnalog: Boolean
        get() = !hasAnalogStick

    fun getTouchControllerConfig(): TouchControllerID.Config {
        return TouchControllerID.getConfig(touchControllerID)
    }
}
