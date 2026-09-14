package com.swordfish.lemuroid.app.mobile.feature.settings.savesync

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.savesync.CloudSaveProviderInfo
import com.swordfish.lemuroid.lib.savesync.ConflictResolution
import com.swordfish.lemuroid.lib.savesync.SaveConflict
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SaveSyncSettingsViewModel(
    private val application: Application,
    private val saveSyncManager: SaveSyncManager,
) : ViewModel() {
    class Factory(
        private val application: Application,
        private val saveSyncManager: SaveSyncManager,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SaveSyncSettingsViewModel(application, saveSyncManager) as T
        }
    }

    val saveSyncInProgress = PendingOperationsMonitor(getContext()).anySaveOperationInProgress()

    data class State(
        val isConfigured: Boolean = false,
        val savesSpace: String = "",
        val lastSyncInfo: String = "",
        val coreNames: List<String> = emptyList(),
        val coreVisibleNames: List<String> = emptyList(),
        val providers: List<CloudSaveProviderInfo> = emptyList(),
        val conflicts: List<SaveConflict> = emptyList(),
    )

    private val refreshTick = MutableStateFlow(0)

    val uiState =
        flow {
            refreshTick.collect {
                emit(buildState())
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, State())

    fun refresh() {
        refreshTick.value += 1
    }

    fun signOut(providerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            saveSyncManager.signOut(providerId)
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    fun resolveConflict(
        conflictId: String,
        resolution: ConflictResolution,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            saveSyncManager.resolveConflict(conflictId, resolution)
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    private fun buildState(): State {
        return State(
            isConfigured = saveSyncManager.isConfigured(),
            savesSpace = saveSyncManager.computeSavesSpace(),
            lastSyncInfo = saveSyncManager.getLastSyncInfo(),
            coreNames = CoreID.values().map { it.coreName },
            coreVisibleNames = CoreID.values().map { saveSyncManager.getDisplayNameForCore(getContext(), it) },
            providers = saveSyncManager.getProviders(),
            conflicts = saveSyncManager.getConflicts(),
        )
    }

    private fun getContext(): Context = application.applicationContext
}
