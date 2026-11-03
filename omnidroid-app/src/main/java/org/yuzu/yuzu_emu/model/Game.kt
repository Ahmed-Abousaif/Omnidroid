package org.yuzu.yuzu_emu.model

import androidx.annotation.Keep

@Keep
class Game(
    val title: String = "",
    val path: String = "",
    val programId: String = "",
    val developer: String = "",
    var version: String = "",
    val isHomebrew: Boolean = false,
)

