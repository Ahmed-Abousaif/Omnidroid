package com.omnidroid.app.mobile.feature.library

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.omnidroid.app.shared.library.LibraryIndexScheduler
import com.omnidroid.app.shared.library.PendingOperationsMonitor
import com.omnidroid.app.shared.settings.StorageFrameworkPickerLauncher
import com.omnidroid.common.paging.buildFlowPaging
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.MetaSystemID
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.dao.SystemCount
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.library.metaSystemID
import com.omnidroid.lib.preferences.SharedPreferencesHelper
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
        val layout: LibraryLayout = LibraryLayout.GRID,
        val portrait: Boolean = false,
        val selectedSystemGameCount: Int? = null,
    )

    private data class LibraryChrome(
        val operationInProgress: Boolean,
        val zoomDensity: Int,
        val layout: LibraryLayout,
        val portrait: Boolean,
        val systemCounts: List<SystemCount>,
    )

    private data class LibraryPresentation(
        val zoomDensity: Int,
        val layout: LibraryLayout,
        val portrait: Boolean,
    )

    private val preferences = SharedPreferencesHelper.getSharedPreferences(appContext)
    private val filterFlow = MutableStateFlow<LibraryFilter>(LibraryFilter.All)
    private val searchQueryFlow = MutableStateFlow("")
    private val scanRequested = MutableStateFlow(false)
    private val zoomDensityFlow = MutableStateFlow(readZoomDensity())
    private val layoutFlow = MutableStateFlow(readLayout())
    private val portraitFlow = MutableStateFlow(readPortrait())
    private val presentationFlow =
        combine(zoomDensityFlow, layoutFlow, portraitFlow) { zoom, layout, portrait ->
            LibraryPresentation(zoom, layout, portrait)
        }
    private val libraryOperationInProgress =
        PendingOperationsMonitor(appContext).anyLibraryOperationInProgress()

    val state: StateFlow<UiState> =
        combine(
            filterFlow,
            searchQueryFlow,
            sidebarSystems(),
            continueGame(),
            combine(
                scanRequested,
                libraryOperationInProgress,
                presentationFlow,
                retrogradeDb.gameDao().selectSystemsWithCount(),
            ) { requested, inProgress, presentation, counts ->
                LibraryChrome(
                    requested || inProgress,
                    presentation.zoomDensity,
                    presentation.layout,
                    presentation.portrait,
                    counts,
                )
            },
        ) { filter, searchQuery, sidebarSystems, continueGames, chrome ->
            val selectedCount =
                (filter as? LibraryFilter.System)?.let { system ->
                    val ids = system.metaSystemID.systemIDs.map { it.dbname }.toSet()
                    chrome.systemCounts.filter { it.systemId in ids }.sumOf { it.count }
                }
            UiState(
                filter = filter,
                searchQuery = searchQuery,
                sidebarSystems = sidebarSystems,
                continueGame = continueGames.firstOrNull(),
                operationInProgress = chrome.operationInProgress,
                zoomDensity = chrome.zoomDensity,
                layout = chrome.layout,
                portrait = chrome.portrait,
                selectedSystemGameCount = selectedCount,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            UiState(
                zoomDensity = zoomDensityFlow.value,
                layout = layoutFlow.value,
                portrait = portraitFlow.value,
            ),
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

    fun setLayout(layout: LibraryLayout) {
        layoutFlow.value = layout
        preferences.edit().putString(LAYOUT_KEY, layout.name).apply()
    }

    fun setPortrait(enabled: Boolean) {
        portraitFlow.value = enabled
        preferences.edit().putBoolean(PORTRAIT_KEY, enabled).apply()
    }

    private fun readZoomDensity(): Int {
        return preferences.getInt(ZOOM_DENSITY_KEY, 0).coerceIn(0, MAX_ZOOM_DENSITY)
    }

    private fun readLayout(): LibraryLayout {
        return when (preferences.getString(LAYOUT_KEY, LibraryLayout.GRID.name)) {
            LibraryLayout.LIST.name -> LibraryLayout.LIST
            else -> LibraryLayout.GRID
        }
    }

    private fun readPortrait(): Boolean = preferences.getBoolean(PORTRAIT_KEY, false)

    fun zoomIn() {
        if (layoutFlow.value != LibraryLayout.GRID) return
        setZoomDensity(zoomDensityFlow.value - 1)
    }

    fun zoomOut() {
        if (layoutFlow.value != LibraryLayout.GRID) return
        setZoomDensity(zoomDensityFlow.value + 1)
    }

    fun syncLibrary(context: Context) {
        if (scanRequested.value || state.value.operationInProgress) return

        val folderKey = context.getString(com.omnidroid.lib.R.string.pref_key_extenral_folder)
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
        private const val LAYOUT_KEY = "pref_library_layout"
        const val PORTRAIT_KEY = "pref_library_portrait"
        const val MAX_ZOOM_DENSITY = 2

        fun readSavedPortrait(context: Context): Boolean {
            return SharedPreferencesHelper.getSharedPreferences(context)
                .getBoolean(PORTRAIT_KEY, false)
        }

        fun orientationFor(portrait: Boolean): Int {
            return if (portrait) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }
}

enum class LibraryLayout {
    GRID,
    LIST,
}
