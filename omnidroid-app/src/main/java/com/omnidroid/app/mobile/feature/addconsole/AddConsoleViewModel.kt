package com.omnidroid.app.mobile.feature.addconsole

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omnidroid.app.mobile.feature.library.RegisteredSystemsStore
import com.omnidroid.lib.core.CoreUpdater
import com.omnidroid.lib.core.CoresSelection
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.MetaSystemID
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.metaSystemID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class AddConsoleViewModel(
    appContext: Context,
    private val retrogradeDb: RetrogradeDatabase,
    private val registeredSystemsStore: RegisteredSystemsStore,
    private val coreUpdater: CoreUpdater,
    private val coresSelection: CoresSelection,
) : ViewModel() {
    class Factory(
        private val appContext: Context,
        private val retrogradeDb: RetrogradeDatabase,
        private val registeredSystemsStore: RegisteredSystemsStore,
        private val coreUpdater: CoreUpdater,
        private val coresSelection: CoresSelection,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AddConsoleViewModel(
                appContext,
                retrogradeDb,
                registeredSystemsStore,
                coreUpdater,
                coresSelection,
            ) as T
        }
    }

    data class ConsoleItem(
        val metaSystemID: MetaSystemID,
        val installed: Boolean,
    )

    data class UiState(
        val consoles: List<ConsoleItem> = emptyList(),
        val installing: MetaSystemID? = null,
        val errorMessage: String? = null,
    )

    private val applicationContext = appContext.applicationContext
    private val installingFlow = MutableStateFlow<MetaSystemID?>(null)
    private val errorFlow = MutableStateFlow<String?>(null)

    val state: StateFlow<UiState> =
        combine(
            registeredSystemsStore.observe(),
            retrogradeDb.gameDao().selectSystemsWithCount(),
            installingFlow,
            errorFlow,
        ) { registered, counts, installing, errorMessage ->
            val withGames =
                counts.mapNotNull { count ->
                    if (count.count <= 0) return@mapNotNull null
                    runCatching { GameSystem.findById(count.systemId).metaSystemID() }.getOrNull()
                }.toSet()
            val installed = registered + withGames

            UiState(
                consoles =
                    MetaSystemID.values()
                        .map {
                            ConsoleItem(
                                metaSystemID = it,
                                installed = it in installed,
                            )
                        }
                        .sortedBy { applicationContext.getString(it.metaSystemID.titleResId) },
                installing = installing,
                errorMessage = errorMessage,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun install(metaSystemID: MetaSystemID) {
        if (installingFlow.value != null) return

        viewModelScope.launch {
            installingFlow.value = metaSystemID
            errorFlow.value = null
            try {
                val cores =
                    metaSystemID.systemIDs
                        .map { GameSystem.findById(it.dbname) }
                        .map { coresSelection.getCoreConfigForSystem(it).coreID }
                        .distinct()
                coreUpdater.downloadCores(applicationContext, cores)
                registeredSystemsStore.register(metaSystemID)
            } catch (error: Throwable) {
                Timber.e(error, "Failed to install console ${metaSystemID.name}")
                errorFlow.value = error.message
            } finally {
                installingFlow.value = null
            }
        }
    }

    fun consumeError() {
        errorFlow.value = null
    }
}
