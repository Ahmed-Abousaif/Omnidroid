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
            "32x" to SystemID.SEGA32X,
            "sega32x" to SystemID.SEGA32X,
            "sega_32x" to SystemID.SEGA32X,
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
            "ps3" to SystemID.PS3,
            "playstation3" to SystemID.PS3,
            "playstation2" to SystemID.PS2,
            "gc" to SystemID.GAMECUBE,
            "gamecube" to SystemID.GAMECUBE,
            "ngc" to SystemID.GAMECUBE,
            "wii" to SystemID.WII,
            "wiiu" to SystemID.WII_U,
            "wii-u" to SystemID.WII_U,
            "switch" to SystemID.SWITCH,
            "nswitch" to SystemID.SWITCH,
            "nintendoswitch" to SystemID.SWITCH,
            "atari5200" to SystemID.ATARI5200,
            "a5200" to SystemID.ATARI5200,
            "5200" to SystemID.ATARI5200,
            "atari800" to SystemID.ATARI800,
            "atari8bit" to SystemID.ATARI800,
            "atari8bitfamily" to SystemID.ATARI800,
            "atari400" to SystemID.ATARI800,
            "atarixl" to SystemID.ATARI800,
            "atarixe" to SystemID.ATARI800,
            "saturn" to SystemID.SATURN,
            "segasaturn" to SystemID.SATURN,
            "3do" to SystemID.THREE_DO,
            "colecovision" to SystemID.COLECOVISION,
            "coleco" to SystemID.COLECOVISION,
            "c64" to SystemID.C64,
            "commodore64" to SystemID.C64,
            "msx" to SystemID.MSX,
            "msx1" to SystemID.MSX,
            "msx2" to SystemID.MSX2,
            "apple2" to SystemID.APPLE2,
            "appleii" to SystemID.APPLE2,
            "jaguar" to SystemID.JAGUAR,
            "atarijaguar" to SystemID.JAGUAR,
            "dreamcast" to SystemID.DREAMCAST,
            "dc" to SystemID.DREAMCAST,
        )

    fun system(path: String?): SystemID? {
        if (path.isNullOrBlank()) return null
        val segments =
            path
                .split('/', '\\')
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
    ): SystemID? = system(path)?.takeIf { it in allowed }

    private fun normalise(segment: String): String = segment.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
}
