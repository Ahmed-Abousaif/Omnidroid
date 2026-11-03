package org.yuzu.yuzu_emu.features.input.model

import androidx.annotation.Keep

@Keep
enum class NativeAnalog(val int: Int) {
    LStick(0),
    RStick(1);

    companion object {
        fun from(int: Int): NativeAnalog = entries.firstOrNull { it.int == int } ?: LStick
    }
}

