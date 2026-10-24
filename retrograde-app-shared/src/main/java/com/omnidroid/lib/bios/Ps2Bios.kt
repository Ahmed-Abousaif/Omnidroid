package com.omnidroid.lib.bios

/**
 * Picks one PS2 BIOS. Fat regional dumps win over slim dumps, and an explicit
 * user choice wins over the game region.
 */
object Ps2Bios {
    const val AUTO = "auto"
    const val USA = "scph39001.bin"
    const val EUROPE = "scph39004.bin"
    const val JAPAN = "scph39000.bin"
    const val JAPAN_SERVED = "scph30000.bin"

    private val REGION =
        linkedMapOf(
            "USA" to listOf(USA),
            "Europe" to listOf(EUROPE),
            "Japan" to listOf(JAPAN, JAPAN_SERVED),
        )
    private val SLIM = listOf("scph70012.bin", "scph77001.bin")

    /** Checksums of the regional images shipped in the PS2 asset package. */
    private val ALTERNATE_CRC =
        mapOf(
            "A19E0BF5" to USA,
            "2FE21E4D" to EUROPE,
            "4FC3B495" to JAPAN_SERVED,
        )

    fun fileNameForCrc(crc: String): String? = ALTERNATE_CRC[crc.uppercase()]

    fun choose(
        regionLabels: Set<String>,
        available: Set<String>,
        overrideName: String?,
    ): String? {
        if (overrideName != null && overrideName in available) return overrideName
        for ((region, names) in REGION) {
            if (region !in regionLabels) continue
            names.firstOrNull { it in available }?.let { return it }
        }
        return (listOf(USA, EUROPE, JAPAN, JAPAN_SERVED) + SLIM).firstOrNull { it in available }
    }
}
