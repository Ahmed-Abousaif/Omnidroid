package com.omnidroid.app.shared.covers

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.ArtworkAttempt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class CoverDownloadCoordinationTest {
    @get:Rule val temp = TemporaryFolder()

    private fun context(): Context =
        object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            private val directory = temp.newFolder()

            override fun getFilesDir(): File = directory
        }

    private val url = "https://thumbnails.libretro.com/test/cover.png"

    private fun bytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(100, 150, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun client(
        calls: AtomicInteger,
        code: Int,
        content: ByteArray = byteArrayOf(),
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                calls.incrementAndGet()
                Response
                    .Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("fixture")
                    .body(content.toResponseBody("image/png".toMediaType()))
                    .build()
            }.build()

    @Test fun gridDetailAndDuplicateRomsShareOneSourceDownload() =
        runBlocking {
            val calls = AtomicInteger()
            val store = CoverCacheStore(context(), client(calls, 200, bytes()))
            val files =
                listOf(
                    async { store.ensureGrid(1, url) },
                    async { store.ensureDetail(1, url) },
                    async { store.ensureGrid(2, url) },
                ).awaitAll()
            assertTrue(files.all { it?.isFile == true })
            assertEquals(1, calls.get())
            assertNotEquals(files[0], files[2])
        }

    @Test fun onlyDefinitiveMissingResponsesPermitProviderFallback() =
        runBlocking {
            val missing = CoverCacheStore(context(), client(AtomicInteger(), 404))
            val unavailable = CoverCacheStore(context(), client(AtomicInteger(), 503))
            assertNull(missing.ensureGrid(1, url))
            assertNull(unavailable.ensureGrid(1, url))
            assertEquals("missing", missing.failure(url)?.state)
            assertEquals("retry", unavailable.failure(url)?.state)
            assertTrue(unavailable.failure(url)!!.retryAt < missing.failure(url)!!.retryAt)
        }

    @Test fun retryStateSurvivesStoreRecreationAndExpires() =
        runBlocking {
            val context = context()
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        context,
                        RetrogradeDatabase::class.java,
                    ).allowMainThreadQueries()
                    .build()
            try {
                val calls = AtomicInteger()
                val client = client(calls, 503)
                val first = CoverCacheStore(context, client).also { it.attachAttempts(db.catalogDao()) }
                first.ensureGrid(1, url)
                val second = CoverCacheStore(context, client).also { it.attachAttempts(db.catalogDao()) }
                second.ensureGrid(1, url)
                assertEquals(1, calls.get())
                db.catalogDao().putAttempt(ArtworkAttempt(url, "retry", 0))
                second.ensureGrid(1, url)
                assertEquals(2, calls.get())
            } finally {
                db.close()
            }
        }
}
