package com.omnidroid.lib.saves

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class StatesPreviewManager(
    private val directoriesManager: DirectoriesManager,
    private val customSaveStorage: CustomSaveStorage = CustomSaveStorage(directoriesManager.context),
) {
    suspend fun getPreviewForSlot(
        game: Game,
        coreID: CoreID,
        index: Int,
        size: Int,
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            val screenshotName = getSlotScreenshotName(game, index)
            val customPath = "state-previews/${coreID.coreName}/$screenshotName"
            val bytes = customSaveStorage.readSave(customPath) ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = BitmapFactory.Options().apply {
                var sample = 1
                while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
                inSampleSize = sample
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@withContext null
            ThumbnailUtils.extractThumbnail(bitmap, size, size, ThumbnailUtils.OPTIONS_RECYCLE_INPUT)
        }

    suspend fun setPreviewForSlot(
        game: Game,
        bitmap: Bitmap,
        coreID: CoreID,
        index: Int,
    ) = withContext(Dispatchers.IO) {
        val screenshotName = getSlotScreenshotName(game, index)
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val bytes = stream.toByteArray()
        customSaveStorage.writeSave(
            "state-previews/${coreID.coreName}/$screenshotName",
            bytes,
        )
    }

    private fun getSlotScreenshotName(
        game: Game,
        index: Int,
    ) = "${game.fileName}.slot${index + 1}.jpg"

    companion object {
        val PREVIEW_SIZE_DP = 96f
    }
}
