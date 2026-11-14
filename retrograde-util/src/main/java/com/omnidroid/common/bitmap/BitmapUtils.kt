package com.omnidroid.common.bitmap

import android.graphics.Bitmap

fun Bitmap.cropToSquare(): Bitmap {
    val size = minOf(width, height)
    val cropW = ((width - height) / 2).coerceAtLeast(0)
    val cropH = ((height - width) / 2).coerceAtLeast(0)
    return Bitmap.createBitmap(this, cropW, cropH, size, size)
}
