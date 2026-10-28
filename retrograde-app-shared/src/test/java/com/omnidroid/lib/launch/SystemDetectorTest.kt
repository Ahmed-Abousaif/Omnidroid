package com.omnidroid.lib.launch

import com.omnidroid.lib.library.SystemID
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
    }

    @Test
    fun returnsNullForUnknownExtensions() {
        assertNull(SystemDetector.detectSystem("unknown"))
        assertNull(SystemDetector.detectSystem("xyz"))
        assertNull(SystemDetector.detectSystem(""))
    }
}
