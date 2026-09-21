package com.omnidroid.common.graphics

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import com.omnidroid.common.kotlin.runCatchingWithRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt

suspend fun SurfaceView.takeScreenshot(
    maxResolution: Int,
    retries: Int = 1,
): Bitmap? =
    withContext(Dispatchers.Main) {
        runCatchingWithRetry(retries) {
            takeScreenshotInternal(maxResolution)
        }.getOrNull()
    }

private suspend fun SurfaceView.takeScreenshotInternal(maxResolution: Int): Bitmap? =
    suspendCoroutine { cont ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            cont.resume(null)
            return@suspendCoroutine
        }

        try {
            val w = width.coerceAtLeast(1)
            val h = height.coerceAtLeast(1)
            val outputScaling = maxResolution / maxOf(w, h).toFloat()
            val inputScaling = outputScaling * 2

            val inputBitmap =
                Bitmap.createBitmap(
                    (w * inputScaling).roundToInt().coerceAtLeast(1),
                    (h * inputScaling).roundToInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )

            val onCompleted = PixelCopy.OnPixelCopyFinishedListener { result: Int ->
                if (result == PixelCopy.SUCCESS) {
                    // This rescaling limits the artifacts introduced by shaders.
                    val outputBitmap =
                        Bitmap.createScaledBitmap(
                            inputBitmap,
                            (w * outputScaling).roundToInt().coerceAtLeast(1),
                            (h * outputScaling).roundToInt().coerceAtLeast(1),
                            true,
                        )

                    cont.resume(outputBitmap)
                } else {
                    cont.resumeWithException(RuntimeException("Cannot take screenshot. Error code: $result"))
                }
            }
            val mainHandler = handler ?: Handler(Looper.getMainLooper())
            PixelCopy.request(this, inputBitmap, onCompleted, mainHandler)
        } catch (e: Exception) {
            cont.resumeWithException(e)
        }
    }

