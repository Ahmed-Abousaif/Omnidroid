package com.omnidroid.app.shared.covers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import coil.request.ImageRequest
import coil.size.Scale
import coil.size.Size
import coil.transform.Transformation
import com.omnidroid.lib.library.db.entity.Game
import kotlin.math.max
import kotlin.math.roundToInt

internal fun backdropRequest(context: Context, game: Game, width: Int, height: Int, radius: Int): ImageRequest {
    val blur = BackdropBlurTransformation(width, height, radius)
    val cover = CoverUtils.coverRequest(context, game, size = Size(width, height))
    return cover.newBuilder()
        // Never let a blurred thumbnail replace the sharp cover in Coil's shared cache.
        .memoryCacheKey("backdrop:${cover.memoryCacheKey}:${blur.cacheKey}")
        .allowHardware(false)
        .scale(Scale.FILL)
        .transformations(blur)
        .crossfade(450)
        .build()
}

/** A small, cached bitmap replaces the live, screen-sized GPU blur on every Android version. */
internal class BackdropBlurTransformation(
    private val width: Int,
    private val height: Int,
    private val radius: Int,
) : Transformation {
    init {
        require(width in 1..128 && height in 1..128 && radius in 1..32)
    }

    override val cacheKey = "backdrop-blur-v1:$width:$height:$radius"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val output = createBitmap(width, height)
        val scale = max(width.toFloat() / input.width, height.toFloat() / input.height)
        val cropWidth = (width / scale).roundToInt().coerceIn(1, input.width)
        val cropHeight = (height / scale).roundToInt().coerceIn(1, input.height)
        val left = (input.width - cropWidth) / 2
        val top = (input.height - cropHeight) / 2
        Canvas(output).drawBitmap(input, Rect(left, top, left + cropWidth, top + cropHeight),
            Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))

        val pixels = IntArray(width * height)
        val scratch = IntArray(pixels.size)
        output.getPixels(pixels, 0, width, 0, 0, width, height)
        // Blur premultiplied channels so transparent custom covers do not acquire dark fringes.
        for (index in pixels.indices) {
            val color = pixels[index]
            val alpha = color ushr 24
            if (alpha != 255) {
                pixels[index] = (alpha shl 24) or
                    ((((color ushr 16 and 255) * alpha + 127) / 255) shl 16) or
                    ((((color ushr 8 and 255) * alpha + 127) / 255) shl 8) or
                    (((color and 255) * alpha + 127) / 255)
            }
        }
        // Three linear-time box passes approximate a Gaussian without a large convolution kernel.
        repeat(3) {
            blurPass(pixels, scratch, height, width, width, 1)
            blurPass(scratch, pixels, width, height, 1, width)
        }
        for (index in pixels.indices) {
            val color = pixels[index]
            val alpha = color ushr 24
            if (alpha in 1..254) {
                pixels[index] = (alpha shl 24) or
                    (((color ushr 16 and 255) * 255 / alpha).coerceAtMost(255) shl 16) or
                    (((color ushr 8 and 255) * 255 / alpha).coerceAtMost(255) shl 8) or
                    ((color and 255) * 255 / alpha).coerceAtMost(255)
            }
        }
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    /** Swap the strides to reuse the same sliding window for horizontal and vertical passes. */
    private fun blurPass(source: IntArray, target: IntArray, lines: Int, length: Int, lineStride: Int, pixelStride: Int) {
        val divisor = radius * 2 + 1
        for (line in 0 until lines) {
            val base = line * lineStride
            var alpha = 0
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -radius..radius) {
                val color = source[base + offset.coerceIn(0, length - 1) * pixelStride]
                alpha += color ushr 24
                red += color ushr 16 and 255
                green += color ushr 8 and 255
                blue += color and 255
            }
            for (position in 0 until length) {
                target[base + position * pixelStride] = ((alpha / divisor) shl 24) or
                    ((red / divisor) shl 16) or ((green / divisor) shl 8) or (blue / divisor)
                val leaving = source[base + (position - radius).coerceAtLeast(0) * pixelStride]
                val entering = source[base + (position + radius + 1).coerceAtMost(length - 1) * pixelStride]
                alpha += (entering ushr 24) - (leaving ushr 24)
                red += (entering ushr 16 and 255) - (leaving ushr 16 and 255)
                green += (entering ushr 8 and 255) - (leaving ushr 8 and 255)
                blue += (entering and 255) - (leaving and 255)
            }
        }
    }
}
