package com.swordfish.lemuroid.app.mobile.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.lemuroid.app.shared.settings.StorageFrameworkPickerLauncher
import com.swordfish.lemuroid.common.paging.buildFlowPaging
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.MetaSystemID
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.library.metaSystemID
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class LibraryViewModel(
    private val appContext: Context,
    private val retrogradeDb: RetrogradeDatabase,
    private val registeredSystemsStore: RegisteredSystemsStore,
) : ViewModel() {
    class Factory(
        private val appContext: Context,
        private val retrogradeDb: RetrogradeDatabase,
        private val registeredSystemsStore: RegisteredSystemsStore,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryViewModel(appContext, retrogradeDb, registeredSystemsStore) as T
        }
    }

    data class UiState(
        val filter: LibraryFilter = LibraryFilter.All,
        val searchQuery: String = "",
        val sidebarSystems: List<MetaSystemID> = emptyList(),
        val continueGame: Game? = null,
        val operationInProgress: Boolean = false,
        val zoomDensity: Int = 0,
        val selectedSystemGameCount: Int? = null,
    )

    private val preferences = SharedPreferencesHelper.getSharedPreferences(appContext)
    private val filterFlow = MutableStateFlow<LibraryFilter>(LibraryFilter.All)
    private val searchQueryFlow = MutableStateFlow("")
    private val scanRequested = MutableStateFlow(false)
    private val zoomDensityFlow = MutableStateFlow(readZoomDensity())
    private val libraryOperationInProgress =
        PendingOperationsMonitor(appContext).anyLibraryOperationInProgress()

    val state: StateFlow<UiState> =
        combine(
            filterFlow,
            searchQueryFlow,
            sidebarSystems(),
            continueGame(),
            combine(scanRequested, libraryOperationInProgress, zoomDensityFlow, retrogradeDb.gameDao().selectSystemsWithCount()) { requested, inProgress, zoom, counts ->
                Triple(requested || inProgress, zoom, counts)
            },
        ) { filter, searchQuery, sidebarSystems, continueGames, operation ->
            val selectedCount =
                (filter as? LibraryFilter.System)?.let { system ->
                    val ids = system.metaSystemID.systemIDs.map { it.dbname }.toSet()
                    operation.third.filter { it.systemId in ids }.sumOf { it.count }
                }
            UiState(
                filter = filter,
                searchQuery = searchQuery,
                sidebarSystems = sidebarSystems,
                continueGame = continueGames.firstOrNull(),
                operationInProgress = operation.first,
                zoomDensity = operation.second,
                selectedSystemGameCount = selectedCount,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            UiState(zoomDensity = zoomDensityFlow.value),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val games: Flow<PagingData<Game>> =
        combine(filterFlow, searchQueryFlow) { filter, query -> filter to query.trim() }
            .flatMapLatest { (filter, query) ->
                buildFlowPaging(PAGE_SIZE, viewModelScope) {
                    gamesPagingSource(filter, query)
                }
            }

    fun selectFilter(filter: LibraryFilter) {
        filterFlow.value = filter
    }

    fun cycleFilter(delta: Int) {
        val systems = state.value.sidebarSystems
        val items =
            buildList {
                add(LibraryFilter.Favorites)
                add(LibraryFilter.All)
                systems.forEach { add(LibraryFilter.System(it)) }
            }
        if (items.isEmpty()) return
        val current = items.indexOfFirst { it == state.value.filter }.let { index ->
            if (index < 0) 1 else index
        }
        selectFilter(items[(current + delta).mod(items.size)])
    }

    fun changeQueryString(query: String) {
        searchQueryFlow.value = query
    }

    fun clearSearchQuery() {
        searchQueryFlow.value = ""
    }

    fun resetHomeUi() {
        clearSearchQuery()
        filterFlow.value = LibraryFilter.All
    }

    fun setZoomDensity(density: Int) {
        val value = density.coerceIn(0, MAX_ZOOM_DENSITY)
        zoomDensityFlow.value = value
        preferences.edit().putInt(ZOOM_DENSITY_KEY, value).apply()
    }

    private fun readZoomDensity(): Int {
        return preferences.getInt(ZOOM_DENSITY_KEY, 0).coerceIn(0, MAX_ZOOM_DENSITY)
    }

    fun zoomIn() {
        setZoomDensity(zoomDensityFlow.value - 1)
    }

    fun zoomOut() {
        setZoomDensity(zoomDensityFlow.value + 1)
    }

    fun syncLibrary(context: Context) {
        if (scanRequested.value || state.value.operationInProgress) return

        val folderKey = context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder)
        val folder =
            SharedPreferencesHelper.getLegacySharedPreferences(context)
                .getString(folderKey, null)

        if (folder.isNullOrBlank()) {
            StorageFrameworkPickerLauncher.pickFolder(context)
            return
        }

        viewModelScope.launch {
            scanRequested.value = true
            try {
                LibraryIndexScheduler.scheduleLibrarySync(context.applicationContext)
                val started = withTimeoutOrNull(3_000) { libraryOperationInProgress.first { it } }
                if (started == true) {
                    libraryOperationInProgress.first { !it }
                }
            } finally {
                scanRequested.value = false
            }
        }
    }

    private fun sidebarSystems(): Flow<List<MetaSystemID>> {
        return combine(
            registeredSystemsStore.observe(),
            retrogradeDb.gameDao().selectSystemsWithCount(),
        ) { registered, counts ->
            val withGames =
                counts.mapNotNull { count ->
                    if (count.count <= 0) return@mapNotNull null
                    runCatching { GameSystem.findById(count.systemId).metaSystemID() }.getOrNull()
                }.toSet()

            (registered + withGames)
                .distinct()
                .sortedBy { appContext.getString(it.titleResId) }
        }
    }

    private fun continueGame(): Flow<List<Game>> = retrogradeDb.gameDao().selectLastPlayed()

    private fun gamesPagingSource(
        filter: LibraryFilter,
        query: String,
    ) = when {
        query.isNotEmpty() && filter is LibraryFilter.Favorites ->
            retrogradeDb.gameDao().searchFavoritesByTitle(query)
        query.isNotEmpty() && filter is LibraryFilter.System ->
            retrogradeDb.gameDao().searchBySystemsAndTitle(filter.systemIds(), query)
        query.isNotEmpty() ->
            retrogradeDb.gameDao().searchByTitle(query)
        filter is LibraryFilter.Favorites ->
            retrogradeDb.gameDao().selectFavorites()
        filter is LibraryFilter.System -> {
            val systemIds = filter.systemIds()
            if (systemIds.size == 1) {
                retrogradeDb.gameDao().selectBySystem(systemIds.first())
            } else {
                retrogradeDb.gameDao().selectBySystems(systemIds)
            }
        }
        else -> retrogradeDb.gameDao().selectAllPaged()
    }

    private fun LibraryFilter.System.systemIds() = metaSystemID.systemIDs.map { it.dbname }

    companion object {
        private const val PAGE_SIZE = 20
        private const val ZOOM_DENSITY_KEY = "pref_library_zoom_density"
        const val MAX_ZOOM_DENSITY = 2
    }
}
