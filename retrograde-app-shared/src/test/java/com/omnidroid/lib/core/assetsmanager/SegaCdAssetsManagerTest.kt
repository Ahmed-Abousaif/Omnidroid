package com.omnidroid.lib.core.assetsmanager

import android.content.Context
import com.omnidroid.lib.bios.BiosManager
import com.omnidroid.lib.core.CoreAssets
import com.omnidroid.lib.core.CoreUpdater
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.SystemID
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class SegaCdAssetsManagerTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val directories get() = DirectoriesManager(context)
    private val preferences get() = context.getSharedPreferences("sega-cd-assets-test", Context.MODE_PRIVATE)
    private val archive = File("../omnidroid-cores/assets/sega-cd.zip")
    private val manager = SegaCdAssetsManager()
    private val expected =
        mapOf(
            "bios_CD_E.bin" to "e66fa1dc5820d254611fdcdba0662372",
            "bios_CD_J.bin" to "278a9397d192149e84e820ac621a8edd",
            "bios_CD_U.bin" to "2efd74e3232ff260e371b99f84024f7f",
        )

    private class PackageApi(
        private val bytes: ByteArray,
    ) : CoreUpdater.CoreManagerApi {
        var calls = 0
        var fail = false

        override suspend fun downloadFile(url: String) = downloadZip(url)

        override suspend fun downloadZip(url: String): Response<ResponseBody> {
            calls++
            if (fail) throw IOException("Offline")
            return Response.success(bytes.toResponseBody())
        }
    }

    @Before
    fun prepare() {
        directories.getSystemDirectory().deleteRecursively()
        preferences.edit().clear().commit()
    }

    @After
    fun clean() {
        directories.getSystemDirectory().deleteRecursively()
    }

    @Test
    fun packageContainsExactlyTheThreeVerifiedRegionalBios() {
        ZipFile(archive).use { zip ->
            val entries = zip.entries().asSequence().toList()
            assertEquals(expected.keys, entries.map { it.name }.toSet())
            entries.forEach { entry ->
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                assertEquals(128 * 1024, bytes.size)
                assertEquals(expected[entry.name], md5(bytes))
            }
        }
        assertTrue(CoreAssets.hasAssets(CoreID.GENESIS_PLUS_GX))
        assertTrue(CoreID.getAssetManager(CoreID.GENESIS_PLUS_GX) is SegaCdAssetsManager)
    }

    @Test
    fun allRegionsInstallAndSurviveBiosPruning() =
        runBlocking {
            val api = PackageApi(archive.readBytes())
            manager.retrieveAssetsIfNeeded(api, directories, preferences)

            expected.keys.forEach { name ->
                val file = File(directories.getSystemDirectory(), name)
                assertEquals(expected[name], md5(file.readBytes()))
                file.setLastModified(1_000)
            }
            BiosManager(directories).deleteBiosBefore(System.currentTimeMillis())

            assertTrue(manager.isReady(directories))
            assertEquals(
                3,
                BiosManager(directories).getBiosInfoAsync().detected.count { it.systemID == SystemID.SEGACD },
            )
            api.fail = true
            manager.retrieveAssetsIfNeeded(api, directories, preferences)
            assertEquals(1, api.calls)
        }

    @Test
    fun missingOrCorruptPublishedBiosIsRepairedOfflineFromTheVerifiedCache() =
        runBlocking {
            val api = PackageApi(archive.readBytes())
            manager.retrieveAssetsIfNeeded(api, directories, preferences)
            File(directories.getSystemDirectory(), "bios_CD_J.bin").delete()
            File(directories.getSystemDirectory(), "bios_CD_E.bin").writeBytes(ByteArray(128 * 1024))
            assertFalse(manager.isReady(directories))
            api.fail = true

            manager.retrieveAssetsIfNeeded(api, directories, preferences)

            assertTrue(manager.isReady(directories))
            assertEquals(1, api.calls)
        }

    @Test
    fun uninstallPreservesPreexistingUserBiosAndRemovesManagedCopies() =
        runBlocking {
            val imported = File(directories.getSystemDirectory(), "bios_CD_J.bin")
            ZipFile(archive).use { zip ->
                zip.getInputStream(zip.getEntry(imported.name)).use { input ->
                    imported.outputStream().use { input.copyTo(it) }
                }
            }
            manager.retrieveAssetsIfNeeded(PackageApi(archive.readBytes()), directories, preferences)

            manager.clearAssets(directories, preferences)

            assertEquals(expected[imported.name], md5(imported.readBytes()))
            assertFalse(File(directories.getSystemDirectory(), "bios_CD_E.bin").exists())
            assertFalse(File(directories.getSystemDirectory(), "bios_CD_U.bin").exists())
            assertFalse(manager.isReady(directories))
        }

    @Test
    fun uninstallPreservesAUserReplacementOfAManagedBios() =
        runBlocking {
            manager.retrieveAssetsIfNeeded(PackageApi(archive.readBytes()), directories, preferences)
            val replacement = File(directories.getSystemDirectory(), "bios_CD_J.bin")
            replacement.writeText("User supplied replacement")

            manager.clearAssets(directories, preferences)

            assertEquals("User supplied replacement", replacement.readText())
        }

    @Test
    fun invalidDownloadedArchiveDoesNotReplaceWorkingBios() =
        runBlocking {
            val valid = archive.readBytes()
            manager.retrieveAssetsIfNeeded(PackageApi(valid), directories, preferences)
            val broken = PackageApi("Not a firmware archive".toByteArray())

            assertTrue(runCatching { manager.retrieveAssetsIfNeeded(broken, directories, preferences, true) }.isFailure)

            assertTrue(manager.isReady(directories))
            expected.keys.forEach { name ->
                assertEquals(expected[name], md5(File(directories.getSystemDirectory(), name).readBytes()))
            }
        }

    private fun md5(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
}
