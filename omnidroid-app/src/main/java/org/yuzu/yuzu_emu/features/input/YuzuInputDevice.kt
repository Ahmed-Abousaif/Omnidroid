package org.yuzu.yuzu_emu.features.input

import androidx.annotation.Keep

@Keep
interface YuzuInputDevice {
    fun getName(): String

    fun getGUID(): String

    fun getPort(): Int

    fun getSupportsVibration(): Boolean

    fun vibrate(intensity: Float)

    fun getAxes(): Array<Int> = arrayOf()

    fun hasKeys(keys: IntArray): BooleanArray = BooleanArray(0)
}

