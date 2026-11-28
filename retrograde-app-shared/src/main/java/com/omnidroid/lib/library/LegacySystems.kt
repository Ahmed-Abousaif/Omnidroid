package com.omnidroid.lib.library

import com.omnidroid.lib.R
import com.omnidroid.lib.core.CoreVariable

/** Additional libretro consoles and home computers. */
internal object LegacySystems {
    fun all(): List<GameSystem> =
        listOf(
            GameSystem(
                SystemID.ATARI5200,
                "Atari - 5200",
                R.string.game_system_title_atari5200,
                R.string.game_system_abbr_atari5200,
                listOf(
                    SystemCoreConfig(
                        CoreID.A5200,
                        exposedSettings =
                            listOf(
                                ExposedSetting("a5200_bios", R.string.setting_a5200_bios),
                                ExposedSetting("a5200_input_hack", R.string.setting_a5200_input_hack),
                                ExposedSetting("a5200_analog_sensitivity", R.string.setting_a5200_analog_sensitivity),
                            ),
                        controllerConfigs =
                            hashMapOf(
                                0 to arrayListOf(ControllerConfigs.ATARI5200),
                                1 to arrayListOf(ControllerConfigs.ATARI5200.copy(libretroId = 1)),
                            ),
                        defaultSettings = listOf(CoreVariable("a5200_bios", "internal")),
                    ),
                ),
                uniqueExtensions = listOf("a52"),
                supportedExtensions = listOf("a52", "bin"),
            ),
            GameSystem(
                SystemID.ATARI800,
                "Atari - 8-bit Family",
                R.string.game_system_title_atari800,
                R.string.game_system_abbr_atari800,
                listOf(
                    SystemCoreConfig(
                        CoreID.ATARI800,
                        exposedSettings =
                            listOf(
                                ExposedSetting("atari800_system", R.string.setting_atari800_system),
                                ExposedSetting("atari800_internalbasic", R.string.setting_atari800_internalbasic),
                                ExposedSetting("atari800_ntscpal", R.string.setting_atari800_ntscpal),
                                ExposedSetting("atari800_cassboot", R.string.setting_atari800_cassboot),
                            ),
                        controllerConfigs =
                            hashMapOf(
                                0 to arrayListOf(ControllerConfigs.ATARI800),
                                1 to arrayListOf(ControllerConfigs.ATARI800),
                                2 to arrayListOf(ControllerConfigs.ATARI800),
                                3 to arrayListOf(ControllerConfigs.ATARI800),
                            ),
                        defaultSettings = listOf(CoreVariable("atari800_system", "Atari 800XL (64K)")),
                    ),
                ),
                uniqueExtensions = listOf("xfd", "atr", "atx", "dcm", "car", "xex"),
                supportedExtensions =
                    listOf(
                        "xfd",
                        "atr",
                        "atx",
                        "dcm",
                        "cas",
                        "bin",
                        "rom",
                        "car",
                        "com",
                        "xex",
                        "m3u",
                    ),
                hasMultiDiskSupport = true,
            ),
            GameSystem(
                SystemID.SATURN,
                "Sega - Saturn",
                R.string.game_system_title_saturn,
                R.string.game_system_abbr_saturn,
                listOf(
                    SystemCoreConfig(
                        CoreID.YABASANSHIRO,
                        exposedSettings =
                            listOf(
                                ExposedSetting(
                                    "yabasanshiro_resolution_mode",
                                    R.string.setting_yabasanshiro_resolution_mode,
                                ),
                                ExposedSetting("yabasanshiro_frameskip", R.string.setting_yabasanshiro_frameskip),
                                ExposedSetting(
                                    "yabasanshiro_system_language",
                                    R.string.setting_yabasanshiro_system_language,
                                ),
                            ),
                        controllerConfigs =
                            hashMapOf(
                                0 to arrayListOf(ControllerConfigs.SATURN),
                                1 to arrayListOf(ControllerConfigs.SATURN),
                            ),
                        requiredBIOSFiles = listOf("saturn_bios.bin"),
                    ),
                ),
                uniqueExtensions = listOf("ccd", "mds"),
                supportedExtensions = listOf("bin", "ccd", "chd", "cue", "iso", "mds"),
            ),
            GameSystem(
                SystemID.THREE_DO,
                "The 3DO Company - 3DO",
                R.string.game_system_title_3do,
                R.string.game_system_abbr_3do,
                listOf(
                    SystemCoreConfig(
                        CoreID.OPERA,
                        defaultSettings =
                            listOf(
                                CoreVariable("opera_bios", "opera-boot-internal"),
                                CoreVariable("opera_active_devices", "2"),
                            ),
                        exposedSettings =
                            listOf(
                                ExposedSetting("opera_bios", R.string.setting_opera_bios),
                                ExposedSetting("opera_region", R.string.setting_opera_region),
                                ExposedSetting("opera_high_resolution", R.string.setting_opera_high_resolution),
                                ExposedSetting("opera_active_devices", R.string.setting_opera_active_devices),
                            ),
                        controllerConfigs =
                            hashMapOf(
                                0 to arrayListOf(ControllerConfigs.THREE_DO),
                                1 to arrayListOf(ControllerConfigs.THREE_DO),
                            ),
                    ),
                ),
                uniqueExtensions = listOf(),
                supportedExtensions = listOf("iso", "bin", "chd", "cue"),
            ),
            GameSystem(
                SystemID.COLECOVISION,
                "Coleco - ColecoVision",
                R.string.game_system_title_colecovision,
                R.string.game_system_abbr_colecovision,
                listOf(
                    SystemCoreConfig(
                        CoreID.GEARCOLECO,
                        exposedSettings =
                            listOf(
                                ExposedSetting("gearcoleco_timing", R.string.setting_gearcoleco_timing),
                                ExposedSetting("gearcoleco_mapper", R.string.setting_gearcoleco_mapper),
                                ExposedSetting("gearcoleco_overscan", R.string.setting_gearcoleco_overscan),
                            ),
                        controllerConfigs =
                            hashMapOf(
                                0 to arrayListOf(ControllerConfigs.COLECOVISION),
                                1 to arrayListOf(ControllerConfigs.COLECOVISION),
                            ),
                        requiredBIOSFiles = listOf("colecovision.rom"),
                    ),
                ),
                uniqueExtensions = listOf("col", "cv"),
                supportedExtensions = listOf("col", "cv", "bin", "rom"),
            ),
            GameSystem(
                SystemID.C64,
                "Commodore - 64",
                R.string.game_system_title_c64,
                R.string.game_system_abbr_c64,
                listOf(
                    SystemCoreConfig(
                        CoreID.VICE_X64,
                        exposedSettings =
                            listOf(
                                ExposedSetting("vice_c64_model", R.string.setting_vice_c64_model),
                                ExposedSetting("vice_joyport", R.string.setting_vice_joyport),
                                ExposedSetting("vice_autostart", R.string.setting_vice_autostart),
                                ExposedSetting("vice_vkbd_theme", R.string.setting_vice_vkbd_theme),
                            ),
                        controllerConfigs =
                            hashMapOf(
                                0 to arrayListOf(ControllerConfigs.C64),
                                1 to arrayListOf(ControllerConfigs.C64),
                            ),
                    ),
                ),
                uniqueExtensions =
                    listOf(
                        "d64",
                        "d71",
                        "d80",
                        "d81",
                        "d82",
                        "g64",
                        "g41",
                        "x64",
                        "t64",
                        "tap",
                        "p00",
                        "crt",
                        "d6z",
                        "d7z",
                        "d8z",
                        "g6z",
                        "g4z",
                        "x6z",
                        "cmd",
                        "vfl",
                        "vsf",
                        "nbz",
                        "d2m",
                        "d4m",
                    ),
                supportedExtensions =
                    listOf(
                        "d64",
                        "d71",
                        "d80",
                        "d81",
                        "d82",
                        "g64",
                        "g41",
                        "x64",
                        "t64",
                        "tap",
                        "prg",
                        "p00",
                        "crt",
                        "bin",
                        "d6z",
                        "d7z",
                        "d8z",
                        "g6z",
                        "g4z",
                        "x6z",
                        "cmd",
                        "m3u",
                        "vfl",
                        "vsf",
                        "nib",
                        "nbz",
                        "d2m",
                        "d4m",
                    ),
                hasMultiDiskSupport = true,
            ),
            GameSystem(
                SystemID.MSX,
                "Microsoft - MSX",
                R.string.game_system_title_msx,
                R.string.game_system_abbr_msx,
                listOf(
                    SystemCoreConfig(
                        CoreID.FMSX,
                        exposedSettings =
                            listOf(
                                ExposedSetting("fmsx_video_mode", R.string.setting_fmsx_video_mode),
                                ExposedSetting("fmsx_hires", R.string.setting_fmsx_hires),
                                ExposedSetting("fmsx_mapper_type_mode", R.string.setting_fmsx_mapper_type_mode),
                            ),
                        controllerConfigs =
                            hashMapOf(
                                0 to arrayListOf(ControllerConfigs.MSX),
                                1 to arrayListOf(ControllerConfigs.MSX.copy(libretroId = 1)),
                            ),
                        defaultSettings = listOf(CoreVariable("fmsx_mode", "MSX1")),
                        requiredBIOSFiles = listOf("MSX.ROM"),
                    ),
                ),
                uniqueExtensions = listOf("mx1"),
                supportedExtensions = listOf("rom", "mx1", "dsk", "fdi", "cas", "m3u"),
                hasMultiDiskSupport = true,
            ),
            GameSystem(
                SystemID.MSX2,
                "Microsoft - MSX2",
                R.string.game_system_title_msx2,
                R.string.game_system_abbr_msx2,
                listOf(
                    SystemCoreConfig(
                        CoreID.FMSX,
                        exposedSettings =
                            listOf(
                                ExposedSetting("fmsx_video_mode", R.string.setting_fmsx_video_mode),
                                ExposedSetting("fmsx_hires", R.string.setting_fmsx_hires),
                                ExposedSetting("fmsx_mapper_type_mode", R.string.setting_fmsx_mapper_type_mode),
                            ),
                        controllerConfigs =
                            hashMapOf(
                                0 to arrayListOf(ControllerConfigs.MSX),
                                1 to arrayListOf(ControllerConfigs.MSX.copy(libretroId = 1)),
                            ),
                        defaultSettings = listOf(CoreVariable("fmsx_mode", "MSX2")),
                        requiredBIOSFiles = listOf("MSX2.ROM", "MSX2EXT.ROM"),
                    ),
                ),
                uniqueExtensions = listOf("mx2"),
                supportedExtensions = listOf("rom", "mx2", "dsk", "fdi", "cas", "m3u"),
                hasMultiDiskSupport = true,
            ),
            GameSystem(
                SystemID.APPLE2,
                "Apple - II",
                R.string.game_system_title_apple2,
                R.string.game_system_abbr_apple2,
                listOf(
                    SystemCoreConfig(
                        CoreID.APPLEWIN,
                        exposedSettings =
                            listOf(
                                ExposedSetting("applewin_machine", R.string.setting_applewin_machine),
                                ExposedSetting("applewin_video_mode", R.string.setting_applewin_video_mode),
                                ExposedSetting("applewin_keyboard_type", R.string.setting_applewin_keyboard_type),
                            ),
                        controllerConfigs =
                            hashMapOf(
                                0 to arrayListOf(ControllerConfigs.APPLE2),
                                1 to arrayListOf(ControllerConfigs.APPLE2),
                            ),
                    ),
                ),
                uniqueExtensions = listOf("do", "po", "woz", "2mg", "2img", "iie", "apl", "hdv", "yaml"),
                supportedExtensions =
                    listOf(
                        "bin",
                        "do",
                        "dsk",
                        "nib",
                        "po",
                        "woz",
                        "2mg",
                        "2img",
                        "iie",
                        "apl",
                        "hdv",
                        "yaml",
                        "m3u",
                    ),
                hasMultiDiskSupport = true,
            ),
            GameSystem(
                SystemID.JAGUAR,
                "Atari - Jaguar",
                R.string.game_system_title_jaguar,
                R.string.game_system_abbr_jaguar,
                listOf(
                    SystemCoreConfig(
                        CoreID.VIRTUAL_JAGUAR,
                        exposedSettings =
                            listOf(
                                ExposedSetting(
                                    "virtualjaguar_usefastblitter",
                                    R.string.setting_virtualjaguar_usefastblitter,
                                ),
                                ExposedSetting("virtualjaguar_pal", R.string.setting_virtualjaguar_pal),
                                ExposedSetting("virtualjaguar_bios", R.string.setting_virtualjaguar_bios),
                            ),
                        defaultSettings = listOf(CoreVariable("virtualjaguar_p1_numpad_to_kb", "Number Row Keys")),
                        controllerConfigs =
                            hashMapOf(
                                0 to arrayListOf(ControllerConfigs.JAGUAR),
                                1 to arrayListOf(ControllerConfigs.JAGUAR),
                            ),
                    ),
                ),
                uniqueExtensions = listOf("j64", "jag", "abs", "cof"),
                supportedExtensions = listOf("j64", "jag", "rom", "abs", "cof", "bin", "prg"),
            ),
        )
}
