package com.omnidroid.lib.library

import com.omnidroid.common.graphics.ColorUtils
import com.omnidroid.lib.R

fun GameSystem.metaSystemID() = MetaSystemID.fromSystemID(id)

/** Meta systems represents a collection of systems which appear the same to the user. It's currently
 *  only for Arcade (without separating FBNeo, MAME2000 or MAME2003). */
enum class MetaSystemID(
    val titleResId: Int,
    val imageResId: Int,
    val systemIDs: List<SystemID>,
) {
    NES(
        R.string.game_system_title_nes,
        R.drawable.game_system_nes,
        listOf(SystemID.NES),
    ),
    SNES(
        R.string.game_system_title_snes,
        R.drawable.game_system_snes,
        listOf(SystemID.SNES),
    ),
    GENESIS(
        R.string.game_system_title_genesis,
        R.drawable.game_system_genesis,
        listOf(SystemID.GENESIS, SystemID.SEGACD, SystemID.SEGA32X),
    ),
    GB(
        R.string.game_system_title_gb,
        R.drawable.game_system_gb,
        listOf(SystemID.GB),
    ),
    GBC(
        R.string.game_system_title_gbc,
        R.drawable.game_system_gbc,
        listOf(SystemID.GBC),
    ),
    GBA(
        R.string.game_system_title_gba,
        R.drawable.game_system_gba,
        listOf(SystemID.GBA),
    ),
    N64(
        R.string.game_system_title_n64,
        R.drawable.game_system_n64,
        listOf(SystemID.N64),
    ),
    SMS(
        R.string.game_system_title_sms,
        R.drawable.game_system_sms,
        listOf(SystemID.SMS),
    ),
    PSP(
        R.string.game_system_title_psp,
        R.drawable.game_system_psp,
        listOf(SystemID.PSP),
    ),
    NDS(
        R.string.game_system_title_nds,
        R.drawable.game_system_ds,
        listOf(SystemID.NDS),
    ),
    GG(
        R.string.game_system_title_gg,
        R.drawable.game_system_gg,
        listOf(SystemID.GG),
    ),
    ATARI2600(
        R.string.game_system_title_atari2600,
        R.drawable.game_system_atari2600,
        listOf(SystemID.ATARI2600),
    ),
    PSX(
        R.string.game_system_title_psx,
        R.drawable.game_system_psx,
        listOf(SystemID.PSX),
    ),
    ARCADE(
        R.string.game_system_title_arcade,
        R.drawable.game_system_arcade,
        listOf(SystemID.FBNEO, SystemID.MAME2003PLUS),
    ),
    ATARI7800(
        R.string.game_system_title_atari7800,
        R.drawable.game_system_atari7800,
        listOf(SystemID.ATARI7800),
    ),
    LYNX(
        R.string.game_system_title_lynx,
        R.drawable.game_system_lynx,
        listOf(SystemID.LYNX),
    ),
    PC_ENGINE(
        R.string.game_system_title_pce,
        R.drawable.game_system_pce,
        listOf(SystemID.PC_ENGINE),
    ),
    NGP(
        R.string.game_system_title_ngp,
        R.drawable.game_system_ngp,
        listOf(SystemID.NGP, SystemID.NGC),
    ),
    WS(
        R.string.game_system_title_ws,
        R.drawable.game_system_ws,
        listOf(SystemID.WS, SystemID.WSC),
    ),
    DOS(
        R.string.game_system_title_dos,
        R.drawable.game_system_dos,
        listOf(SystemID.DOS),
    ),
    NINTENDO_3DS(
        R.string.game_system_title_3ds,
        R.drawable.game_system_3ds,
        listOf(SystemID.NINTENDO_3DS),
    ),
    PS2(
        R.string.game_system_title_ps2,
        R.drawable.game_system_ps2,
        listOf(SystemID.PS2),
    ),
    GAMECUBE(
        R.string.game_system_title_gamecube,
        R.drawable.game_system_gamecube,
        listOf(SystemID.GAMECUBE),
    ),
    WII(
        R.string.game_system_title_wii,
        R.drawable.game_system_wii,
        listOf(SystemID.WII),
    ),
    DREAMCAST(
        R.string.game_system_title_dreamcast,
        R.drawable.game_system_dreamcast,
        listOf(SystemID.DREAMCAST),
    ),
    WII_U(
        R.string.game_system_title_wii_u,
        R.drawable.game_system_wii_u,
        listOf(SystemID.WII_U),
    ),
    SWITCH(
        R.string.game_system_title_switch,
        R.drawable.game_system_switch,
        listOf(SystemID.SWITCH),
    ),
    PS3(
        R.string.game_system_title_ps3,
        R.drawable.game_system_ps3,
        listOf(SystemID.PS3),
    ),
    ATARI5200(R.string.game_system_title_atari5200, R.drawable.game_system_atari5200, listOf(SystemID.ATARI5200)),
    ATARI800(R.string.game_system_title_atari800, R.drawable.game_system_atari800, listOf(SystemID.ATARI800)),
    SATURN(R.string.game_system_title_saturn, R.drawable.game_system_saturn, listOf(SystemID.SATURN)),
    THREE_DO(R.string.game_system_title_3do, R.drawable.game_system_3do, listOf(SystemID.THREE_DO)),
    COLECOVISION(
        R.string.game_system_title_colecovision,
        R.drawable.game_system_colecovision,
        listOf(SystemID.COLECOVISION),
    ),
    C64(R.string.game_system_title_c64, R.drawable.game_system_c64, listOf(SystemID.C64)),
    MSX(R.string.game_system_title_msx, R.drawable.game_system_msx, listOf(SystemID.MSX)),
    MSX2(R.string.game_system_title_msx2, R.drawable.game_system_msx2, listOf(SystemID.MSX2)),
    APPLE2(R.string.game_system_title_apple2, R.drawable.game_system_apple2, listOf(SystemID.APPLE2)),
    JAGUAR(R.string.game_system_title_jaguar, R.drawable.game_system_jaguar, listOf(SystemID.JAGUAR)),
    ;

    fun color(): Int = ColorUtils.color(ordinal.toFloat() / values().size)

    companion object {
        fun fromSystemID(systemID: SystemID): MetaSystemID =
            when (systemID) {
                SystemID.FBNEO -> ARCADE
                SystemID.MAME2003PLUS -> ARCADE
                SystemID.ATARI2600 -> ATARI2600
                SystemID.GB -> GB
                SystemID.GBC -> GBC
                SystemID.GBA -> GBA
                SystemID.GENESIS -> GENESIS
                SystemID.SEGACD -> GENESIS
                SystemID.SEGA32X -> GENESIS
                SystemID.GG -> GG
                SystemID.N64 -> N64
                SystemID.NDS -> NDS
                SystemID.NES -> NES
                SystemID.PSP -> PSP
                SystemID.PSX -> PSX
                SystemID.SMS -> SMS
                SystemID.SNES -> SNES
                SystemID.PC_ENGINE -> PC_ENGINE
                SystemID.LYNX -> LYNX
                SystemID.ATARI7800 -> ATARI7800
                SystemID.DOS -> DOS
                SystemID.NGP -> NGP
                SystemID.NGC -> NGP
                SystemID.WS -> WS
                SystemID.WSC -> WS
                SystemID.NINTENDO_3DS -> NINTENDO_3DS
                SystemID.PS2 -> PS2
                SystemID.GAMECUBE -> GAMECUBE
                SystemID.WII -> WII
                SystemID.DREAMCAST -> DREAMCAST
                SystemID.WII_U -> WII_U
                SystemID.SWITCH -> SWITCH
                SystemID.PS3 -> PS3
                SystemID.ATARI5200 -> ATARI5200
                SystemID.ATARI800 -> ATARI800
                SystemID.SATURN -> SATURN
                SystemID.THREE_DO -> THREE_DO
                SystemID.COLECOVISION -> COLECOVISION
                SystemID.C64 -> C64
                SystemID.MSX -> MSX
                SystemID.MSX2 -> MSX2
                SystemID.APPLE2 -> APPLE2
                SystemID.JAGUAR -> JAGUAR
            }
    }
}
