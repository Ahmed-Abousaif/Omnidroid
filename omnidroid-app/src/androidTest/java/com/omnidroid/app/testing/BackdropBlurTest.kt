package com.omnidroid.app.testing

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import coil.decode.DataSource
import coil.request.SuccessResult
import coil.size.Size
import com.omnidroid.app.shared.covers.BackdropBlurTransformation
import com.omnidroid.app.shared.covers.CoverUtils
import com.omnidroid.app.shared.covers.CustomCoverManager
import com.omnidroid.app.shared.covers.backdropRequest
import com.omnidroid.lib.library.db.entity.Game
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackdropBlurTest {
    @Test
    fun solidColorIsPreservedAndInputRemainsUsable() = runBlocking {
        val input = createBitmap(64, 64).apply { eraseColor(Color.rgb(30, 100, 200)) }
        val result = BackdropBlurTransformation(128, 72, 4).transform(input, Size(128, 72))
        assertEquals(128, result.width)
        assertEquals(72, result.height)
        assertEquals(input.getPixel(0, 0), result.getPixel(0, 0))
        assertEquals(input.getPixel(0, 0), result.getPixel(127, 71))
        assertFalse(input.isRecycled)
    }

    @Test
    fun portraitCoverIsCenterCroppedBeforeBlurring() = runBlocking {
        val input = createBitmap(64, 128)
        val pixels = IntArray(64 * 128) { index ->
            when (index / 64) {
                in 0..31 -> Color.RED
                in 32..95 -> Color.GREEN
                else -> Color.BLUE
            }
        }
        input.setPixels(pixels, 0, 64, 0, 0, 64, 128)
        val result = BackdropBlurTransformation(128, 64, 3).transform(input, Size(128, 64))
        assertEquals(Color.GREEN, result.getPixel(0, 0))
        assertEquals(Color.GREEN, result.getPixel(64, 32))
        assertEquals(Color.GREEN, result.getPixel(127, 63))
    }

    @Test
    fun fineDetailIsBlurredAndTransparentPixelsDoNotAddAColorFringe() = runBlocking {
        val input = createBitmap(128, 128)
        val pixels = IntArray(128 * 128) { index ->
            if ((index / 128 + index % 128) % 2 == 0) Color.WHITE else Color.BLACK
        }
        input.setPixels(pixels, 0, 128, 0, 0, 128, 128)
        val result = BackdropBlurTransformation(128, 128, 4).transform(input, Size(128, 128))
        assertTrue(Color.red(result.getPixel(64, 64)) in 110..145)
        assertTrue(kotlin.math.abs(Color.red(result.getPixel(64, 64)) - Color.red(result.getPixel(65, 64))) < 10)

        input.eraseColor(Color.TRANSPARENT)
        input.setPixel(64, 64, Color.RED)
        val transparent = BackdropBlurTransformation(128, 128, 1).transform(input, Size(128, 128))
        val center = transparent.getPixel(64, 64)
        assertTrue(Color.alpha(center) in 1..254)
        assertEquals(255, Color.red(center))
        assertEquals(0, Color.green(center))
        assertEquals(0, Color.blue(center))
    }

    @Test
    fun blurredCacheEntriesCannotReplaceSharpCovers() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val game = Game(id = -900001, title = "Backdrop test", fileName = "test.nes",
            fileUri = "benchmark://backdrop/test", systemId = "nes", developer = null,
            coverFrontUrl = null, lastIndexedAt = 0)
        val first = backdropRequest(context, game, 128, 64, 4)
        assertNotEquals(CoverUtils.coverCacheKey(game), first.memoryCacheKey)
        assertNotEquals(first.memoryCacheKey, backdropRequest(context, game, 64, 128, 4).memoryCacheKey)
        assertNotEquals(first.memoryCacheKey, backdropRequest(context, game, 128, 64, 5).memoryCacheKey)
    }

    @Test
    fun uploadedCoverAndItsReplacementProduceFreshCachedBackdrops() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = CustomCoverManager(context)
        val loader = ImageLoader.Builder(context).build()
        val source = File.createTempFile("backdrop_upload_", ".png", context.cacheDir)
        val game = Game(id = -900002, title = "Custom backdrop test", fileName = "test.nes",
            fileUri = "benchmark://backdrop/upload", systemId = "nes", developer = null,
            coverFrontUrl = "https://example.invalid/original.jpg", lastIndexedAt = 0)
        try {
            fun writeSource(color: Int) {
                val bitmap = createBitmap(256, 256).apply { eraseColor(color) }
                try {
                    source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally {
                    bitmap.recycle()
                }
            }

            writeSource(Color.RED)
            val firstPath = manager.saveCustomCover(game.id, Uri.fromFile(source))
            val firstGame = game.copy(customCoverPath = firstPath)
            val firstRequest = backdropRequest(context, firstGame, 128, 64, 4)
            assertEquals(manager.coverFile(game.id), firstRequest.data)
            val first = loader.execute(firstRequest) as SuccessResult
            val firstBitmap = (first.drawable as BitmapDrawable).bitmap
            assertEquals(128, firstBitmap.width)
            assertEquals(64, firstBitmap.height)
            assertTrue(Color.red(firstBitmap.getPixel(64, 32)) > 240)
            assertTrue(Color.blue(firstBitmap.getPixel(64, 32)) < 15)
            assertEquals(DataSource.MEMORY_CACHE, (loader.execute(firstRequest) as SuccessResult).dataSource)

            // The uploader versions its stored path with the file's modification time.
            delay(10)
            writeSource(Color.BLUE)
            val replacementPath = manager.saveCustomCover(game.id, Uri.fromFile(source))
            assertNotEquals(firstPath, replacementPath)
            val replacementGame = game.copy(customCoverPath = replacementPath)
            val replacementRequest = backdropRequest(context, replacementGame, 128, 64, 4)
            assertNotEquals(firstRequest.memoryCacheKey, replacementRequest.memoryCacheKey)
            val replacement = loader.execute(replacementRequest) as SuccessResult
            assertNotEquals(DataSource.MEMORY_CACHE, replacement.dataSource)
            val replacementBitmap = (replacement.drawable as BitmapDrawable).bitmap
            assertTrue(Color.blue(replacementBitmap.getPixel(64, 32)) > 240)
            assertTrue(Color.red(replacementBitmap.getPixel(64, 32)) < 15)
            assertEquals(DataSource.MEMORY_CACHE, (loader.execute(replacementRequest) as SuccessResult).dataSource)

            val sharpRequest = CoverUtils.coverRequest(context, replacementGame, size = Size(128, 64))
                .newBuilder().allowHardware(false).build()
            val sharp = loader.execute(sharpRequest) as SuccessResult
            assertNotEquals(replacement.memoryCacheKey, sharp.memoryCacheKey)
            assertTrue(Color.blue((sharp.drawable as BitmapDrawable).bitmap.getPixel(0, 0)) > 240)
        } finally {
            loader.shutdown()
            source.delete()
            manager.deleteCustomCover(game.id)
        }
    }
}
