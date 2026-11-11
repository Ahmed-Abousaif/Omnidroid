package com.omnidroid.app.mobile.feature.gamemenu.states

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.gamemenu.GameMenuRequest
import com.omnidroid.app.shared.gamemenu.GameMenuHelper
import com.omnidroid.lib.saves.StatesManager
import com.omnidroid.lib.saves.StatesPreviewManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import timber.log.Timber

class GameMenuStatesViewModel(
    private val application: Application,
    private val gameMenuRequest: GameMenuRequest,
    private val statesManager: StatesManager,
    private val disableMissingEntries: Boolean,
    private val statesPreviewManager: StatesPreviewManager,
) : ViewModel() {
    class Factory(
        private val application: Application,
        private val gameMenuRequest: GameMenuRequest,
        private val statesManager: StatesManager,
        private val disableMissingEntries: Boolean,
        private val statesPreviewManager: StatesPreviewManager,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GameMenuStatesViewModel(
                application,
                gameMenuRequest,
                statesManager,
                disableMissingEntries,
                statesPreviewManager,
            ) as T
        }
    }

    data class StateEntry(
        val slot: Int,
        val title: String,
        val description: String,
        val enabled: Boolean,
        val preview: Bitmap?,
    )

    data class AutoSaveEntry(val description: String, val enabled: Boolean)

    data class State(
        val entries: List<StateEntry> = emptyList(),
        val autoSave: AutoSaveEntry? = null,
        val canAddSlot: Boolean = false,
        val loadFailed: Boolean = false,
    )

    private val refresh = MutableStateFlow(0)

    fun retry() {
        refresh.update { it + 1 }
    }

    val uiStates =
        refresh.map {
            try {
                val slotsInfo = statesManager.getSavedSlotsInfo(gameMenuRequest.game, gameMenuRequest.coreConfig.coreID)

                val entries =
                    slotsInfo.map { (index, slotInfo) ->
                        val title =
                            application.applicationContext.getString(
                                R.string.game_menu_state,
                                (index + 1).toString(),
                            )
                        val description = GameMenuHelper.getSaveStateDescription(slotInfo)
                        val isEnabled = !disableMissingEntries || slotInfo.exists
                        val preview =
                            runCatching {
                                GameMenuHelper.getSaveStateBitmap(
                                    application.applicationContext,
                                    statesPreviewManager,
                                    slotInfo,
                                    gameMenuRequest.game,
                                    gameMenuRequest.coreConfig.coreID,
                                    index,
                                )
                            }.getOrElse {
                                if (it is CancellationException) throw it
                                Timber.w(it, "Cannot load save preview")
                                null
                            }

                        StateEntry(index, title, description, isEnabled, preview)
                    }

                val autoSave =
                    if (disableMissingEntries) {
                        val info =
                            statesManager.getAutoSaveInfo(
                                gameMenuRequest.game,
                                gameMenuRequest.coreConfig.coreID,
                            )
                        AutoSaveEntry(GameMenuHelper.getSaveStateDescription(info), info.exists)
                    } else {
                        null
                    }
                State(entries, autoSave, canAddSlot = !disableMissingEntries)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Cannot list save states")
                State(loadFailed = true)
            }
        }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())
}
