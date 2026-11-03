package com.omnidroid.lib.launch

import com.omnidroid.lib.library.SystemID
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SystemDetectorTest {

    @Test
    fun detectsUniqueExtensions() {
        assertEquals(SystemID.NES, SystemDetector.detectSystem("nes")?.id)
        assertEquals(SystemID.NES, SystemDetector.detectSystem(".NES")?.id)
        assertEquals(SystemID.SNES, SystemDetector.detectSystem("sfc")?.id)
        assertEquals(SystemID.SNES, SystemDetector.detectSystem("smc")?.id)
        assertEquals(SystemID.GBA, SystemDetector.detectSystem("gba")?.id)
        assertEquals(SystemID.GB, SystemDetector.detectSystem("gb")?.id)
        assertEquals(SystemID.GBC, SystemDetector.detectSystem("gbc")?.id)
        assertEquals(SystemID.N64, SystemDetector.detectSystem("z64")?.id)
        assertEquals(SystemID.N64, SystemDetector.detectSystem("n64")?.id)
        assertEquals(SystemID.NDS, SystemDetector.detectSystem("nds")?.id)
        assertEquals(SystemID.NINTENDO_3DS, SystemDetector.detectSystem("3ds")?.id)
        assertEquals(SystemID.NINTENDO_3DS, SystemDetector.detectSystem(".3DS")?.id)
        assertEquals(SystemID.NINTENDO_3DS, SystemDetector.detectSystem("3dsx")?.id)
        assertEquals(SystemID.NINTENDO_3DS, SystemDetector.detectSystem("cxi")?.id)
        assertEquals(SystemID.SWITCH, SystemDetector.detectSystem("nsp")?.id)
        assertEquals(SystemID.SWITCH, SystemDetector.detectSystem(".NSP")?.id)
        assertEquals(SystemID.SWITCH, SystemDetector.detectSystem("xci")?.id)
        assertEquals(SystemID.WII_U, SystemDetector.detectSystem("wux")?.id)
        assertEquals(SystemID.WII_U, SystemDetector.detectSystem(".WUX")?.id)
        assertEquals(SystemID.WII_U, SystemDetector.detectSystem("wud")?.id)
        assertEquals(SystemID.ATARI2600, SystemDetector.detectSystem("a26")?.id)
    }

    @Test
    fun detectsCollidingExtensionsWithSystemExtra() {
        // .chd can be PSX, PS2, Dreamcast, Sega CD
        assertEquals(SystemID.PSX, SystemDetector.detectSystem("chd", "psx")?.id)
        assertEquals(SystemID.PS2, SystemDetector.detectSystem("chd", "ps2")?.id)
        assertEquals(SystemID.DREAMCAST, SystemDetector.detectSystem("chd", "dreamcast")?.id)
        assertEquals(SystemID.SEGACD, SystemDetector.detectSystem("chd", "segacd")?.id)

        // .iso can be PSX, PSP, GameCube, Wii
        assertEquals(SystemID.PSP, SystemDetector.detectSystem("iso", "psp")?.id)
        assertEquals(SystemID.GAMECUBE, SystemDetector.detectSystem("iso", "gamecube")?.id)
        assertEquals(SystemID.WII, SystemDetector.detectSystem("iso", "wii")?.id)

        // .rvz can be GameCube or Wii
        assertEquals(SystemID.GAMECUBE, SystemDetector.detectSystem("rvz", "gamecube")?.id)
        assertEquals(SystemID.WII, SystemDetector.detectSystem("rvz", "wii")?.id)

        // .bin can be Genesis, PSX
        assertEquals(SystemID.GENESIS, SystemDetector.detectSystem("bin", "genesis")?.id)
        assertEquals(SystemID.PSX, SystemDetector.detectSystem("bin", "psx")?.id)
    }

    @Test
    fun detectsCollidingExtensionsWithoutSystemExtraFallback() {
        // Defaults to common console targets
        assertEquals(SystemID.PSX, SystemDetector.detectSystem("chd")?.id)
        assertEquals(SystemID.PSX, SystemDetector.detectSystem("iso")?.id)
        assertEquals(SystemID.PSX, SystemDetector.detectSystem("cue")?.id)
        assertEquals(SystemID.GENESIS, SystemDetector.detectSystem("bin")?.id)
        assertEquals(SystemID.FBNEO, SystemDetector.detectSystem("zip")?.id)
        assertEquals(SystemID.WII, SystemDetector.detectSystem("rvz")?.id)
        assertEquals(SystemID.WII, SystemDetector.detectSystem(".RVZ")?.id)
    }

    @Test
    fun detectsRvzHeaderSniffingForGameCubeAndWii() {
        // Construct mock RVZ header with disc_type at offset 0x48 (1 = GC, 2 = Wii)
        val gcHeader = ByteArray(0x100)
        "RVZ\u0001".toByteArray().copyInto(gcHeader, 0)
        gcHeader[0x4B] = 1 // disc_type = 1 (GameCube)
        assertEquals(
            SystemID.GAMECUBE,
            SystemDetector.detectSystem("rvz", null) { ByteArrayInputStream(gcHeader) }?.id
        )

        val wiiHeader = ByteArray(0x100)
        "RVZ\u0001".toByteArray().copyInto(wiiHeader, 0)
        wiiHeader[0x4B] = 2 // disc_type = 2 (Wii)
        assertEquals(
            SystemID.WII,
            SystemDetector.detectSystem("rvz", null) { ByteArrayInputStream(wiiHeader) }?.id
        )
    }

    @Test
    fun returnsNullForUnknownExtensions() {
        assertNull(SystemDetector.detectSystem("unknown"))
        assertNull(SystemDetector.detectSystem("xyz"))
        assertNull(SystemDetector.detectSystem(""))
    }
}
