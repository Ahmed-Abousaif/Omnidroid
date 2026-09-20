package com.omnidroid.metadata.rawg

/**
 * Best-effort mapping from Omnidroid/Libretro system ids to RAWG platform ids.
 * @see https://api.rawg.io/docs/#operation/platforms_list
 */
object RawgPlatformIds {
    private val bySystemId: Map<String, Int> =
        mapOf(
            "nes" to 49,
            "snes" to 79,
            "n64" to 83,
            "gb" to 26,
            "gbc" to 43,
            "gba" to 24,
            "nds" to 9,
            "3ds" to 8,
            "gc" to 105,
            "gamecube" to 105,
            "wii" to 11,
            "sg1000" to 11,
            "sms" to 74,
            "genesis" to 167,
            "megadrive" to 167,
            "sega32x" to 11,
            "segacd" to 11,
            "saturn" to 45,
            "dreamcast" to 106,
            "psx" to 27,
            "ps1" to 27,
            "psp" to 17,
            "atari2600" to 23,
            "atari7800" to 28,
            "lynx" to 28,
            "ngp" to 25,
            "ngpc" to 25,
            "wswan" to 52,
            "wswanc" to 52,
            "pcengine" to 72,
            "tg16" to 72,
            "arcade" to 28,
            "fbneo" to 28,
            "mame" to 28,
            "dos" to 4,
            "pc" to 4,
        )

    fun forSystemId(systemId: String?): Int? {
        if (systemId.isNullOrBlank()) return null
        return bySystemId[systemId.lowercase()]
    }
}
