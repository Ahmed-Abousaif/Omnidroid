package com.omnidroid.lib.library.scan

import com.omnidroid.lib.library.SystemID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

class DetectionRulesTest {
    @Test
    fun romHashMatchesTheBundledDatabase() {
        assertEquals(2321860806577408022L, ScanKeys.romHash("Tony Hawk's Pro Skater 4 (Europe).cue"))
    }

    @Test
    fun playstationSerialMatchesTheDatabaseForm() {
        assertEquals("SLUS-00594", ScanKeys.playstationSerial("BOOT = cdrom:\\SLUS_005.94;1"))
        assertEquals("SLPM-65002", ScanKeys.playstationSerial("SLPM-65002-0"))
    }

    @Test
    fun n64ByteSwapHashesToBigEndian() {
        val little = byteArrayOf(0x40, 0x12, 0x37, 0x80.toByte(), 0x11, 0x22, 0x33, 0x44)
        val big = byteArrayOf(0x80.toByte(), 0x37, 0x12, 0x40, 0x44, 0x33, 0x22, 0x11)
        val swapped = CartridgeHasher.hash(ByteArraySource(little), HashMode.N64)
        val raw = CartridgeHasher.hash(ByteArraySource(big), HashMode.RAW)
        assertEquals(raw, swapped)
    }

    @Test
    fun snesCopierHeaderIsSkipped() {
        val body = ByteArray(1024) { 0x11 }
        val headered = ByteArray(512) { 0x22 } + body
        assertEquals(
            CartridgeHasher.hash(ByteArraySource(body), HashMode.RAW),
            CartridgeHasher.hash(ByteArraySource(headered), HashMode.SNES),
        )
    }

    @Test
    fun playstationDiscIsIdentifiedFromSystemCnf() {
        val image = playstationImage("BOOT = cdrom:\\SLUS_005.94;1\n")
        val hit = DiscIdentifier.identify(DiscImages.userSectors(ByteArraySource(image), DiscImages.layout(ByteArraySource(image))))
        assertEquals(SystemID.PSX, hit?.system)
        assertEquals("SLUS-00594", hit?.serial)
    }

    @Test
    fun boot2MarksPs2() {
        val image = playstationImage("BOOT2 = cdrom0:\\SLUS_200.02;1\n")
        val source = ByteArraySource(image)
        val hit = DiscIdentifier.identify(DiscImages.userSectors(source, DiscImages.layout(source)))
        assertEquals(SystemID.PS2, hit?.system)
        assertEquals("SLUS-20002", hit?.serial)
    }

    @Test
    fun gamecubeMagicAndCode() {
        val header = ByteArray(0x20)
        header[0] = 'G'.code.toByte()
        header[1] = 'W'.code.toByte()
        header[2] = '7'.code.toByte()
        header[3] = 'P'.code.toByte()
        header[0x1C] = 0xC2.toByte()
        header[0x1D] = 0x33
        header[0x1E] = 0x9F.toByte()
        header[0x1F] = 0x3D
        val id = NintendoDisc.fromHeader(header)
        assertEquals(SystemID.GAMECUBE, id?.system)
        assertEquals("GW7P", id?.code)
    }

    @Test
    fun wiiMagic() {
        val header = ByteArray(0x20)
        header[0] = 'S'.code.toByte()
        header[1] = 'P'.code.toByte()
        header[2] = '3'.code.toByte()
        header[3] = 'E'.code.toByte()
        header[0x18] = 0x5D
        header[0x19] = 0x1C
        header[0x1A] = 0x9E.toByte()
        header[0x1B] = 0xA3.toByte()
        assertEquals(SystemID.WII, NintendoDisc.fromHeader(header)?.system)
    }

    @Test
    fun mipsElfIsPs2AndPowerPcNeedsAFolder() {
        val mips = elf(ElfHeader.MACHINE_MIPS)
        val ppc = elf(ElfHeader.MACHINE_PPC)
        val mipsResult = DetectionRules.identify(ScanInput("game.elf", mips.size.toLong(), null, { ByteArraySource(mips) }))
        val ppcResult = DetectionRules.identify(ScanInput("game.elf", ppc.size.toLong(), "storage/wiiu/game.elf", { ByteArraySource(ppc) }))
        val ppcLoose = DetectionRules.identify(ScanInput("game.elf", ppc.size.toLong(), "storage/roms/game.elf", { ByteArraySource(ppc) }))
        assertEquals(SystemID.PS2, (mipsResult as Detection.Ready).system)
        assertEquals(SystemID.WII_U, (ppcResult as Detection.Ready).system)
        assertTrue(ppcLoose is Detection.Rejected)
    }

    @Test
    fun dolRequiresAConsoleFolder() {
        val bytes = ByteArray(16)
        val missing = DetectionRules.identify(ScanInput("game.dol", 16, "games/game.dol", { ByteArraySource(bytes) }))
        val hinted = DetectionRules.identify(ScanInput("game.dol", 16, "games/gamecube/game.dol", { ByteArraySource(bytes) }))
        assertTrue(missing is Detection.Rejected)
        assertEquals(SystemID.GAMECUBE, (hinted as Detection.Ready).system)
    }

    @Test
    fun folderHintUsesWholeSegments() {
        assertEquals(SystemID.PSX, FolderHints.system("primary/Games/PS1/game.iso"))
        assertEquals(SystemID.GENESIS, FolderHints.system("primary/Genesis/game.nes"))
        assertNull(FolderHints.system("primary/roms/game.nes"))
    }

    @Test
    fun chdHeaderRejectsOtherVersions() {
        val header = ByteArray(64)
        "MComprHD".toByteArray().copyInto(header)
        header[12] = 0
        header[13] = 0
        header[14] = 0
        header[15] = 5
        header[56] = 0
        header[57] = 0
        header[58] = 0x80.toByte()
        header[59] = 0
        assertTrue(ChdHeader.isVersion5(ByteArraySource(header)))
        header[15] = 4
        assertTrue(!ChdHeader.isVersion5(ByteArraySource(header)))
    }

    @Test
    fun zipDominantEntryUsesTheCentralDirectoryCrc() {
        val payload = "hello".toByteArray()
        val zip = storedZip("game.nes", payload)
        val entries = ZipDirectory.read(ByteArraySource(zip))!!
        assertEquals(1, entries.size)
        assertEquals(crc(payload), entries[0].crc)
        val detection = DetectionRules.identify(ScanInput("game.zip", zip.size.toLong(), null, { ByteArraySource(zip) }))
        val ready = detection as Detection.Ready
        assertEquals(SystemID.NES, ready.system)
        assertEquals(crc(payload), ready.key?.value)
    }

    @Test
    fun cueUsesTheHighDensityTrack() {
        val sheet =
            """
            FILE "track01.bin" BINARY
              TRACK 01 AUDIO
            REM HIGH-DENSITY AREA
            FILE "track02.bin" BINARY
              TRACK 02 MODE1/2352
            """.trimIndent()
        val bin = segaCdSector()
        val result =
            DetectionRules.identify(
                ScanInput(
                    name = "game.cue",
                    size = sheet.length.toLong(),
                    path = null,
                    open = { ByteArraySource(sheet.toByteArray()) },
                    openNamed = { name -> if (name == "track02.bin") ByteArraySource(bin) else null },
                ),
            )
        assertEquals(SystemID.SEGACD, (result as Detection.Ready).system)
    }

    private fun playstationImage(config: String): ByteArray {
        val sectors = ByteArray(19 * 2048)
        val pvd = 16 * 2048
        sectors[pvd] = 1
        "CD001".toByteArray().copyInto(sectors, pvd + 1)
        "PLAYSTATION".toByteArray().copyInto(sectors, pvd + 8)
        val root = pvd + 156
        sectors[root] = 34
        putLe32(sectors, root + 2, 17)
        putLe32(sectors, root + 10, 2048)
        sectors[root + 32] = 1
        val directory = 17 * 2048
        val record = directory
        val name = "SYSTEM.CNF;1".toByteArray()
        sectors[record] = (33 + name.size).toByte()
        putLe32(sectors, record + 2, 18)
        putLe32(sectors, record + 10, config.length)
        sectors[record + 32] = name.size.toByte()
        name.copyInto(sectors, record + 33)
        config.toByteArray().copyInto(sectors, 18 * 2048)
        return sectors
    }

    private fun segaCdSector(): ByteArray {
        val sector = ByteArray(2352)
        sector[0] = 0
        repeat(10) { sector[1 + it] = 0xFF.toByte() }
        sector[11] = 0
        "SEGADISCSYSTEM".toByteArray().copyInto(sector, 16)
        "T-12345-00".toByteArray().copyInto(sector, 16 + 0x183)
        return sector
    }

    private fun elf(machine: Int): ByteArray {
        val bytes = ByteArray(20)
        bytes[0] = 0x7F
        bytes[1] = 'E'.code.toByte()
        bytes[2] = 'L'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        bytes[4] = 1
        bytes[5] = 1
        bytes[18] = (machine and 0xFF).toByte()
        bytes[19] = ((machine shr 8) and 0xFF).toByte()
        return bytes
    }

    private fun storedZip(
        name: String,
        payload: ByteArray,
    ): ByteArray {
        val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
        val local = ByteArray(30 + nameBytes.size + payload.size)
        putLe32(local, 0, 0x04034b50)
        putLe32(local, 14, crc(payload).toLong(16).toInt())
        putLe32(local, 18, payload.size)
        putLe32(local, 22, payload.size)
        local[26] = nameBytes.size.toByte()
        nameBytes.copyInto(local, 30)
        payload.copyInto(local, 30 + nameBytes.size)
        val central = ByteArray(46 + nameBytes.size)
        putLe32(central, 0, 0x02014b50)
        putLe32(central, 16, crc(payload).toLong(16).toInt())
        putLe32(central, 20, payload.size)
        putLe32(central, 24, payload.size)
        central[28] = nameBytes.size.toByte()
        nameBytes.copyInto(central, 46)
        val eocd = ByteArray(22)
        putLe32(eocd, 0, 0x06054b50)
        eocd[8] = 1
        eocd[10] = 1
        putLe32(eocd, 12, central.size)
        putLe32(eocd, 16, local.size)
        return local + central + eocd
    }

    private fun crc(bytes: ByteArray): String {
        val crc = CRC32()
        crc.update(bytes)
        return "%08X".format(crc.value)
    }

    private fun putLe32(
        bytes: ByteArray,
        offset: Int,
        value: Int,
    ) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
        bytes[offset + 2] = (value ushr 16).toByte()
        bytes[offset + 3] = (value ushr 24).toByte()
    }
}
