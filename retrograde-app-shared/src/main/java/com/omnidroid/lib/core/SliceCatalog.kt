package com.omnidroid.lib.core

import com.omnidroid.lib.library.CoreID

object SliceCatalog {
    const val SCHEMA_VERSION = 2

    data class Slice(
        val id: String,
        val systems: List<String>,
        val cores: List<String>,
    )

    val slices =
        listOf(
            Slice("atari2600", listOf("atari2600"), listOf("stella")),
            Slice("nes", listOf("nes"), listOf("fceumm")),
            Slice("snes", listOf("snes"), listOf("snes9x")),
            Slice("sms", listOf("sms"), listOf("genesis_plus_gx")),
            Slice("md", listOf("md"), listOf("genesis_plus_gx")),
            Slice("scd", listOf("scd"), listOf("genesis_plus_gx")),
            Slice("gg", listOf("gg"), listOf("genesis_plus_gx")),
            Slice("gb", listOf("gb"), listOf("gambatte")),
            Slice("gbc", listOf("gbc"), listOf("gambatte")),
            Slice("gba", listOf("gba"), listOf("mgba")),
            Slice("n64", listOf("n64"), listOf("mupen64plus_next_gles3")),
            Slice("psx", listOf("psx"), listOf("pcsx_rearmed")),
            Slice("psp", listOf("psp"), listOf("ppsspp")),
            Slice("arcade", listOf("fbneo", "mame2003plus"), listOf("fbneo", "mame2003_plus")),
            Slice("nds", listOf("nds"), listOf("desmume", "melondsds", "melonds")),
            Slice("3ds", listOf("3ds"), listOf("azahar", "citra")),
            Slice("atari7800", listOf("atari7800"), listOf("prosystem")),
            Slice("lynx", listOf("lynx"), listOf("handy")),
            Slice("pce", listOf("pce"), listOf("mednafen_pce_fast")),
            Slice("ngp", listOf("ngp"), listOf("mednafen_ngp")),
            Slice("ngc", listOf("ngc"), listOf("mednafen_ngp")),
            Slice("ws", listOf("ws"), listOf("mednafen_wswan")),
            Slice("wsc", listOf("wsc"), listOf("mednafen_wswan")),
            Slice("dos", listOf("dos"), listOf("dosbox_pure")),
            Slice("ps2", listOf("ps2"), listOf("armsx2")),
            Slice("gamecube", listOf("gamecube"), listOf("dolphin")),
            Slice("wii", listOf("wii"), listOf("dolphin")),
            Slice("dreamcast", listOf("dreamcast"), listOf("flycast")),
        )

    fun forCore(core: CoreID): List<Slice> = slices.filter { core.coreName in it.cores }

    fun byId(id: String): Slice? = slices.firstOrNull { it.id == id }

    fun needsInstall(
        installedSha: String?,
        installedSchema: Int?,
        sha: String,
        schema: Int,
    ): Boolean {
        if (schema != SCHEMA_VERSION) return false
        return installedSha != sha || installedSchema != schema
    }
}

interface MetadataSliceInstaller {
    suspend fun ensureSlices(
        context: android.content.Context,
        coreIDs: List<CoreID>,
    ): Set<String>
}

fun interface SliceInstallListener {
    fun onSlicesInstalled(sliceIds: Set<String>)
}
