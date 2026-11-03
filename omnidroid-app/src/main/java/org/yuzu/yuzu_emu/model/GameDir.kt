package org.yuzu.yuzu_emu.model

import androidx.annotation.Keep

@Keep
data class GameDir(
    val uriString: String,
    var deepScan: Boolean,
)

