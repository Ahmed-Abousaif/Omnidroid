package com.omnidroid.lib.saves

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StatesPreviewManager(
    private val directoriesManager: DirectoriesManager,
    private val customSaveStorage: CustomSaveStorage =
        CustomSaveStorage(directoriesManager.context, directoriesManager),
) {
    suspend fun getPreviewForSlot(
        game: Game,
        coreID: CoreID,
        index: Int,
        size: Int,
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            val screenshotName = getSlotScreenshotName(game, index)
            val bytes = readPreviewBytes(game, coreID, index, screenshotName) ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options =
                BitmapFactory.Options().apply {
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
        val targetPath = writePreviewPath(game, coreID, index, screenshotName)
        val success = customSaveStorage.writeSave(targetPath, bytes)
        if (success &&
            customSaveStorage.isConfigured() &&
            !customSaveStorage.isStashedLocally(targetPath) &&
            customSaveStorage.getSaveInfo(targetPath)?.exists == true
        ) {
            val legacyPath = "state-previews/${coreID.coreName}/$screenshotName"
            if (targetPath != legacyPath &&
                customSaveStorage.getSaveInfo(legacyPath)?.exists == true &&
                customSaveStorage.canSafelyDeleteLegacyState(coreID.coreName, game.fileName, game.systemId)
            ) {
                customSaveStorage.deleteSave(legacyPath)
            }
        }
    }

    private fun readPreviewBytes(
        game: Game,
        coreID: CoreID,
        index: Int,
        screenshotName: String,
    ): ByteArray? {
        val consoleFolder = SaveConsoleFolders.forSystemId(game.systemId)
        val consolePath = "state-previews/$consoleFolder/${coreID.coreName}/$screenshotName"
        val corePath = "state-previews/${coreID.coreName}/$screenshotName"

        if (customSaveStorage.isConfigured()) {
            customSaveStorage.readSave(consolePath)?.takeIf { it.isNotEmpty() }?.let { return it }
            // Avoid mixing a legacy preview onto a slot that was already saved under the console folder
            val consoleStatePath = "states/$consoleFolder/${coreID.coreName}/${game.fileName}.slot${index + 1}"
            val stateMovedToConsole =
                coreID != CoreID.ARMSX3 &&
                    customSaveStorage.getSaveInfo(consoleStatePath)?.exists == true
            if (stateMovedToConsole) return null
            return customSaveStorage.readSave(corePath)?.takeIf { it.isNotEmpty() }
        }

        return customSaveStorage.readSave(corePath)?.takeIf { it.isNotEmpty() }
            ?: customSaveStorage.readSave(consolePath)?.takeIf { it.isNotEmpty() }
    }

    private fun writePreviewPath(
        game: Game,
        coreID: CoreID,
        index: Int,
        screenshotName: String,
    ): String {
        val consoleFolder = SaveConsoleFolders.forSystemId(game.systemId)
        val consolePath = "state-previews/$consoleFolder/${coreID.coreName}/$screenshotName"
        if (customSaveStorage.isConfigured()) {
            return consolePath
        }
        val consoleStatePath = "states/$consoleFolder/${coreID.coreName}/${game.fileName}.slot${index + 1}"
        if (customSaveStorage.getSaveInfo(consolePath)?.exists == true ||
            customSaveStorage.getSaveInfo(consoleStatePath)?.exists == true
        ) {
            return consolePath
        }
        return "state-previews/${coreID.coreName}/$screenshotName"
    }

    private fun getSlotScreenshotName(
        game: Game,
        index: Int,
    ) = "${game.fileName}.slot${index + 1}.jpg"

    companion object {
        val PREVIEW_SIZE_DP = 96f
    }
}
