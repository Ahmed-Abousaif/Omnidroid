package com.omnidroid.lib.core.assetsmanager

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AssetInstallationTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun zip(vararg entries: Pair<String, String>): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { output ->
            entries.forEach { (name, contents) ->
                output.putNextEntry(ZipEntry(name))
                output.write(contents.toByteArray())
                output.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }

    private fun install(destination: File, vararg entries: Pair<String, String>) {
        AssetInstallation.install(zip(*entries), destination, validate = { File(it, "required.bin").length() > 0 })
    }

    @Test fun missingFileIsDetectedEvenWhenTheFolderAndOtherFilesStillExist() {
        val destination = File(temporary.root, "assets")
        install(destination, "required.bin" to "ready", "font.bin" to "font")
        assertTrue(AssetInstallation.isComplete(destination))
        File(destination, "font.bin").delete()
        assertFalse(AssetInstallation.isComplete(destination))
    }

    @Test fun failedRefreshLeavesTheCompleteWorkingInstallationUntouched() {
        val destination = File(temporary.root, "assets")
        install(destination, "required.bin" to "original", "font.bin" to "font")
        assertThrows(IOException::class.java) { install(destination, "unrelated.bin" to "incomplete") }
        assertEquals("original", File(destination, "required.bin").readText())
        assertTrue(AssetInstallation.isComplete(destination))
        assertEquals(listOf("assets"), temporary.root.listFiles()!!.map { it.name })
    }

    @Test fun cancelledExtractionRetainsThePreviousAssetsAndRemovesStagingFiles() {
        val destination = File(temporary.root, "assets")
        install(destination, "required.bin" to "original")
        var checks = 0
        assertThrows(CancellationException::class.java) {
            AssetInstallation.install(
                zip("required.bin" to "replacement"), destination,
                validate = { true },
                checkCancelled = { if (++checks > 1) throw CancellationException("cancelled") },
            )
        }
        assertEquals("original", File(destination, "required.bin").readText())
        assertTrue(AssetInstallation.isComplete(destination))
        assertEquals(1, temporary.root.listFiles()!!.size)
    }

    @Test fun archivePathsCannotEscapeTheInstallationOrTouchSiblingUserFiles() {
        val destination = File(temporary.root, "assets")
        val userFile = File(temporary.root, "save.dat").apply { writeText("user data") }
        assertThrows(IOException::class.java) {
            install(destination, "../save.dat" to "overwrite", "required.bin" to "ready")
        }
        assertEquals("user data", userFile.readText())
        assertFalse(destination.exists())
    }

    @Test fun emptyPackagesAreNeverMarkedInstalled() {
        val destination = File(temporary.root, "assets")
        assertThrows(IOException::class.java) { install(destination) }
        assertFalse(AssetInstallation.isComplete(destination))
    }

    @Test fun changedFileContentsAreDetectedEvenWhenTheLengthIsUnchanged() {
        val destination = File(temporary.root, "assets")
        install(destination, "required.bin" to "original")
        File(destination, "required.bin").writeText("damaged!")
        assertFalse(AssetInstallation.isComplete(destination))
    }

    @Test fun successfulRefreshReplacesOnlyTheAssetDirectory() {
        val userDirectory = File(temporary.root, "User").apply { mkdir() }
        val save = File(userDirectory, "save.dat").apply { writeText("keep") }
        val destination = File(temporary.root, "Sys")
        install(destination, "required.bin" to "old", "obsolete.bin" to "old")
        install(destination, "required.bin" to "new")
        assertEquals("new", File(destination, "required.bin").readText())
        assertFalse(File(destination, "obsolete.bin").exists())
        assertEquals("keep", save.readText())
    }
}
