package com.omnidroid.app.shared.switch_emu

import android.content.Context
import com.omnidroid.app.shared.covers.CustomCoverManager
import com.omnidroid.lib.library.SwitchIconExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.utils.FileUtil
import org.yuzu.yuzu_emu.utils.GameMetadata
import timber.log.Timber

class EdenIconExtractor(private val context: Context) : SwitchIconExtractor {

    override suspend fun extractAndSave(gameId: Int, fileUri: String): String? =
        withContext(Dispatchers.IO) {
            try {
                FileUtil.init(context)
                if (!NativeLibrary.isLoaded) {
                    runCatching { EdenBridge.initialize(context) }
                }
                if (!NativeLibrary.isLoaded) {
                    Timber.d("EdenIconExtractor: Eden native library not loaded, skipping $fileUri")
                    return@withContext null
                }

                val targetPath = if (fileUri.startsWith("file://", ignoreCase = true)) {
                    android.net.Uri.parse(fileUri).path ?: fileUri.removePrefix("file://")
                } else {
                    fileUri
                }
                val bytes = runCatching { GameMetadata.getIcon(targetPath) }.getOrNull()
                if (bytes == null || bytes.isEmpty()) {
                    Timber.d("EdenIconExtractor: No icon data extracted for $fileUri")
                    return@withContext null
                }

                val savedPath = CustomCoverManager(context).saveFromBytes(gameId, bytes)
                Timber.i("EdenIconExtractor: Successfully saved cover for game $gameId to $savedPath")
                savedPath
            } catch (e: Throwable) {
                Timber.w(e, "EdenIconExtractor: Failed to extract and save icon for game $gameId ($fileUri)")
                null
            }
        }
}
