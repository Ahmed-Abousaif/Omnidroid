package com.omnidroid.app.testing

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.SystemClock
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import coil.decode.DataSource
import coil.request.SuccessResult
import coil.size.Size
import com.omnidroid.app.shared.covers.CoverCacheStore
import com.omnidroid.app.shared.covers.CoverUtils
import com.omnidroid.app.shared.covers.backdropRequest
import com.omnidroid.lib.library.db.entity.Game
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

@RunWith(AndroidJUnit4::class)
class CoverCacheIdentityTest {
    @Test
    fun repeatedLookupsReuseSharpAndBlurredImagesAndReplacementInvalidatesBoth() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val game = Game(id = -900003, title = "Processed cache test", fileName = "test.nes",
            fileUri = "benchmark://cover-cache/test", systemId = "nes", developer = null,
            coverFrontUrl = "https://thumbnails.libretro.com/cover-cache-test.png", lastIndexedAt = 0)
        val directory = File(context.filesDir, "${CoverCacheStore.COVER_CACHE_DIR}/${game.id}")
        assertFalse("The test must not overwrite existing files", directory.exists())
        var color = Color.RED
        var downloads = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            downloads++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(imageBytes(color).toResponseBody("image/png".toMediaType())).build()
        }.build()
        val store = CoverCacheStore(context, client)
        val loader = ImageLoader.Builder(context).build()
        try {
            val file = checkNotNull(store.ensureGrid(game.id, game.coverFrontUrl!!))
            CoverUtils.coverCache(context)
            val originalTime = file.lastModified()
            val originalKey = CoverUtils.coverCacheKey(game)
            val sharpRequest = CoverUtils.coverRequest(context, game, size = Size(128, 128))
                .newBuilder().allowHardware(false).build()
            val blurRequest = backdropRequest(context, game, 128, 64, 4)
            loader.execute(sharpRequest) as SuccessResult
            loader.execute(blurRequest) as SuccessResult
            val started = SystemClock.elapsedRealtimeNanos()
            var sharpHits = 0
            var blurHits = 0
            var timestampsStable = true
            var keysStable = true
            repeat(20) {
                delay(2)
                assertEquals(file, store.gridFile(game.id, game.coverFrontUrl!!))
                assertEquals(file, store.preferredCoverFile(game.id, game.coverFrontUrl))
                timestampsStable = timestampsStable && originalTime == file.lastModified()
                keysStable = keysStable && originalKey == CoverUtils.coverCacheKey(game)
                val sharp = loader.execute(CoverUtils.coverRequest(context, game, size = Size(128, 128))
                    .newBuilder().allowHardware(false).build()) as SuccessResult
                val blur = loader.execute(backdropRequest(context, game, 128, 64, 4)) as SuccessResult
                if (sharp.dataSource == DataSource.MEMORY_CACHE) sharpHits++
                if (blur.dataSource == DataSource.MEMORY_CACHE) blurHits++
            }
            val message = "cover-cache: sharpHits=$sharpHits/20 blurHits=$blurHits/20 " +
                "timestampsStable=$timestampsStable keysStable=$keysStable elapsedMs=" +
                (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
            InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                putString("stream", "$message\n")
            })
            assertTrue("Reading a cover must not change its content timestamp", timestampsStable)
            assertTrue("Unchanged covers must keep the same cache key", keysStable)
            assertEquals(20, sharpHits)
            assertEquals(20, blurHits)
            assertEquals(1, downloads)

            // Re-downloading the same URL after eviction must still invalidate old memory entries.
            assertTrue(file.delete())
            delay(10)
            color = Color.BLUE
            val replacement = checkNotNull(store.ensureGrid(game.id, game.coverFrontUrl!!))
            assertEquals(file.absolutePath, replacement.absolutePath)
            val replacementSharpRequest = CoverUtils.coverRequest(context, game, size = Size(128, 128))
                .newBuilder().allowHardware(false).build()
            val replacementBlurRequest = backdropRequest(context, game, 128, 64, 4)
            assertNotEquals(sharpRequest.memoryCacheKey, replacementSharpRequest.memoryCacheKey)
            assertNotEquals(blurRequest.memoryCacheKey, replacementBlurRequest.memoryCacheKey)
            for (request in listOf(replacementSharpRequest, replacementBlurRequest)) {
                val result = loader.execute(request) as SuccessResult
                assertNotEquals(DataSource.MEMORY_CACHE, result.dataSource)
                val pixel = (result.drawable as BitmapDrawable).bitmap.getPixel(0, 0)
                assertTrue(Color.blue(pixel) > 230)
                assertTrue(Color.red(pixel) < 25)
                assertEquals(DataSource.MEMORY_CACHE, (loader.execute(request) as SuccessResult).dataSource)
            }
            assertEquals(2, downloads)
        } finally {
            loader.shutdown()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            directory.deleteRecursively()
        }
    }

    @Test
    fun evictionKeepsRecentlyReadImagesAcrossStoreRecreationAndDeletesTheirOldMarkers() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(base.cacheDir, "cover-lru-test-${System.nanoTime()}").apply { mkdirs() }
        val preferenceNames = mutableSetOf<String>()
        val context = object : ContextWrapper(base) {
            override fun getFilesDir() = directory
            override fun getSharedPreferences(name: String, mode: Int) =
                base.getSharedPreferences("${directory.name}-$name".also(preferenceNames::add), mode)
        }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(imageBytes(Color.GREEN).toResponseBody("image/png".toMediaType())).build()
        }.build()
        val url = "https://thumbnails.libretro.com/cover-lru-test.png"
        try {
            val store = CoverCacheStore(context, client)
            val first = checkNotNull(store.ensureGrid(1, url))
            val second = checkNotNull(store.ensureGrid(2, url))
            // Sparse files exceed the real cache budget without allocating hundreds of MB.
            for (file in listOf(first, second)) {
                RandomAccessFile(file, "rw").use { it.setLength(160L * 1024 * 1024) }
            }
            val firstContentTime = System.currentTimeMillis() - 30_000
            assertTrue(first.setLastModified(firstContentTime))
            assertTrue(second.setLastModified(firstContentTime + 10_000))
            store.gridFile(2, url)
            delay(10)
            store.gridFile(1, url)
            assertEquals(firstContentTime, first.lastModified())

            val recreated = CoverCacheStore(context, client)
            val third = checkNotNull(recreated.ensureGrid(3, url))
            assertTrue(first.exists())
            assertTrue(third.exists())
            assertFalse(second.exists())
            assertFalse(File("${second.path}.access").exists())
            assertEquals(1, recreated.lastEviction?.evictedFiles)
            assertEquals(160L * 1024 * 1024, recreated.lastEviction?.evictedBytes)
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            directory.deleteRecursively()
            preferenceNames.forEach(base::deleteSharedPreferences)
        }
    }

    private fun imageBytes(color: Int): ByteArray {
        val bitmap = createBitmap(256, 256).apply { eraseColor(color) }
        return try {
            ByteArrayOutputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                it.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
