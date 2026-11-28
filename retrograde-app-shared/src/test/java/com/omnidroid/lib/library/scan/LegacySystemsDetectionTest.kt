package com.omnidroid.lib.library.scan

import com.omnidroid.lib.core.ConsoleManager
import com.omnidroid.lib.launch.SystemDetector
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.MetaSystemID
import com.omnidroid.lib.library.SystemID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LegacySystemsDetectionTest {
    private fun detect(
        name: String,
        bytes: ByteArray = ByteArray(4096),
        path: String? = null,
    ) = DetectionRules.identify(ScanInput(name, bytes.size.toLong(), path, { ByteArraySource(bytes) }))

    @Test fun sharedComputerImagesRequireFoldersAndDoNotStealUniqueSuffixes() {
        assertTrue(detect("game.dsk") is Detection.Rejected)
        assertEquals(SystemID.APPLE2, (detect("game.dsk", path = "/roms/Apple II/game.dsk") as Detection.Ready).system)
        assertEquals(SystemID.MSX2, (detect("game.dsk", path = "/roms/msx2/game.dsk") as Detection.Ready).system)
        assertEquals(
            SystemID.ATARI800,
            (detect("game.cas", path = "/roms/atari800/game.cas") as Detection.Ready).system,
        )
        assertTrue(detect("game.rom") is Detection.Rejected)
        assertEquals(SystemID.JAGUAR, (detect("game.rom", path = "/roms/jaguar/game.rom") as Detection.Ready).system)
        assertEquals(SystemID.MSX2, (detect("game.mx2", path = "/roms/msx/game.mx2") as Detection.Ready).system)
    }

    @Test fun uniqueSuffixesImportWithoutFolderHints() {
        mapOf(
            "a52" to SystemID.ATARI5200,
            "atr" to SystemID.ATARI800,
            "col" to SystemID.COLECOVISION,
            "d64" to SystemID.C64,
            "mx1" to SystemID.MSX,
            "mx2" to SystemID.MSX2,
            "woz" to SystemID.APPLE2,
            "j64" to SystemID.JAGUAR,
        ).forEach { (extension, system) ->
            assertEquals(system, (detect("game.$extension") as Detection.Ready).system)
            assertEquals(system, SystemDetector.detectSystem(extension)?.id)
        }
    }

    @Test fun saturnAndOperaDiscsAreIdentifiedBeforeIso9660IncludingRawTracks() {
        val saturn =
            ByteArray(2048 * 20).also {
                "SEGA SEGASATURN ".toByteArray().copyInto(it)
                "MK-81000  ".toByteArray().copyInto(it, 0x20)
            }
        val opera =
            ByteArray(2048 * 20).also {
                it[0] = 1
                (1..5).forEach { i -> it[i] = 0x5a }
                it[6] = 1
            }
        listOf(saturn to SystemID.SATURN, opera to SystemID.THREE_DO).forEach { (iso, system) ->
            assertEquals(system, (detect("disc.iso", iso) as Detection.Ready).system)
            assertEquals(system, (detect("disc.bin", iso) as Detection.Ready).system)
            assertEquals(system, SystemDetector.detectSystem("iso", openStream = { iso.inputStream() })?.id)
            val raw = ByteArray(2352 * 20)
            repeat(20) { sector ->
                (1..10).forEach { raw[sector * 2352 + it] = 0xff.toByte() }
                iso.copyInto(raw, sector * 2352 + 16, sector * 2048, (sector + 1) * 2048)
            }
            assertEquals(system, (detect("disc.bin", raw) as Detection.Ready).system)
        }
    }

    @Test fun archivesRetainTheMemberAndFolderHintWithoutTreatingArcadeRomsAsComputerImages() {
        val archive =
            ByteArrayOutputStream()
                .also { output ->
                    ZipOutputStream(output).use { zip ->
                        zip.putNextEntry(ZipEntry("nested/game.rom"))
                        zip.write(ByteArray(128))
                        zip.closeEntry()
                    }
                }.toByteArray()
        val result = detect("game.zip", archive, "/roms/ColecoVision/game.zip") as Detection.Ready
        assertEquals(SystemID.COLECOVISION, result.system)
        assertEquals("nested/game.rom", result.archiveEntry)
        assertTrue(detect("arcade.zip", archive) is Detection.Arcade)
    }

    @Test fun msxConsolesKeepTheSharedCoreInstalledUntilBothAreRemoved() {
        assertTrue(
            ConsoleManager.isCoreStillNeeded(CoreID.FMSX, MetaSystemID.MSX, setOf(MetaSystemID.MSX, MetaSystemID.MSX2)),
        )
        assertFalse(ConsoleManager.isCoreStillNeeded(CoreID.FMSX, MetaSystemID.MSX, setOf(MetaSystemID.MSX)))
        val msx = GameSystem.findById("msx").systemCoreConfigs.single()
        val msx2 = GameSystem.findById("msx2").systemCoreConfigs.single()
        assertEquals("MSX1", msx.defaultSettings.single { it.key == "fmsx_mode" }.value)
        assertEquals("MSX2", msx2.defaultSettings.single { it.key == "fmsx_mode" }.value)
    }
}
