package com.omnidroid.lib.core

import android.content.Context
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PicoDriveDownloadTest {
    @Test
    fun picoDriveUsesItsPublishedRevisionAndKeepsOtherCoreCaches() =
        runBlocking {
            val context: Context = RuntimeEnvironment.getApplication()
            val directories = DirectoriesManager(context)
            val root = directories.getCoresDirectory()
            root.deleteRecursively()
            val gx = File(File(root, GithubCoreDownloader.CORES_VERSION), CoreID.GENESIS_PLUS_GX.libretroFileName)
            gx.parentFile!!.mkdirs()
            gx.writeBytes(byteArrayOf(1, 2, 3))
            File(root, "obsolete/core.so").apply {
                parentFile!!.mkdirs()
                writeText("old")
            }
            val urls = mutableListOf<String>()
            val api =
                object : CoreUpdater.CoreManagerApi {
                    override suspend fun downloadFile(url: String): Response<ResponseBody> {
                        urls += url
                        return Response.success(byteArrayOf(0x7f, 0x45, 0x4c, 0x46).toResponseBody())
                    }

                    override suspend fun downloadZip(url: String): Response<ResponseBody> =
                        error("Unexpected archive download")
                }
            try {
                val downloader = GithubCoreDownloader(directories, api)
                val downloaded = downloader.retrieve(CoreID.PICODRIVE)
                assertTrue(downloaded.isFile)
                assertEquals(GithubCoreDownloader.versionFor(CoreID.PICODRIVE), downloaded.parentFile!!.name)
                assertTrue(
                    urls.single().contains(
                        "/${GithubCoreDownloader.versionFor(CoreID.PICODRIVE)}/omnidroid_core_picodrive/",
                    ),
                )
                assertEquals(downloaded, GithubCoreDownloader.findCached(root, CoreID.PICODRIVE))
                assertEquals(gx, downloader.retrieve(CoreID.GENESIS_PLUS_GX))
                assertEquals(downloaded, downloader.retrieve(CoreID.PICODRIVE))
                assertEquals(1, urls.size)
                assertFalse(File(root, "obsolete").exists())
                assertTrue(gx.exists())
            } finally {
                root.deleteRecursively()
            }
        }
}
