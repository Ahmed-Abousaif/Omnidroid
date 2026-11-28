package com.omnidroid.app.shared.ps3

import com.omnidroid.lib.backend.NormalizedKey
import com.omnidroid.lib.backend.NormalizedMotionSource
import kotlin.math.roundToInt

/** RPCS3 accepts whole-pad snapshots; updating one control must preserve the rest. */
class Ps3PadState {
    var digital1 = 0
        private set
    var digital2 = 0
        private set
    var lx = 128
        private set
    var ly = 128
        private set
    var rx = 128
        private set
    var ry = 128
        private set

    fun button(
        key: NormalizedKey,
        pressed: Boolean,
    ): Boolean {
        val (group, mask) =
            when (key) {
                NormalizedKey.BUTTON_SELECT -> 1 to 1
                NormalizedKey.BUTTON_THUMB_L -> 1 to 2
                NormalizedKey.BUTTON_THUMB_R -> 1 to 4
                NormalizedKey.BUTTON_START -> 1 to 8
                NormalizedKey.DPAD_UP -> 1 to 16
                NormalizedKey.DPAD_RIGHT -> 1 to 32
                NormalizedKey.DPAD_DOWN -> 1 to 64
                NormalizedKey.DPAD_LEFT -> 1 to 128
                NormalizedKey.BUTTON_HOME -> return false // Reserved for Omnidroid's menu.
                NormalizedKey.TRIGGER_L2 -> 2 to 1
                NormalizedKey.TRIGGER_R2 -> 2 to 2
                NormalizedKey.TRIGGER_L1 -> 2 to 4
                NormalizedKey.TRIGGER_R1 -> 2 to 8
                // Omnidroid's PlayStation layout uses A=circle, B=cross, X=triangle, Y=square.
                NormalizedKey.BUTTON_X -> 2 to 16
                NormalizedKey.BUTTON_A -> 2 to 32
                NormalizedKey.BUTTON_B -> 2 to 64
                NormalizedKey.BUTTON_Y -> 2 to 128
                else -> return false
            }
        if (group == 1) {
            digital1 = if (pressed) digital1 or mask else digital1 and mask.inv()
        } else {
            digital2 = if (pressed) digital2 or mask else digital2 and mask.inv()
        }
        return true
    }

    fun stick(
        source: NormalizedMotionSource,
        x: Float,
        y: Float,
    ) {
        fun axis(value: Float) = ((value.coerceIn(-1f, 1f) + 1f) * 127.5f).roundToInt()
        when (source) {
            NormalizedMotionSource.LeftStick -> {
                lx = axis(x)
                ly = axis(y)
            }
            NormalizedMotionSource.RightStick -> {
                rx = axis(x)
                ry = axis(y)
            }
        }
    }

    fun release() {
        digital1 = 0
        digital2 = 0
        lx = 128
        ly = 128
        rx = 128
        ry = 128
    }

    fun send(port: Int) = Armsx3Bridge.pad(port, digital1, digital2, lx, ly, rx, ry)
}
