package com.omnidroid.lib.core.assetsmanager

import android.content.Context
import com.omnidroid.lib.bios.BiosManager
import com.omnidroid.lib.bios.Ps2Bios
import com.omnidroid.lib.core.CoreUpdater
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.storage.DirectoriesManager
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ConsoleAssetsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val directories get() = DirectoriesManager(context)
    private val preferences get() = context.getSharedPreferences("asset-install-test", Context.MODE_PRIVATE)
    private val packages = File("../omnidroid-cores/assets")

    private class PackageApi(private val archive: File) : CoreUpdater.CoreManagerApi {
        var calls = 0
        var fail = false
        override suspend fun downloadFile(url: String) = downloadZip(url)
        override suspend fun downloadZip(url: String): Response<ResponseBody> {
            calls++
            if (fail) throw IOException("network interrupted")
            return Response.success(archive.readBytes().toResponseBody())
        }
    }

    @Before fun prepare() {
        directories.getSystemDirectory().deleteRecursively()
        preferences.edit().clear().commit()
        assertTrue("Core packages must be available for this integration test", packages.isDirectory)
    }

    @After fun clean() {
        directories.getSystemDirectory().deleteRecursively()
    }

    @Test fun allFourPackagedAssetArchivesInstallAndCanBeRemoved() = runBlocking {
        listOf(
            CoreID.PPSSPP to "ppsspp", CoreID.ARMSX2 to "pcee2",
            CoreID.DOLPHIN to "dolphin", CoreID.CEMU to "cemu",
        ).forEach { (core, archive) ->
            val manager = CoreID.getAssetManager(core)
            assertFalse("$core starts without assets", manager.isReady(directories))
            manager.retrieveAssetsIfNeeded(PackageApi(File(packages, "$archive.zip")), directories, preferences)
            assertTrue("$core support files must be complete", manager.isReady(directories))
            manager.clearAssets(directories, preferences)
            assertFalse("$core managed support files must be removed", manager.isReady(directories))
        }
    }

    @Test fun pspMissingFontIsReinstalledDespiteTheExistingFolderAndVersionMarker() = runBlocking {
        val api = PackageApi(File(packages, "ppsspp.zip"))
        val manager = PPSSPPAssetsManager()
        manager.retrieveAssetsIfNeeded(api, directories, preferences)
        File(directories.getSystemDirectory(), "PPSSPP/flash0/font/jpn0.pgf").delete()
        assertFalse(manager.isReady(directories))
        manager.retrieveAssetsIfNeeded(api, directories, preferences)
        assertTrue(manager.isReady(directories))
        assertEquals(2, api.calls)
    }

    @Test fun failedDolphinRefreshKeepsAssetsAndUninstallPreservesUserSaves() = runBlocking {
        val api = PackageApi(File(packages, "dolphin.zip"))
        val manager = DolphinAssetsManager()
        manager.retrieveAssetsIfNeeded(api, directories, preferences)
        val save = File(directories.getSystemDirectory(), "dolphin-emu/User/GC/save.dat")
        save.parentFile!!.mkdirs()
        save.writeText("keep this save")
        api.fail = true
        try {
            manager.retrieveAssetsIfNeeded(api, directories, preferences, force = true)
            fail("Expected network failure")
        } catch (_: IOException) { }
        assertTrue(manager.isReady(directories))
        manager.clearAssets(directories, preferences)
        assertEquals("keep this save", save.readText())
        assertFalse(manager.isReady(directories))
    }

    @Test fun cemuRepairsBothKeyLocationsWithoutRedownloadingTheValidCache() = runBlocking {
        val api = PackageApi(File(packages, "cemu.zip"))
        val manager = CemuAssetsManager()
        manager.retrieveAssetsIfNeeded(api, directories, preferences)
        val root = directories.getSystemDirectory()
        File(root, "keys.txt").delete()
        assertFalse(manager.isReady(directories))
        manager.retrieveAssetsIfNeeded(api, directories, preferences)
        assertTrue(manager.isReady(directories))
        assertArrayEquals(File(root, "Cemu/keys.txt").readBytes(), File(root, "keys.txt").readBytes())
        assertEquals(1, api.calls)
        manager.clearAssets(directories, preferences)
        assertFalse(File(root, "Cemu/keys.txt").exists())
        assertFalse(File(root, "keys.txt").exists())
    }

    @Test fun ps2UninstallRemovesDownloadedBiosButPreservesImportedBios() = runBlocking {
        val manager = PCEE2AssetsManager()
        manager.retrieveAssetsIfNeeded(PackageApi(File(packages, "pcee2.zip")), directories, preferences)
        val system = directories.getSystemDirectory()
        val imported = File(system, "imported.bin")
        ZipFile(File(packages, "pcee2.zip")).use { zip ->
            val entry = zip.entries().asSequence().first { PCEE2AssetsManager.ServedRegion.EUROPE.matches(it.name) }
            zip.getInputStream(entry).use { input -> imported.outputStream().use { input.copyTo(it) } }
        }
        assertTrue(BiosManager.adoptPs2File(system, imported))
        imported.delete()
        manager.clearAssets(directories, preferences)
        assertFalse(File(BiosManager.ps2Library(system), Ps2Bios.USA).exists())
        assertTrue(File(BiosManager.ps2Library(system), Ps2Bios.EUROPE).isFile)
        assertTrue(manager.isReady(directories))
    }

    @Test fun existingValidPspAssetsRemainUsableOfflineAfterAnUpgrade() = runBlocking {
        val api = PackageApi(File(packages, "ppsspp.zip"))
        val manager = PPSSPPAssetsManager()
        manager.retrieveAssetsIfNeeded(api, directories, preferences)
        val assets = File(directories.getSystemDirectory(), "PPSSPP")
        File(assets, ".omnidroid-assets").delete()
        File(assets, ".omnidroid-version").delete()
        api.fail = true
        manager.retrieveAssetsIfNeeded(api, directories, preferences)
        assertEquals(1, api.calls)
        assertTrue(manager.isReady(directories))
    }

    @Test fun cemuRefreshAndUninstallPreserveUserAddedKeys() = runBlocking {
        val api = PackageApi(File(packages, "cemu.zip"))
        val manager = CemuAssetsManager()
        manager.retrieveAssetsIfNeeded(api, directories, preferences)
        val custom = "1234567890abcdef1122334455667788"
        val system = directories.getSystemDirectory()
        File(system, "keys.txt").appendText("\n$custom # user supplied\n")
        manager.retrieveAssetsIfNeeded(api, directories, preferences, force = true)
        assertTrue(File(system, "Cemu/keys.txt").readText().contains(custom))
        manager.clearAssets(directories, preferences)
        assertTrue(File(system, "keys.txt").readText().contains(custom))
        assertTrue(File(system, "Cemu/keys.txt").readText().contains(custom))
        assertFalse(File(system, ".cemu-assets").exists())
    }
}
