package com.omnidroid.lib.library.scan

enum class MetadataKeyType {
    CRC,
    SERIAL,
    CODE,
    ROM_BASE,
    ROM_NAME,
}

data class MetadataKey(
    val type: MetadataKeyType,
    val value: String,
    val memberCrcs: List<String> = emptyList(),
)
