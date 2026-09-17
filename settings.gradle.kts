@file:Suppress("ktlint")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}

include(
    ":retrograde-util",
    ":retrograde-app-shared",
    ":omnidroid-touchinput",
    ":omnidroid-app",
    ":omnidroid-metadata-libretro-db",
    ":omnidroid-app-ext-free",
    ":omnidroid-app-ext-play",
    ":bundled-cores",
    ":baselineprofile"
)

project(":bundled-cores").projectDir = File("omnidroid-cores/bundled-cores")

fun usePlayDynamicFeatures(): Boolean {
    val task = gradle.startParameter.taskRequests.toString()
    return task.contains("Play") && task.contains("Dynamic")
}

if (usePlayDynamicFeatures()) {
    include(
        ":omnidroid_core_desmume",
        ":omnidroid_core_dosbox_pure",
        ":omnidroid_core_fbneo",
        ":omnidroid_core_fceumm",
        ":omnidroid_core_gambatte",
        ":omnidroid_core_genesis_plus_gx",
        ":omnidroid_core_handy",
        ":omnidroid_core_mame2003_plus",
        ":omnidroid_core_mednafen_ngp",
        ":omnidroid_core_mednafen_pce_fast",
        ":omnidroid_core_mednafen_wswan",
        ":omnidroid_core_melonds",
        ":omnidroid_core_mgba",
        ":omnidroid_core_mupen64plus_next_gles3",
        ":omnidroid_core_pcsx_rearmed",
        ":omnidroid_core_ppsspp",
        ":omnidroid_core_prosystem",
        ":omnidroid_core_snes9x",
        ":omnidroid_core_stella",
        ":omnidroid_core_citra",
        ":omnidroid_core_pcee2"
    )

    project(":omnidroid_core_gambatte").projectDir = File("omnidroid-cores/omnidroid_core_gambatte")
    project(":omnidroid_core_desmume").projectDir = File("omnidroid-cores/omnidroid_core_desmume")
    project(":omnidroid_core_melonds").projectDir = File("omnidroid-cores/omnidroid_core_melonds")
    project(":omnidroid_core_fbneo").projectDir = File("omnidroid-cores/omnidroid_core_fbneo")
    project(":omnidroid_core_fceumm").projectDir = File("omnidroid-cores/omnidroid_core_fceumm")
    project(":omnidroid_core_genesis_plus_gx").projectDir = File("omnidroid-cores/omnidroid_core_genesis_plus_gx")
    project(":omnidroid_core_mame2003_plus").projectDir = File("omnidroid-cores/omnidroid_core_mame2003_plus")
    project(":omnidroid_core_mgba").projectDir = File("omnidroid-cores/omnidroid_core_mgba")
    project(":omnidroid_core_mupen64plus_next_gles3").projectDir = File("omnidroid-cores/omnidroid_core_mupen64plus_next_gles3")
    project(":omnidroid_core_pcsx_rearmed").projectDir = File("omnidroid-cores/omnidroid_core_pcsx_rearmed")
    project(":omnidroid_core_ppsspp").projectDir = File("omnidroid-cores/omnidroid_core_ppsspp")
    project(":omnidroid_core_snes9x").projectDir = File("omnidroid-cores/omnidroid_core_snes9x")
    project(":omnidroid_core_stella").projectDir = File("omnidroid-cores/omnidroid_core_stella")
    project(":omnidroid_core_handy").projectDir = File("omnidroid-cores/omnidroid_core_handy")
    project(":omnidroid_core_prosystem").projectDir = File("omnidroid-cores/omnidroid_core_prosystem")
    project(":omnidroid_core_mednafen_pce_fast").projectDir = File("omnidroid-cores/omnidroid_core_mednafen_pce_fast")
    project(":omnidroid_core_mednafen_ngp").projectDir = File("omnidroid-cores/omnidroid_core_mednafen_ngp")
    project(":omnidroid_core_mednafen_wswan").projectDir = File("omnidroid-cores/omnidroid_core_mednafen_wswan")
    project(":omnidroid_core_dosbox_pure").projectDir = File("omnidroid-cores/omnidroid_core_dosbox_pure")
    project(":omnidroid_core_citra").projectDir = File("omnidroid-cores/omnidroid_core_citra")
    project(":omnidroid_core_pcee2").projectDir = File("omnidroid-cores/omnidroid_core_pcee2")
}
