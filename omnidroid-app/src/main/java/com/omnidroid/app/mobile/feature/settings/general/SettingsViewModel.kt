package com.omnidroid.app.mobile.feature.settings.general

import android.content.Context
import android.text.format.Formatter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.omnidroid.app.shared.library.PendingOperationsMonitor
import com.omnidroid.app.shared.settings.SettingsInteractor
import com.omnidroid.lib.saves.CustomSaveStorage
import com.omnidroid.lib.savesync.SaveSyncManager
import com.omnidroid.lib.storage.cache.CacheCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(
                context,
                settingsInteractor,
                saveSyncManager,
                sharedPreferences,
            ) as T
    }

    data class CacheState(
        val default: String,
        val values: List<String>,
        val displayNames: List<String>,
    )

    data class State(
        val currentDirectory: String = "",
        val customSavesDirectory: String = "",
        val isSaveSyncSupported: Boolean = false,
        val isMigrating: Boolean = false,
        val migrationError: Boolean = false,
    )

    private val operations = PendingOperationsMonitor(context)

    val indexingInProgress = operations.anyLibraryOperationInProgress()

    val directoryScanInProgress = operations.isDirectoryScanInProgress()

    private val isMigrating = MutableStateFlow(false)
    private val migrationError = MutableStateFlow(false)

    val uiState =
        combine(
            sharedPreferences.getString(
                context.getString(com.omnidroid.lib.R.string.pref_key_extenral_folder),
                "",
            ).asFlow(),
            sharedPreferences.getString(
                context.getString(com.omnidroid.lib.R.string.pref_key_custom_saves_folder),
                "",
            ).asFlow(),
            isMigrating,
            migrationError,
        ) { romsDir, savesDir, migrating, error ->
            State(
                currentDirectory = romsDir,
                customSavesDirectory = savesDir,
                isSaveSyncSupported = saveSyncManager.isSupported(),
                isMigrating = migrating,
                migrationError = error,
            )
        }
            .flowOn(Dispatchers.IO)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                State(isSaveSyncSupported = saveSyncManager.isSupported()),
            )

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

    fun changeCustomSavesFolder() {
        settingsInteractor.changeCustomSavesFolder()
    }

    fun resetCustomSavesFolder() {
        viewModelScope.launch {
            isMigrating.value = true
            val result =
                withContext(Dispatchers.IO) {
                    settingsInteractor.resetCustomSavesFolder()
                }
            isMigrating.value = false
            if (result is CustomSaveStorage.MigrationResult.Failure) {
                migrationError.value = true
            }
        }
    }

    fun dismissMigrationError() {
        migrationError.value = false
    }

    fun resetAllSettings() {
        settingsInteractor.resetAllSettings()
    }
}
