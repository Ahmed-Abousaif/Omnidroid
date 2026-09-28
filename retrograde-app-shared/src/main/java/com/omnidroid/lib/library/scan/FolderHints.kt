package com.omnidroid.lib.library.scan

import com.omnidroid.lib.library.SystemID
import java.util.Locale

object FolderHints {
    private val ALIASES: Map<String, SystemID> =
        mapOf(
            "atari2600" to SystemID.ATARI2600,
            "a2600" to SystemID.ATARI2600,
            "2600" to SystemID.ATARI2600,
            "nes" to SystemID.NES,
            "famicom" to SystemID.NES,
            "snes" to SystemID.SNES,
            "supernintendo" to SystemID.SNES,
            "superfamicom" to SystemID.SNES,
            "sfc" to SystemID.SNES,
            "sms" to SystemID.SMS,
            "mastersystem" to SystemID.SMS,
            "genesis" to SystemID.GENESIS,
            "megadrive" to SystemID.GENESIS,
            "md" to SystemID.GENESIS,
            "segacd" to SystemID.SEGACD,
            "megacd" to SystemID.SEGACD,
            "scd" to SystemID.SEGACD,
            "gg" to SystemID.GG,
            "gamegear" to SystemID.GG,
            "gb" to SystemID.GB,
            "gameboy" to SystemID.GB,
            "gbc" to SystemID.GBC,
            "gameboycolor" to SystemID.GBC,
            "gba" to SystemID.GBA,
            "gameboyadvance" to SystemID.GBA,
            "n64" to SystemID.N64,
            "nintendo64" to SystemID.N64,
            "psx" to SystemID.PSX,
            "ps1" to SystemID.PSX,
            "playstation" to SystemID.PSX,
            "psone" to SystemID.PSX,
            "psp" to SystemID.PSP,
            "playstationportable" to SystemID.PSP,
            "nds" to SystemID.NDS,
            "ds" to SystemID.NDS,
            "nintendods" to SystemID.NDS,
            "3ds" to SystemID.NINTENDO_3DS,
            "nintendo3ds" to SystemID.NINTENDO_3DS,
            "fbneo" to SystemID.FBNEO,
            "mame2003plus" to SystemID.MAME2003PLUS,
            "mame" to SystemID.MAME2003PLUS,
            "pce" to SystemID.PC_ENGINE,
            "pcengine" to SystemID.PC_ENGINE,
            "turbografx" to SystemID.PC_ENGINE,
            "turbografx16" to SystemID.PC_ENGINE,
            "tg16" to SystemID.PC_ENGINE,
            "lynx" to SystemID.LYNX,
            "atari7800" to SystemID.ATARI7800,
            "a7800" to SystemID.ATARI7800,
            "7800" to SystemID.ATARI7800,
            "ngp" to SystemID.NGP,
            "neogeopocket" to SystemID.NGP,
            "ngpc" to SystemID.NGC,
            "neogeopocketcolor" to SystemID.NGC,
            "ws" to SystemID.WS,
            "wonderswan" to SystemID.WS,
            "wsc" to SystemID.WSC,
            "wonderswancolor" to SystemID.WSC,
            "dos" to SystemID.DOS,
            "ps2" to SystemID.PS2,
            "playstation2" to SystemID.PS2,
            "gc" to SystemID.GAMECUBE,
            "gamecube" to SystemID.GAMECUBE,
            "ngc" to SystemID.GAMECUBE,
            "wii" to SystemID.WII,
            "wiiu" to SystemID.WII_U,
            "wii-u" to SystemID.WII_U,
            "dreamcast" to SystemID.DREAMCAST,
            "dc" to SystemID.DREAMCAST,
        )

    fun system(path: String?): SystemID? {
        if (path.isNullOrBlank()) return null
        val segments =
            path.split('/', '\\')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .dropLast(1)
        return segments.asReversed().firstNotNullOfOrNull { segment ->
            ALIASES[normalise(segment)]
        }
    }

    fun systemAmong(
        path: String?,
        allowed: Set<SystemID>,
    ): SystemID? {
        return system(path)?.takeIf { it in allowed }
    }

    private fun normalise(segment: String): String {
        return segment.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
    }
}
