package com.omnidroid.lib.core.assetsmanager

import android.content.Context
import com.omnidroid.lib.bios.BiosManager
import com.omnidroid.lib.core.CoreAssets
import com.omnidroid.lib.core.CoreUpdater
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.db.entity.Game
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
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ManagedFirmwareAssetsManagerTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val directories get() = DirectoriesManager(context)
    private val preferences get() = context.getSharedPreferences("managed-firmware-test", Context.MODE_PRIVATE)
    private val packages = File("../omnidroid-cores/assets")

    private fun archive(profile: FirmwarePackage) = File(packages, "${profile.archiveName}.zip")

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

    private fun networkManager(profile: FirmwarePackage) =
        object : ManagedFirmwareAssetsManager(profile) {
            override fun openBundledArchive(directories: DirectoriesManager): InputStream? = null
        }

    @Before fun prepare() {
        directories.getSystemDirectory().deleteRecursively()
        preferences.edit().clear().commit()
    }

    @After fun clean() {
        directories.getSystemDirectory().deleteRecursively()
    }

    @Test fun allPackagesContainExactlyTheExpectedVerifiedFirmware() {
        FirmwarePackage.entries.forEach { profile ->
            ZipFile(archive(profile)).use { zip ->
                assertEquals(
                    profile.files.keys,
                    zip
                        .entries()
                        .asSequence()
                        .map { it.name }
                        .toSet(),
                )
                profile.files.forEach { (name, spec) ->
                    val data = zip.getInputStream(zip.getEntry(name)).use { it.readBytes() }
                    assertEquals(spec.size, data.size.toLong())
                    assertEquals(spec.md5, md5(data))
                }
            }
        }
        listOf(CoreID.YABASANSHIRO, CoreID.GEARCOLECO, CoreID.FMSX).forEach {
            assertTrue(CoreAssets.hasAssets(it))
            assertTrue(CoreID.getAssetManager(it) is ManagedFirmwareAssetsManager)
        }
    }

    @Test fun everyPackageInstallsAndSurvivesLibraryBiosPruning() =
        runBlocking {
            FirmwarePackage.entries.forEach { profile ->
                val manager = networkManager(profile)
                manager.retrieveAssetsIfNeeded(PackageApi(archive(profile).readBytes()), directories, preferences)
                assertTrue(manager.isReady(directories))
                profile.files.keys.forEach { File(directories.getSystemDirectory(), it).setLastModified(1000) }
            }
            BiosManager(directories).deleteBiosBefore(System.currentTimeMillis())
            FirmwarePackage.entries.forEach { assertTrue(networkManager(it).isReady(directories)) }
            listOf("saturn", "colecovision", "msx", "msx2").forEach { id ->
                val game =
                    Game(
                        fileName = "game",
                        fileUri = "file:///game",
                        title = "Game",
                        systemId = id,
                        developer = null,
                        coverFrontUrl = null,
                        lastIndexedAt = 0,
                    )
                assertTrue(
                    BiosManager(
                        directories,
                    ).getMissingBiosFiles(GameSystem.findById(id).systemCoreConfigs.single(), game).isEmpty(),
                )
            }
        }

    @Test fun bundledArchiveInstallsWithoutAnyNetworkRequest() =
        runBlocking {
            val profile = FirmwarePackage.MSX
            val manager =
                object : ManagedFirmwareAssetsManager(profile) {
                    override fun openBundledArchive(directories: DirectoriesManager) = archive(profile).inputStream()
                }
            val offline = PackageApi(byteArrayOf()).apply { fail = true }
            manager.retrieveAssetsIfNeeded(offline, directories, preferences)
            assertEquals(0, offline.calls)
            assertTrue(manager.isReady(directories))
        }

    @Test fun missingAndCorruptFirmwareAreRepairedFromTheCacheOffline() =
        runBlocking {
            FirmwarePackage.entries.forEach { profile ->
                val api = PackageApi(archive(profile).readBytes())
                val manager = networkManager(profile)
                manager.retrieveAssetsIfNeeded(api, directories, preferences)
                File(directories.getSystemDirectory(), profile.files.keys.first()).delete()
                api.fail = true
                manager.retrieveAssetsIfNeeded(api, directories, preferences)
                File(directories.getSystemDirectory(), profile.files.keys.first()).writeText("corrupt")
                manager.retrieveAssetsIfNeeded(api, directories, preferences)
                assertTrue(manager.isReady(directories))
                assertEquals(1, api.calls)
            }
        }

    @Test fun uninstallPreservesPreexistingImportsAndUserReplacements() =
        runBlocking {
            val profile = FirmwarePackage.MSX
            val imported = File(directories.getSystemDirectory(), "MSX.ROM")
            ZipFile(archive(profile)).use { zip ->
                imported.writeBytes(zip.getInputStream(zip.getEntry(imported.name)).use { it.readBytes() })
            }
            val manager = networkManager(profile)
            manager.retrieveAssetsIfNeeded(PackageApi(archive(profile).readBytes()), directories, preferences)
            val replacement = File(directories.getSystemDirectory(), "MSX2.ROM").apply { writeText("User replacement") }
            manager.clearAssets(directories, preferences)
            assertEquals(profile.files.getValue(imported.name).md5, md5(imported.readBytes()))
            assertEquals("User replacement", replacement.readText())
            assertFalse(File(directories.getSystemDirectory(), "MSX2EXT.ROM").exists())
        }

    @Test fun invalidRefreshKeepsTheWorkingInstallation() =
        runBlocking {
            val profile = FirmwarePackage.SATURN
            val manager = networkManager(profile)
            manager.retrieveAssetsIfNeeded(PackageApi(archive(profile).readBytes()), directories, preferences)
            assertTrue(
                runCatching {
                    manager.retrieveAssetsIfNeeded(
                        PackageApi("bad archive".toByteArray()),
                        directories,
                        preferences,
                        true,
                    )
                }.isFailure,
            )
            assertTrue(manager.isReady(directories))
        }

    private fun md5(bytes: ByteArray) =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") {
            "%02x".format(it)
        }
}
