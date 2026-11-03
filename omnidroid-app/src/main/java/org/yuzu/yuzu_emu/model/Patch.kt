package org.yuzu.yuzu_emu.model

import androidx.annotation.Keep

@Keep
data class Patch(
    var enabled: Boolean,
    val name: String,
    val version: String,
    val type: Int,
    val programId: String,
    val titleId: String,
    val numericVersion: Long = 0,
    val source: Int = 0,
)

