package com.omnidroid.lib.library.scan

import com.omnidroid.lib.launch.SystemDetector
import com.omnidroid.lib.library.SystemID
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Sega32xDetectionTest {
    @Test
    fun nativeAndRenamedCartridgesUse32xAndTheSameRawChecksum() {
        val bytes = image("SEGA 32X")
        val expected = CartridgeHasher.hash(ByteArraySource(bytes), HashMode.RAW)
        listOf("32x", "bin", "gen", "md").forEach { extension ->
            val result = identify("game.$extension", bytes)
            assertEquals(SystemID.SEGA32X, result.system)
            assertEquals(expected, result.key?.value)
            assertEquals(
                SystemID.SEGA32X,
                SystemDetector.detectSystem(extension, openStream = { bytes.inputStream() })?.id,
            )
        }
    }

    @Test
    fun smdUsesOddThenEvenBanksAndSkipsTheCopierHeader() {
        val raw = image("SEGA 32X")
        val smd = interleave(raw)
        val expected = CartridgeHasher.hash(ByteArraySource(raw), HashMode.RAW)
        val result = identify("game.smd", smd)
        assertEquals(SystemID.SEGA32X, result.system)
        assertEquals(expected, result.key?.value)
        assertEquals(SystemID.SEGA32X, SystemDetector.detectSystem("smd", openStream = { smd.inputStream() })?.id)
    }

    @Test
    fun zipped32xAndRenamedCartridgesKeepTheArchiveMemberAndSystem() {
        val raw = image("SEGA 32X")
        val expected = CartridgeHasher.hash(ByteArraySource(raw), HashMode.RAW)
        listOf("32x", "bin", "md", "smd").forEach { extension ->
            val content = if (extension == "smd") interleave(raw) else raw
            val result = identify("game.zip", zip("nested/game.$extension", content))
            assertEquals(SystemID.SEGA32X, result.system)
            assertEquals(expected, result.key?.value)
            assertEquals("nested/game.$extension", result.archiveEntry)
        }
    }

    @Test
    fun provisionalGameIsInsertedAs32xBeforeHashing() {
        val systems = mutableListOf<SystemID>()
        val bytes = image("SEGA 32X")
        DetectionRules.identify(input("game.md", bytes)) { systems += it }
        assertEquals(listOf(SystemID.SEGA32X), systems)
    }

    @Test
    fun genesisAndSmdGenesisRemainGenesis() {
        val raw = image("SEGA MEGA DRIVE")
        val expected = CartridgeHasher.hash(ByteArraySource(raw), HashMode.RAW)
        listOf("bin", "gen", "md", "smd").forEach { extension ->
            val content = if (extension == "smd") interleave(raw) else raw
            val result = identify("game.$extension", content)
            assertEquals(SystemID.GENESIS, result.system)
            assertEquals(expected, result.key?.value)
        }
    }

    @Test
    fun externalHintsAndFolderAliasesAccept32x() {
        assertEquals(SystemID.SEGA32X, SystemDetector.detectSystem("bin", "Sega 32X")?.id)
        assertEquals(SystemID.SEGA32X, SystemDetector.detectSystem(".32X")?.id)
        assertEquals(SystemID.SEGA32X, FolderHints.system("/roms/sega32x/game.bin"))
    }

    private fun identify(
        name: String,
        bytes: ByteArray,
    ): Detection.Ready = DetectionRules.identify(input(name, bytes)) as Detection.Ready

    private fun input(
        name: String,
        bytes: ByteArray,
    ) = ScanInput(name, bytes.size.toLong(), null, {
        ByteArraySource(bytes)
    })

    private fun image(header: String) =
        ByteArray(32 * 1024) { (it * 13).toByte() }.also {
            header.padEnd(16).toByteArray().copyInto(it, 0x100)
        }

    private fun interleave(raw: ByteArray): ByteArray =
        ByteArray(raw.size + 512).also { output ->
            for (block in raw.indices step 16 * 1024) {
                repeat(8192) { index ->
                    output[512 + block + index] = raw[block + index * 2 + 1]
                    output[512 + block + 8192 + index] = raw[block + index * 2]
                }
            }
        }

    private fun zip(
        name: String,
        bytes: ByteArray,
    ): ByteArray =
        ByteArrayOutputStream()
            .also { output ->
                ZipOutputStream(output).use { zip ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }.toByteArray()
}
