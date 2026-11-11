package com.omnidroid.app.mobile.feature.settings.general

import android.content.Context
import android.text.format.Formatter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.omnidroid.app.shared.library.PendingOperationsMonitor
import com.omnidroid.app.shared.settings.SettingsInteractor
import com.omnidroid.lib.savesync.SaveSyncManager
import com.omnidroid.lib.storage.cache.CacheCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(
    context: Context,
    private val settingsInteractor: SettingsInteractor,
    saveSyncManager: SaveSyncManager,
    sharedPreferences: FlowSharedPreferences,
) : ViewModel() {
    class Factory(
        private val context: Context,
        private val settingsInteractor: SettingsInteractor,
        private val saveSyncManager: SaveSyncManager,
        private val sharedPreferences: FlowSharedPreferences,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                context,
                settingsInteractor,
                saveSyncManager,
                sharedPreferences,
            ) as T
        }
    }

    data class CacheState(
        val default: String,
        val values: List<String>,
        val displayNames: List<String>,
    )

    data class State(
        val currentDirectory: String = "",
        val isSaveSyncSupported: Boolean = false,
    )

    private val operations = PendingOperationsMonitor(context)

    val indexingInProgress = operations.anyLibraryOperationInProgress()

    val directoryScanInProgress = operations.isDirectoryScanInProgress()

    val uiState =
        sharedPreferences.getString(context.getString(com.omnidroid.lib.R.string.pref_key_extenral_folder))
            .asFlow()
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
            .map { State(it, saveSyncManager.isSupported()) }

    val cacheState =
        flow {
            val supportedCacheValues = CacheCleaner.getSupportedCacheLimits()
            emit(
                CacheState(
                    default = CacheCleaner.getDefaultCacheLimit().toString(),
                    values = supportedCacheValues.map { it.toString() },
                    displayNames = supportedCacheValues.map { Formatter.formatShortFileSize(context, it) },
                ),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun changeLocalStorageFolder() {
        settingsInteractor.changeLocalStorageFolder()
    }

    fun resetAllSettings() {
        settingsInteractor.resetAllSettings()
    }
}
