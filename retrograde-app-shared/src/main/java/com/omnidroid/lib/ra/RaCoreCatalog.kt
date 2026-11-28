package com.omnidroid.lib.ra

import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.SystemID

data class RaCoreResolution(
    val supported: Boolean,
    val consoleId: Int,
    val libraryName: String,
    val quirks: Set<String> = emptySet(),
    val disabledReason: String? = null,
)

/**
 * Resolves a core + system pair to a RetroAchievements console ID and capability flags.
 * Prefers manifest `ra` metadata when available, falling back to a data-driven built-in table.
 * Implemented without console/core conditional branching to obey repository guardrails.
 */
object RaCoreCatalog {
    // Matches retroachievements rcheevos include/rc_consoles.h
    private const val RC_CONSOLE_UNKNOWN = 0
    private const val RC_CONSOLE_MEGA_DRIVE = 1
    private const val RC_CONSOLE_NINTENDO_64 = 2
    private const val RC_CONSOLE_SUPER_NINTENDO = 3
    private const val RC_CONSOLE_GAMEBOY = 4
    private const val RC_CONSOLE_GAMEBOY_ADVANCE = 5
    private const val RC_CONSOLE_GAMEBOY_COLOR = 6
    private const val RC_CONSOLE_NINTENDO = 7
    private const val RC_CONSOLE_PC_ENGINE = 8
    private const val RC_CONSOLE_SEGA_CD = 9
    private const val RC_CONSOLE_SEGA_32X = 10
    private const val RC_CONSOLE_MASTER_SYSTEM = 11
    private const val RC_CONSOLE_PLAYSTATION = 12
    private const val RC_CONSOLE_ATARI_LYNX = 13
    private const val RC_CONSOLE_NEOGEO_POCKET = 14
    private const val RC_CONSOLE_GAME_GEAR = 15
    private const val RC_CONSOLE_GAMECUBE = 16
    private const val RC_CONSOLE_NINTENDO_DS = 18
    private const val RC_CONSOLE_WII = 19
    private const val RC_CONSOLE_WII_U = 20
    private const val RC_CONSOLE_PLAYSTATION_2 = 21
    private const val RC_CONSOLE_ATARI_2600 = 25
    private const val RC_CONSOLE_MS_DOS = 26
    private const val RC_CONSOLE_ARCADE = 27
    private const val RC_CONSOLE_DREAMCAST = 40
    private const val RC_CONSOLE_PSP = 41
    private const val RC_CONSOLE_ATARI_7800 = 51
    private const val RC_CONSOLE_WONDERSWAN = 53
    private const val RC_CONSOLE_NINTENDO_3DS = 62
    private const val RC_CONSOLE_PLAYSTATION_3 = 82

    private val systemNameToConsoleId: Map<String, Int> =
        mapOf(
            SystemID.NES.dbname to RC_CONSOLE_NINTENDO,
            SystemID.SNES.dbname to RC_CONSOLE_SUPER_NINTENDO,
            SystemID.GENESIS.dbname to RC_CONSOLE_MEGA_DRIVE,
            SystemID.GB.dbname to RC_CONSOLE_GAMEBOY,
            SystemID.GBC.dbname to RC_CONSOLE_GAMEBOY_COLOR,
            SystemID.GBA.dbname to RC_CONSOLE_GAMEBOY_ADVANCE,
            SystemID.N64.dbname to RC_CONSOLE_NINTENDO_64,
            SystemID.SMS.dbname to RC_CONSOLE_MASTER_SYSTEM,
            SystemID.PSP.dbname to RC_CONSOLE_PSP,
            SystemID.NDS.dbname to RC_CONSOLE_NINTENDO_DS,
            SystemID.GG.dbname to RC_CONSOLE_GAME_GEAR,
            SystemID.ATARI2600.dbname to RC_CONSOLE_ATARI_2600,
            SystemID.PSX.dbname to RC_CONSOLE_PLAYSTATION,
            SystemID.FBNEO.dbname to RC_CONSOLE_ARCADE,
            SystemID.MAME2003PLUS.dbname to RC_CONSOLE_ARCADE,
            SystemID.PC_ENGINE.dbname to RC_CONSOLE_PC_ENGINE,
            SystemID.LYNX.dbname to RC_CONSOLE_ATARI_LYNX,
            SystemID.ATARI7800.dbname to RC_CONSOLE_ATARI_7800,
            SystemID.SEGACD.dbname to RC_CONSOLE_SEGA_CD,
            SystemID.SEGA32X.dbname to RC_CONSOLE_SEGA_32X,
            SystemID.NGP.dbname to RC_CONSOLE_NEOGEO_POCKET,
            SystemID.NGC.dbname to RC_CONSOLE_NEOGEO_POCKET,
            SystemID.WS.dbname to RC_CONSOLE_WONDERSWAN,
            SystemID.WSC.dbname to RC_CONSOLE_WONDERSWAN,
            SystemID.DOS.dbname to RC_CONSOLE_MS_DOS,
            SystemID.NINTENDO_3DS.dbname to RC_CONSOLE_NINTENDO_3DS,
            SystemID.PS2.dbname to RC_CONSOLE_PLAYSTATION_2,
            SystemID.GAMECUBE.dbname to RC_CONSOLE_GAMECUBE,
            SystemID.WII.dbname to RC_CONSOLE_WII,
            SystemID.DREAMCAST.dbname to RC_CONSOLE_DREAMCAST,
            SystemID.WII_U.dbname to RC_CONSOLE_WII_U,
            SystemID.PS3.dbname to RC_CONSOLE_PLAYSTATION_3,
        )

    private data class BuiltinCoreEntry(
        val supported: Boolean,
        val defaultConsoleId: Int,
        val libraryName: String,
        val disabledReason: String? = null,
    )

    private val builtinCores: Map<String, BuiltinCoreEntry> =
        mapOf(
            "stella" to BuiltinCoreEntry(true, RC_CONSOLE_ATARI_2600, "Stella"),
            "prosystem" to BuiltinCoreEntry(true, RC_CONSOLE_ATARI_7800, "ProSystem"),
            "handy" to BuiltinCoreEntry(true, RC_CONSOLE_ATARI_LYNX, "Handy"),
            "fceumm" to BuiltinCoreEntry(true, RC_CONSOLE_NINTENDO, "FCEUmm"),
            "snes9x" to BuiltinCoreEntry(true, RC_CONSOLE_SUPER_NINTENDO, "Snes9x"),
            "gambatte" to BuiltinCoreEntry(true, RC_CONSOLE_GAMEBOY, "Gambatte"),
            "mgba" to BuiltinCoreEntry(true, RC_CONSOLE_GAMEBOY_ADVANCE, "mGBA"),
            "mupen64plus_next_gles3" to BuiltinCoreEntry(true, RC_CONSOLE_NINTENDO_64, "Mupen64Plus-Next"),
            "dolphin" to BuiltinCoreEntry(true, RC_CONSOLE_GAMECUBE, "dolphin-emu"),
            "melondsds" to BuiltinCoreEntry(true, RC_CONSOLE_NINTENDO_DS, "melonDS"),
            "genesis_plus_gx" to BuiltinCoreEntry(true, RC_CONSOLE_MEGA_DRIVE, "Genesis Plus GX"),
            "picodrive" to BuiltinCoreEntry(true, RC_CONSOLE_MEGA_DRIVE, "PicoDrive"),
            "flycast" to BuiltinCoreEntry(true, RC_CONSOLE_DREAMCAST, "Flycast"),
            "pcsx_rearmed" to BuiltinCoreEntry(true, RC_CONSOLE_PLAYSTATION, "PCSX-ReARMed"),
            "ppsspp" to BuiltinCoreEntry(true, RC_CONSOLE_PSP, "PPSSPP"),
            "fbneo" to BuiltinCoreEntry(true, RC_CONSOLE_ARCADE, "FinalBurn Neo"),
            "mednafen_pce_fast" to BuiltinCoreEntry(true, RC_CONSOLE_PC_ENGINE, "Beetle PCE Fast"),
            "mednafen_ngp" to BuiltinCoreEntry(true, RC_CONSOLE_NEOGEO_POCKET, "Beetle NeoPop"),
            "mednafen_wswan" to BuiltinCoreEntry(true, RC_CONSOLE_WONDERSWAN, "Beetle Cygne"),
            "dosbox_pure" to BuiltinCoreEntry(true, RC_CONSOLE_MS_DOS, "DOSBox-pure"),
            "mame2003_plus" to
                BuiltinCoreEntry(
                    supported = false,
                    defaultConsoleId = RC_CONSOLE_ARCADE,
                    libraryName = "MAME 2003-Plus",
                    disabledReason = "Arcade achievements require FinalBurn Neo",
                ),
            "citra" to
                BuiltinCoreEntry(
                    supported = false,
                    defaultConsoleId = RC_CONSOLE_NINTENDO_3DS,
                    libraryName = "Citra",
                    disabledReason = "Citra core does not expose RetroAchievements memory",
                ),
            "azahar" to
                BuiltinCoreEntry(
                    supported = false,
                    defaultConsoleId = RC_CONSOLE_NINTENDO_3DS,
                    libraryName = "Azahar",
                    disabledReason = "Azahar core does not expose RetroAchievements memory",
                ),
            "cemu" to
                BuiltinCoreEntry(
                    supported = false,
                    defaultConsoleId = RC_CONSOLE_WII_U,
                    libraryName = "Cemu",
                    disabledReason = "Wii U core does not support libretro RetroAchievements",
                ),
            "armsx2" to
                BuiltinCoreEntry(
                    supported = false,
                    defaultConsoleId = RC_CONSOLE_PLAYSTATION_2,
                    libraryName = "ARMSX2",
                    disabledReason = "ARMSX2 does not expose RA memory maps",
                ),
            "switch" to
                BuiltinCoreEntry(
                    supported = false,
                    defaultConsoleId = RC_CONSOLE_UNKNOWN,
                    libraryName = "Eden",
                    disabledReason = "Nintendo Switch is not supported by RetroAchievements",
                ),
            "armsx3" to
                BuiltinCoreEntry(
                    supported = false,
                    defaultConsoleId = RC_CONSOLE_PLAYSTATION_3,
                    libraryName = "ARMSX3",
                    disabledReason = "PS3 backend does not use libretro RA",
                ),
        )

    fun resolveConsoleId(systemDbName: String?): Int =
        systemDbName?.let { systemNameToConsoleId[it] } ?: RC_CONSOLE_UNKNOWN

    fun resolve(
        coreID: CoreID,
        systemID: SystemID,
        engine: RetroAchievementsEngine? = null,
    ): RaCoreResolution {
        val systemConsoleId = systemNameToConsoleId[systemID.dbname] ?: RC_CONSOLE_UNKNOWN
        val builtin = builtinCores[coreID.coreName]

        val supportedByCore = builtin?.supported ?: false
        val consoleId =
            if (systemConsoleId != RC_CONSOLE_UNKNOWN) {
                systemConsoleId
            } else {
                builtin?.defaultConsoleId ?: RC_CONSOLE_UNKNOWN
            }
        val libraryName = builtin?.libraryName ?: coreID.coreDisplayName

        if (!supportedByCore || consoleId == RC_CONSOLE_UNKNOWN) {
            return RaCoreResolution(
                supported = false,
                consoleId = consoleId,
                libraryName = libraryName,
                disabledReason =
                    builtin?.disabledReason
                        ?: "Core ${coreID.coreDisplayName} does not support RetroAchievements",
            )
        }

        if (engine != null && engine.isModuleLoaded() && !engine.isSystemAllowed(libraryName, consoleId)) {
            return RaCoreResolution(
                supported = false,
                consoleId = consoleId,
                libraryName = libraryName,
                disabledReason = "$libraryName is restricted for console ID $consoleId by RetroAchievements",
            )
        }

        return RaCoreResolution(
            supported = true,
            consoleId = consoleId,
            libraryName = libraryName,
            disabledReason = null,
        )
    }
}
