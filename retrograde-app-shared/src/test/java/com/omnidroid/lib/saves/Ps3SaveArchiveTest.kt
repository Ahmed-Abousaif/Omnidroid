package com.omnidroid.lib.saves

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Ps3SaveArchiveTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun roundTripsOneTitleWithoutIncludingOtherGames() {
        val source = temporary.newFolder("source")
        File(source, "BLUS31426-SAVE/PARAM.SFO").apply { parentFile!!.mkdirs(); writeText("saved game") }
        File(source, "BLES00001-SAVE/DATA").apply { parentFile!!.mkdirs(); writeText("other game") }
        val archive = Ps3SaveArchive.capture(source, "BLUS31426")!!
        val destination = temporary.newFolder("destination")
        Ps3SaveArchive.restore(destination, "BLUS31426", archive)
        assertEquals("saved game", File(destination, "BLUS31426-SAVE/PARAM.SFO").readText())
        assertFalse(File(destination, "BLES00001-SAVE").exists())
    }

    @Test fun rejectsTraversalBeforeReplacingAnySave() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("BLUS31426-SAVE/DATA")); zip.write("old".toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("BLUS31426-SAVE/../../escape")); zip.write(1); zip.closeEntry()
        }
        val destination = temporary.newFolder("destination")
        assertThrows(IllegalArgumentException::class.java) { Ps3SaveArchive.restore(destination, "BLUS31426", output.toByteArray()) }
        assertFalse(File(destination, "BLUS31426-SAVE/DATA").exists())
        assertFalse(File(destination.parentFile, "escape").exists())
    }
}
