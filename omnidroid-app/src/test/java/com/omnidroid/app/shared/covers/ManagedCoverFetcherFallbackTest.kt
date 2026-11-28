package com.omnidroid.app.shared.covers

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import coil.decode.DataSource
import coil.request.Options
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class ManagedCoverFetcherFallbackTest {
    @get:Rule val temp = TemporaryFolder()

    private val primaryUrl =
        "https://thumbnails.libretro.com/" +
            "Nintendo%20-%20Super%20Nintendo%20Entertainment%20System/" +
            "Named_Boxarts/Super%20Mario%20World%20%28USA%29.png"
    private val githubUrl =
        "https://raw.githubusercontent.com/" +
            "libretro-thumbnails/Nintendo_-_Super_Nintendo_Entertainment_System/master/" +
            "Named_Boxarts/Super%20Mario%20World%20%28USA%29.png"

    private fun testContext(): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val directory = temp.newFolder()
        return object : ContextWrapper(base) {
            override fun getFilesDir(): File = directory
        }
    }

    private fun pngBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }

    @Test
    fun managedCoverFetcherFallsBackToGithubWhenPrimaryFails() =
        runBlocking {
            val context = testContext()
            val requested = mutableListOf<String>()
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        val url = chain.request().url.toString()
                        requested += url
                        when (url) {
                            primaryUrl ->
                                Response
                                    .Builder()
                                    .request(Request.Builder().url(url).build())
                                    .protocol(Protocol.HTTP_1_1)
                                    .code(503)
                                    .message("Unavailable")
                                    .body(byteArrayOf().toResponseBody("image/png".toMediaType()))
                                    .build()
                            githubUrl ->
                                Response
                                    .Builder()
                                    .request(Request.Builder().url(url).build())
                                    .protocol(Protocol.HTTP_1_1)
                                    .code(200)
                                    .message("OK")
                                    .body(pngBytes().toResponseBody("image/png".toMediaType()))
                                    .build()
                            else -> error("Unexpected URL: $url")
                        }
                    }.build()
            val store = CoverCacheStore(context, client)
            val fetcher =
                ManagedCoverFetcher(
                    ManagedCover(42, primaryUrl, CoverCacheStore.Tier.GRID),
                    Options(context),
                ) { store }

            val result = fetcher.fetch()
            assertEquals(DataSource.DISK, result.dataSource)
            assertEquals(listOf(primaryUrl, githubUrl), requested)
            assertNotNull(store.gridFile(42, githubUrl))
        }
}
