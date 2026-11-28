package com.omnidroid.lib.core

import android.content.ContextWrapper
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class BuildbotCoreDownloadTest {
    @get:Rule val temporary = TemporaryFolder()
    private val directories get() =
        DirectoriesManager(
            object : ContextWrapper(null) {
                override fun getFilesDir() = temporary.root
            },
        )

    private class FakeApi(
        private val content: ByteArray,
    ) : CoreUpdater.CoreManagerApi {
        var requested = ""

        override suspend fun downloadFile(url: String): Response<ResponseBody> = error("Expected a buildbot ZIP")

        override suspend fun downloadZip(url: String): Response<ResponseBody> {
            requested = url
            return Response.success(content.toResponseBody())
        }
    }

    private fun archive(payload: ByteArray): ByteArray =
        ByteArrayOutputStream()
            .also { output ->
                ZipOutputStream(output).use { zip ->
                    zip.putNextEntry(ZipEntry("a5200_libretro_android.so"))
                    zip.write(payload)
                    zip.closeEntry()
                }
            }.toByteArray()

    @Test fun newCoreCanInstallBeforePublicationAndIsThenCached() =
        runBlocking {
            val payload = byteArrayOf(0x7f, 0x45, 0x4c, 0x46, 1, 2, 3, 4)
            val api = FakeApi(archive(payload))
            val downloader = GithubCoreDownloader(directories, api)
            val installed = downloader.retrieve(CoreID.A5200)
            assertTrue(api.requested.startsWith("https://buildbot.libretro.com/nightly/android/latest/"))
            assertArrayEquals(payload, installed.readBytes())
            assertEquals("liba5200_libretro_android.so", installed.name)
            api.requested = ""
            assertEquals(installed, downloader.retrieve(CoreID.A5200))
            assertEquals("", api.requested)
        }

    @Test fun invalidLibraryDoesNotLeaveAnInstalledOrPartFile() =
        runBlocking {
            val downloader = GithubCoreDownloader(directories, FakeApi(archive("not an ELF library".toByteArray())))
            assertTrue(runCatching { downloader.retrieve(CoreID.A5200) }.isFailure)
            assertNull(GithubCoreDownloader.findCached(directories.getCoresDirectory(), CoreID.A5200))
            assertTrue(directories.getCoresDirectory().walkTopDown().none { it.name.endsWith(".part") })
        }
}
