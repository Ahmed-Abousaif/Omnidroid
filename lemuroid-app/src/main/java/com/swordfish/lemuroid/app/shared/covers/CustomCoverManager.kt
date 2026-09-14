package com.swordfish.lemuroid.app.shared.covers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.swordfish.lemuroid.common.bitmap.cropToSquare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.min

class CustomCoverManager(private val context: Context) {
    class UnreadableImageException : IOException("Unable to read image")

    suspend fun saveCustomCover(
        gameId: Int,
        source: Uri,
    ): String =
        withContext(Dispatchers.IO) {
            val temp = File.createTempFile("custom_cover_", ".img", context.cacheDir)
            try {
                copySourceToFile(source, temp)
                val processed = decodeAndProcess(temp)
                try {
                    val dest = coverFile(gameId)
                    dest.parentFile?.mkdirs()
                    val toWrite =
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                            processed.config == Bitmap.Config.HARDWARE
                        ) {
                            processed.copy(Bitmap.Config.ARGB_8888, false) ?: processed
                        } else {
                            processed
                        }
                    try {
                        FileOutputStream(dest).use { output ->
                            if (!toWrite.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                                throw UnreadableImageException()
                            }
                        }
                    } finally {
                        if (toWrite !== processed) {
                            toWrite.recycle()
                        }
                    }
                    dest.setLastModified(System.currentTimeMillis())
                    "${dest.absolutePath}?${dest.lastModified()}"
                } finally {
                    processed.recycle()
                }
            } finally {
                temp.delete()
            }
        }

    fun deleteCustomCover(
        gameId: Int,
        storedPath: String? = null,
    ) {
        val defaultFile = coverFile(gameId)
        defaultFile.delete()
        storedPath
            ?.let(::fileForStoredPath)
            ?.takeIf { it.absolutePath != defaultFile.absolutePath }
            ?.delete()
    }

    fun coverFile(gameId: Int): File = File(coversDirectory(), "$gameId.jpg")

    private fun coversDirectory(): File = File(context.filesDir, COVERS_DIR)

    private fun copySourceToFile(
        source: Uri,
        dest: File,
    ) {
        val input =
            context.contentResolver.openInputStream(source)
                ?: throw UnreadableImageException()
        input.use { stream ->
            dest.outputStream().use { output ->
                val copied = stream.copyTo(output)
                if (copied <= 0L) throw UnreadableImageException()
            }
        }
    }

    private fun decodeAndProcess(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw UnreadableImageException()
        }

        val decodeOptions =
            BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_SIZE)
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = true
            }
        val decoded =
            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                ?: throw UnreadableImageException()

        var current = decoded
        current = replaceBitmap(current, applyExifOrientation(file, current))
        current = replaceBitmap(current, current.cropToSquare())
        if (current.width > MAX_SIZE || current.height > MAX_SIZE) {
            current = replaceBitmap(current, Bitmap.createScaledBitmap(current, MAX_SIZE, MAX_SIZE, true))
        }
        return current
    }

    private fun replaceBitmap(
        previous: Bitmap,
        next: Bitmap,
    ): Bitmap {
        if (next !== previous && !previous.isRecycled) {
            previous.recycle()
        }
        return next
    }

    private fun applyExifOrientation(
        file: File,
        bitmap: Bitmap,
    ): Bitmap {
        val orientation =
            runCatching {
                ExifInterface(file.absolutePath)
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxSize: Int,
    ): Int {
        val minDim = min(width, height)
        var sample = 1
        while (minDim / (sample * 2) >= maxSize) {
            sample *= 2
        }
        return sample
    }

    companion object {
        const val COVERS_DIR = "custom_covers"
        const val MAX_SIZE = 512
        const val JPEG_QUALITY = 90

        fun fileForStoredPath(storedPath: String): File = File(storedPath.substringBefore('?'))
    }
}
