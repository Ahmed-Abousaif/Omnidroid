package com.omnidroid.lib.saves

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Ps2MemoryCardsTest {
    @get:Rule val temporary = TemporaryFolder()

    private lateinit var system: File
    private lateinit var saves: File
    private lateinit var ini: File
    private lateinit var legacy: File
    private lateinit var cards: File

    @Before
    fun setUp() {
        system = temporary.newFolder("system")
        saves = temporary.newFolder("saves")
        ini = File(system, "pcsx2/inis/armsx2-libretro.ini").apply { parentFile?.mkdirs() }
        legacy = File(system, "pcsx2/memcards").apply { mkdirs() }
        cards = File(saves, "pcsx2/memcards")
    }

    @Test
    fun `does nothing before the core has written its ini`() {
        File(legacy, "Mcd001.ps2").writeBytes(byteArrayOf(1))

        Ps2MemoryCards.prepare(system, saves)

        assertFalse(cards.exists())
    }

    @Test
    fun `points the core at the saves folder and keeps the rest of the ini`() {
        ini.writeText(INI)

        Ps2MemoryCards.prepare(system, saves)

        val lines = ini.readLines()
        assertTrue("MemoryCards = ${cards.absolutePath}" in lines)
        assertFalse("MemoryCards = memcards" in lines)
        assertTrue("Bios = bios" in lines)
        assertTrue("Slot1_Filename = Mcd001.ps2" in lines)
    }

    @Test
    fun `leaves an ini that already points at the saves folder untouched`() {
        ini.writeText(INI)
        Ps2MemoryCards.prepare(system, saves)
        ini.setLastModified(1_000)

        Ps2MemoryCards.prepare(system, saves)

        assertEquals(1_000, ini.lastModified())
    }

    @Test
    fun `adds the key when the ini has no folders section`() {
        ini.writeText("[MemoryCards]\nSlot1_Enable = true\n")

        Ps2MemoryCards.prepare(system, saves)

        val lines = ini.readLines()
        assertEquals("MemoryCards = ${cards.absolutePath}", lines[lines.indexOf("[Folders]") + 1])
        assertTrue("Slot1_Enable = true" in lines)
    }

    @Test
    fun `copies the old cards once and keeps the originals`() {
        ini.writeText(INI)
        File(legacy, "Mcd001.ps2").writeBytes(byteArrayOf(1, 2, 3))
        File(legacy, "notes.txt").writeText("not a card")

        Ps2MemoryCards.prepare(system, saves)

        assertArrayEquals(byteArrayOf(1, 2, 3), File(cards, "Mcd001.ps2").readBytes())
        assertTrue(File(legacy, "Mcd001.ps2").exists())
        assertFalse(File(cards, "notes.txt").exists())

        // A card deleted from the new folder doesn't come back from the old one.
        File(cards, "Mcd001.ps2").delete()
        Ps2MemoryCards.prepare(system, saves)
        assertFalse(File(cards, "Mcd001.ps2").exists())
    }

    @Test
    fun `keeps an imported card and the differing old one beside it`() {
        ini.writeText(INI)
        File(legacy, "Mcd001.ps2").writeBytes(byteArrayOf(1))
        File(legacy, "Mcd002.ps2").writeBytes(byteArrayOf(2))
        cards.mkdirs()
        File(cards, "Mcd001.ps2").writeBytes(byteArrayOf(9))
        File(cards, "Mcd002.ps2").writeBytes(byteArrayOf(2))

        Ps2MemoryCards.prepare(system, saves)

        assertArrayEquals(byteArrayOf(9), File(cards, "Mcd001.ps2").readBytes())
        assertArrayEquals(byteArrayOf(1), File(cards, "Mcd001 (old location).ps2").readBytes())
        assertFalse(File(cards, "Mcd002 (old location).ps2").exists())
    }

    @Test
    fun `retries after the ini switch fails and picks up the card the core kept using`() {
        ini.writeText(INI)
        File(legacy, "Mcd001.ps2").writeBytes(byteArrayOf(1))
        val blocker = File(ini.parentFile, ".${ini.name}.tmp").apply { mkdirs() }

        assertTrue(runCatching { Ps2MemoryCards.prepare(system, saves) }.isFailure)
        assertTrue("MemoryCards = memcards" in ini.readLines())

        // The game ran on the old folder and saved there.
        File(legacy, "Mcd001.ps2").writeBytes(byteArrayOf(2))
        blocker.delete()
        Ps2MemoryCards.prepare(system, saves)

        assertArrayEquals(byteArrayOf(2), File(cards, "Mcd001.ps2").readBytes())
        assertFalse(File(cards, "Mcd001 (old location).ps2").exists())
        assertTrue("MemoryCards = ${cards.absolutePath}" in ini.readLines())

        // Once switched, the old folder is left alone.
        File(legacy, "Mcd001.ps2").writeBytes(byteArrayOf(3))
        Ps2MemoryCards.prepare(system, saves)
        assertArrayEquals(byteArrayOf(2), File(cards, "Mcd001.ps2").readBytes())
    }

    @Test
    fun `a retry never replaces a card that was changed in the new folder`() {
        ini.writeText(INI)
        File(legacy, "Mcd001.ps2").writeBytes(byteArrayOf(1))
        val blocker = File(ini.parentFile, ".${ini.name}.tmp").apply { mkdirs() }
        runCatching { Ps2MemoryCards.prepare(system, saves) }

        File(cards, "Mcd001.ps2").writeBytes(byteArrayOf(7))
        File(legacy, "Mcd001.ps2").writeBytes(byteArrayOf(2))
        blocker.delete()
        Ps2MemoryCards.prepare(system, saves)

        assertArrayEquals(byteArrayOf(7), File(cards, "Mcd001.ps2").readBytes())
        assertArrayEquals(byteArrayOf(2), File(cards, "Mcd001 (old location).ps2").readBytes())
    }

    @Test
    fun `maps ps2 files from anywhere in an import to the card folder`() {
        assertEquals("pcsx2/memcards/Mcd001.ps2", Ps2MemoryCards.importPath("Mcd001.ps2"))
        assertEquals("pcsx2/memcards/Burnout.ps2", Ps2MemoryCards.importPath("PCSX2\\memcards\\Burnout.ps2"))
        assertEquals("pcsx2/memcards/Mcd002.ps2", Ps2MemoryCards.importPath("saves/pcsx2/memcards/Mcd002.ps2"))
        assertNull(Ps2MemoryCards.importPath("saves/Game.srm"))
        assertNull(Ps2MemoryCards.importPath("memcards/.ps2"))
        assertTrue(Ps2MemoryCards.isCardPath("pcsx2/memcards/Mcd001.ps2"))
        assertFalse(Ps2MemoryCards.isCardPath("Mcd001.ps2"))
    }

    private companion object {
        const val INI = """[UI]
SettingsVersion = 1

[Folders]
Bios = bios
MemoryCards = memcards
Logs = logs

[MemoryCards]
Slot1_Enable = true
Slot1_Filename = Mcd001.ps2
"""
    }
}
