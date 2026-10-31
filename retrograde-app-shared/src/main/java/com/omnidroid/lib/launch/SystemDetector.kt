package com.omnidroid.lib.launch

import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.SystemID
import java.util.Locale

/**
 * Detects the GameSystem for an external launch file from its extension and optional SYSTEM hint.
 */
object SystemDetector {

    private val SYSTEM_ALIASES = mapOf(
        "genesis" to SystemID.GENESIS,
        "megadrive" to SystemID.GENESIS,
        "mega_drive" to SystemID.GENESIS,
        "md" to SystemID.GENESIS,
        "playstation" to SystemID.PSX,
        "ps1" to SystemID.PSX,
        "psx" to SystemID.PSX,
        "ps" to SystemID.PSX,
        "ps2" to SystemID.PS2,
        "playstation2" to SystemID.PS2,
        "psp" to SystemID.PSP,
        "n64" to SystemID.N64,
        "nes" to SystemID.NES,
        "famicom" to SystemID.NES,
        "snes" to SystemID.SNES,
        "sfc" to SystemID.SNES,
        "super_nintendo" to SystemID.SNES,
        "gb" to SystemID.GB,
        "gameboy" to SystemID.GB,
        "gbc" to SystemID.GBC,
        "gameboy_color" to SystemID.GBC,
        "gba" to SystemID.GBA,
        "gameboy_advance" to SystemID.GBA,
        "nds" to SystemID.NDS,
        "ds" to SystemID.NDS,
        "3ds" to SystemID.NINTENDO_3DS,
        "nintendo_3ds" to SystemID.NINTENDO_3DS,
        "gamecube" to SystemID.GAMECUBE,
        "gc" to SystemID.GAMECUBE,
        "wii" to SystemID.WII,
        "wiiu" to SystemID.WII_U,
        "dreamcast" to SystemID.DREAMCAST,
        "dc" to SystemID.DREAMCAST,
        "fbneo" to SystemID.FBNEO,
        "arcade" to SystemID.FBNEO,
        "mame" to SystemID.MAME2003PLUS,
        "pce" to SystemID.PC_ENGINE,
        "pc_engine" to SystemID.PC_ENGINE,
        "turbografx" to SystemID.PC_ENGINE,
        "scd" to SystemID.SEGACD,
        "segacd" to SystemID.SEGACD,
        "mega_cd" to SystemID.SEGACD,
        "atari2600" to SystemID.ATARI2600,
        "atari7800" to SystemID.ATARI7800,
        "lynx" to SystemID.LYNX,
        "sms" to SystemID.SMS,
        "gg" to SystemID.GG,
        "ws" to SystemID.WS,
        "wsc" to SystemID.WSC,
        "ngp" to SystemID.NGP,
        "dos" to SystemID.DOS,
    )

    private val DEFAULT_COLLISION_DEFAULTS = mapOf(
        "chd" to SystemID.PSX,
        "iso" to SystemID.PSX,
        "cue" to SystemID.PSX,
        "pbp" to SystemID.PSX,
        "bin" to SystemID.GENESIS,
        "zip" to SystemID.FBNEO,
    )

    /**
     * Identifies the system given a file extension and optional system extra.
     *
     * @param rawExtension The file extension with or without leading dot.
     * @param systemHint Optional system extra string passed from frontend.
     * @return The resolved GameSystem, or null if unknown or unrecognizable.
     */
    fun detectSystem(rawExtension: String, systemHint: String? = null): GameSystem? {
        val extension = rawExtension.trimStart('.').lowercase(Locale.US)
        if (extension.isEmpty()) return null

        // 1. If systemHint is provided, attempt to match it to a known SystemID
        if (!systemHint.isNullOrBlank()) {
            val normalizedHint = systemHint.trim().lowercase(Locale.US)
                .replace("-", "_")
                .replace(" ", "_")

            val targetSystemId = SYSTEM_ALIASES[normalizedHint]
                ?: SystemID.values().firstOrNull {
                    it.dbname.equals(normalizedHint, ignoreCase = true) ||
                        it.name.equals(normalizedHint, ignoreCase = true)
                }

            if (targetSystemId != null) {
                val candidateSystem = runCatching { GameSystem.findById(targetSystemId.dbname) }.getOrNull()
                if (candidateSystem != null) {
                    return candidateSystem
                }
            }
        }

        // 2. Check unique extension across all systems
        val uniqueMatch = GameSystem.findByUniqueFileExtension(extension)
        if (uniqueMatch != null) {
            return uniqueMatch
        }

        // 3. Fallback for common colliding extensions without explicit hint
        val defaultSystemId = DEFAULT_COLLISION_DEFAULTS[extension]
        if (defaultSystemId != null) {
            return runCatching { GameSystem.findById(defaultSystemId.dbname) }.getOrNull()
        }

        return null
    }
}
