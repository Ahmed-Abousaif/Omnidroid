package com.omnidroid.app.shared.library

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.omnidroid.app.mobile.shared.NotificationsManager
import com.omnidroid.app.shared.covers.CustomCoverManager
import com.omnidroid.app.shared.switch_emu.EdenBridge
import com.omnidroid.app.utils.android.createSyncForegroundInfo
import com.omnidroid.lib.core.CoreLibraryLocator
import com.omnidroid.lib.core.SyncProgressTracker
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.SystemID
import com.omnidroid.lib.library.db.RetrogradeDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.utils.FileUtil
import org.yuzu.yuzu_emu.utils.GameMetadata
import timber.log.Timber

@HiltWorker
class SwitchIconExtractionWork
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val retrogradeDatabase: RetrogradeDatabase,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val coreFile = CoreLibraryLocator.find(applicationContext, CoreID.SWITCH)
        if (coreFile == null) {
            Timber.i("SwitchIconExtractionWork: Eden core not installed, skipping icon extraction")
            return Result.success()
        }

        val switchGames = retrogradeDatabase.gameDao()
            .selectBySystemMissingCustomCover(SystemID.SWITCH.dbname)

        if (switchGames.isEmpty()) {
            Timber.i("SwitchIconExtractionWork: No Switch games missing custom cover")
            return Result.success()
        }

        Timber.i("SwitchIconExtractionWork: Found %d Switch games needing cover extraction", switchGames.size)

        val notificationsManager = NotificationsManager(applicationContext)
        val foregroundInfo =
            createSyncForegroundInfo(
                NotificationsManager.SWITCH_ICON_EXTRACTION_NOTIFICATION_ID,
                notificationsManager.switchIconExtractionNotification(),
            )
        setForegroundAsync(foregroundInfo)

        withContext(Dispatchers.IO) {
            try {
                FileUtil.init(applicationContext)
                if (!NativeLibrary.isLoaded) {
                    EdenBridge.initialize(applicationContext, coreFile)
                }

                if (!NativeLibrary.isLoaded) {
                    Timber.w("SwitchIconExtractionWork: Eden native library failed to load")
                    return@withContext
                }

                val coverManager = CustomCoverManager(applicationContext)

                for ((index, game) in switchGames.withIndex()) {
                    SyncProgressTracker.update(
                        stepText = "Extracting icon: ${game.displayName}",
                        current = index + 1,
                        total = switchGames.size,
                        isIndeterminate = false,
                    )

                    try {
                        val bytes = runCatching {
                            GameMetadata.getIcon(game.fileUri)
                        }.getOrNull()

                        if (bytes != null && bytes.isNotEmpty()) {
                            val path = coverManager.saveFromBytes(game.id, bytes)
                            val updated = game.copy(customCoverPath = path)
                            retrogradeDatabase.gameDao().update(updated)
                            Timber.i("SwitchIconExtractionWork: Extracted cover for '${game.title}' -> $path")
                        } else {
                            Timber.d("SwitchIconExtractionWork: No icon in ROM for '${game.title}'")
                        }
                    } catch (e: Throwable) {
                        Timber.w(e, "SwitchIconExtractionWork: Failed to extract icon for '${game.title}'")
                    }
                }
            } catch (e: Throwable) {
                Timber.e(e, "SwitchIconExtractionWork failed with exception")
            } finally {
                SyncProgressTracker.clear()
            }
        }

        return Result.success()
    }
}
