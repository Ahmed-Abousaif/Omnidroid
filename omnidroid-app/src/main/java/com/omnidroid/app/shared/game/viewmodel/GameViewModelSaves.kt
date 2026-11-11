package com.omnidroid.app.shared.game.viewmodel

import android.content.Context
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.settings.SettingsManager
import com.omnidroid.app.shared.game.view.takeScreenshot
import com.omnidroid.common.graphics.GraphicsUtils
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.SystemCoreConfig
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.saves.IncompatibleStateException
import com.omnidroid.lib.saves.SaveState
import com.omnidroid.lib.saves.SavesManager
import com.omnidroid.lib.saves.StatesManager
import com.omnidroid.lib.saves.StatesPreviewManager
import com.omnidroid.libretrodroid.GLRetroView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import kotlin.math.roundToInt

class GameViewModelSaves(
    private val appContext: Context,
    private val system: GameSystem,
    private val game: Game,
    private val systemCoreConfig: SystemCoreConfig,
    private val retroGameView: GameViewModelRetroGameView,
    private val settingsManager: SettingsManager,
    private val savesManager: SavesManager,
    private val statesManager: StatesManager,
    private val statesPreviewManager: StatesPreviewManager,
    private val sideEffects: GameViewModelSideEffects,
) {
    private var currentQuickSave: SaveState? = null

    @Volatile private var preserveAutoSave = false

    data class SaveSnapshot(
        val sram: ByteArray?,
        val autoSave: SaveState?,
        val captureFailed: Boolean = false,
    )

    suspend fun saveSlot(index: Int) {
        saveManualState {
            statesManager.setSlotSave(game, it, systemCoreConfig.coreID, index)
            index
        }
    }

    suspend fun saveNewSlot() {
        saveManualState { statesManager.setNewSlotSave(game, systemCoreConfig.coreID, it) }
    }

    private suspend fun saveManualState(write: suspend (SaveState) -> Int) {
        try {
            val state = getCurrentSaveState() ?: throw IOException("Core did not capture a state")
            val index = write(state)
            try {
                takeScreenshotPreview(index)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "State saved, but preview failed")
            }
            sideEffects.showToast(appContext.getString(R.string.game_toast_save_state_saved))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showSaveError(e)
        }
    }

    suspend fun loadSlot(index: Int) {
        try {
            val state =
                statesManager.getSlotSave(game, systemCoreConfig.coreID, index)
                    ?: throw IOException("Save slot is missing")
            withContext(Dispatchers.IO) {
                if (!loadSaveState(state)) throw IOException("Core rejected state")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showLoadError(e)
        }
    }

    /** An explicit load remains available even when automatic saving/resuming is disabled. */
    suspend fun loadAutoSave() {
        try {
            val state =
                statesManager.getAutoSave(game, systemCoreConfig.coreID)
                    ?: throw IOException("Autosave is missing")
            withContext(Dispatchers.IO) { restoreQuickSave(state) }
            preserveAutoSave = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onAutoSaveLoadFailed(e)
        }
    }

    suspend fun captureSaveSnapshot(useEmulationThread: Boolean): SaveSnapshot? {
        val retroGameView = retroGameView.retroGameView ?: return null
        var failed = false

        fun capture(block: () -> ByteArray?): ByteArray? =
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Save capture failed")
                failed = true
                null
            }
        val sramState = capture { retroGameView.serializeSRAM(useEmulationThread) }
        var autoSaveState: SaveState? = null
        if (isAutoSaveEnabled() && !preserveAutoSave) {
            capture {
                val state =
                    getCurrentSaveState(useEmulationThread)
                        ?: throw IOException("Core did not capture autosave")
                autoSaveState = state
                state.state
            }
        }
        return SaveSnapshot(sramState, autoSaveState, failed)
    }

    suspend fun writeSaveSnapshot(
        snapshot: SaveSnapshot?,
        notifyFailure: Boolean = true,
    ): Boolean {
        if (snapshot == null) {
            if (notifyFailure) showSaveError(IOException("No running game to save"))
            return false
        }
        Timber.i(
            "GameViewModelSaves.write game=%s core=%s writingSram=%s writingAutoSave=%s",
            game.id,
            systemCoreConfig.coreID,
            snapshot.sram != null,
            snapshot.autoSave != null,
        )
        var failed = snapshot.captureFailed

        suspend fun write(block: suspend () -> Unit) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Save write failed")
                failed = true
            }
        }
        // Attempt both so a failure in one does not prevent saving the other.
        snapshot.sram?.takeIf { it.isNotEmpty() }?.let { write { savesManager.setSaveRAM(game, it) } }
        if (!preserveAutoSave) {
            snapshot.autoSave?.let { write { statesManager.setAutoSave(game, systemCoreConfig.coreID, it) } }
        }
        if (failed && notifyFailure) showSaveError(IOException("Could not save all game progress"))
        return !failed
    }

    // On some cores unserialize fails with no reason. So we need to try multiple times.
    suspend fun restoreAutoSaveAsync(saveState: SaveState) {
        // PPSSPP and Mupen64 initialize some state while rendering the first frame, so we have to wait before restoring
        // the autosave. Do not change thread here. Stick to the GL one to avoid issues with PPSSPP.
        if (!isAutoSaveEnabled()) return

        preserveAutoSave = true
        try {
            retroGameView.waitGLEvent<GLRetroView.GLRetroEvents.FrameRendered>()
            restoreQuickSave(saveState)
            preserveAutoSave = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onAutoSaveLoadFailed(e)
        }
    }

    fun onAutoSaveLoadFailed(error: Exception) {
        preserveAutoSave = true
        Timber.e(error, "Error while loading autosave; preserving existing file")
        val message =
            if (error is IncompatibleStateException) {
                R.string.game_toast_autosave_incompatible
            } else {
                R.string.game_toast_autosave_load_failed
            }
        sideEffects.showToast(appContext.getString(message))
    }

    private fun showSaveError(error: Exception) {
        Timber.e(error, "Could not save state")
        sideEffects.showToast(appContext.getString(R.string.game_toast_save_state_failed))
    }

    private fun showLoadError(error: Exception) {
        Timber.e(error, "Could not load state")
        val message =
            if (error is IncompatibleStateException) {
                R.string.error_message_incompatible_state
            } else {
                R.string.game_toast_load_state_failed
            }
        sideEffects.showToast(appContext.getString(message))
    }

    private fun getCurrentSaveState(useEmulationThread: Boolean = true): SaveState? {
        val retroGameView = retroGameView.retroGameView ?: return null
        val currentDisk =
            if (system.hasMultiDiskSupport) {
                retroGameView.getCurrentDisk(useEmulationThread)
            } else {
                0
            }
        val stateBytes = retroGameView.serializeState(useEmulationThread)?.takeIf { it.isNotEmpty() } ?: return null
        return SaveState(
            stateBytes,
            SaveState.Metadata(currentDisk, systemCoreConfig.statesVersion),
        )
    }

    private suspend fun isAutoSaveEnabled(): Boolean {
        return systemCoreConfig.statesSupported && settingsManager.autoSave()
    }

    private suspend fun takeScreenshotPreview(index: Int) {
        val sizeInDp = StatesPreviewManager.PREVIEW_SIZE_DP
        val previewSize = GraphicsUtils.convertDpToPixel(sizeInDp, appContext).roundToInt()
        val preview = retroGameView.retroGameView?.takeScreenshot(previewSize, 3)
        if (preview != null) {
            statesPreviewManager.setPreviewForSlot(game, preview, systemCoreConfig.coreID, index)
        }
    }

    private suspend fun restoreQuickSave(saveState: SaveState) {
        restoreSaveStateWithRetry { loadSaveState(saveState) }
    }

    private fun loadSaveState(saveState: SaveState): Boolean {
        val retroGameView = retroGameView.retroGameView ?: return false

        if (systemCoreConfig.statesVersion != saveState.metadata.version) {
            throw IncompatibleStateException()
        }

        if (system.hasMultiDiskSupport &&
            retroGameView.getAvailableDisks() > 1 &&
            retroGameView.getCurrentDisk() != saveState.metadata.diskIndex
        ) {
            retroGameView.changeDisk(saveState.metadata.diskIndex)
        }

        return retroGameView.unserializeState(saveState.state)
    }

    fun saveQuickSave() {
        try {
            val state = getCurrentSaveState() ?: throw IOException("Core did not capture quick save")
            currentQuickSave = state
            sideEffects.showToast(appContext.getString(R.string.game_toast_quick_save_saved))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showSaveError(e)
        }
    }

    fun loadQuickSave() {
        val state = currentQuickSave
        if (state == null) {
            sideEffects.showToast(appContext.getString(R.string.game_toast_quick_save_missing))
            return
        }
        try {
            if (!loadSaveState(state)) throw IOException("Core rejected quick save")
            sideEffects.showToast(appContext.getString(R.string.game_toast_quick_save_loaded))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showLoadError(e)
        }
    }
}
