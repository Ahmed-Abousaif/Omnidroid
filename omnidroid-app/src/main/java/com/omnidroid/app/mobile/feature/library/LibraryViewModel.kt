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
import com.omnidroid.lib.core.SyncProgress
import com.omnidroid.lib.core.SyncProgressTracker
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.MetaSystemID
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.dao.GameOrder
import com.omnidroid.lib.library.db.dao.SystemCount
import com.omnidroid.lib.library.db.dao.selectPaged
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
    private val startupStore: LibraryStartupStore,
) : ViewModel() {
    class Factory(
        private val appContext: Context,
        private val retrogradeDb: RetrogradeDatabase,
        private val registeredSystemsStore: RegisteredSystemsStore,
        private val startupStore: LibraryStartupStore,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryViewModel(appContext, retrogradeDb, registeredSystemsStore, startupStore) as T
        }
    }

    data class UiState(
        val filter: LibraryFilter = LibraryFilter.All,
        val searchQuery: String = "",
        val sidebarSystems: List<MetaSystemID> = emptyList(),
        val continueGame: Game? = null,
        val operationInProgress: Boolean = false,
        val syncProgress: SyncProgress? = null,
        val zoomDensity: Int = 0,
        val layout: LibraryLayout = LibraryLayout.CONSOLE,
        val portrait: Boolean = false,
        val sort: LibrarySort = LibrarySort.TITLE,
        val selectedSystemGameCount: Int? = null,
        val startupFilter: LibraryFilter = LibraryFilter.All,
    )

    private data class LibraryChrome(
        val operationInProgress: Boolean,
        val syncProgress: SyncProgress?,
        val presentation: LibraryPresentation,
        val systemCounts: List<SystemCount>,
        val startupFilter: LibraryFilter,
    )

    private data class LibraryPresentation(
        val zoomDensity: Int,
        val layout: LibraryLayout,
        val portrait: Boolean,
        val sort: LibrarySort,
    )

    private val preferences = SharedPreferencesHelper.getSharedPreferences(appContext)
    private val startupFilterFlow = startupStore.observe()
    private val filterFlow = MutableStateFlow(startupStore.get())
    private val searchQueryFlow = MutableStateFlow("")
    private val scanRequested = MutableStateFlow(false)
    private val zoomDensityFlow = MutableStateFlow(readZoomDensity())
    private val layoutFlow = MutableStateFlow(readLayout())
    private val portraitFlow = MutableStateFlow(readPortrait())
    private val sortFlow = MutableStateFlow(readSort())
    private val syncProgressFlow = SyncProgressTracker.progress
    private val presentationFlow =
        combine(zoomDensityFlow, layoutFlow, portraitFlow, sortFlow) { zoom, layout, portrait, sort ->
            LibraryPresentation(zoom, layout, portrait, sort)
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
                combine(
                    scanRequested,
                    libraryOperationInProgress,
                    syncProgressFlow,
                ) { requested, inProgress, progress ->
                    Pair(requested || inProgress, progress)
                },
                presentationFlow,
                retrogradeDb.gameDao().selectSystemsWithCount(),
                startupFilterFlow,
            ) { opStatus, presentation, counts, startup ->
                LibraryChrome(
                    opStatus.first,
                    opStatus.second,
                    presentation,
                    counts,
                    startup,
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
                syncProgress = if (chrome.operationInProgress) chrome.syncProgress else null,
                zoomDensity = chrome.presentation.zoomDensity,
                layout = chrome.presentation.layout,
                portrait = chrome.presentation.portrait,
                sort = chrome.presentation.sort,
                selectedSystemGameCount = selectedCount,
                startupFilter = chrome.startupFilter,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            UiState(
                zoomDensity = zoomDensityFlow.value,
                layout = layoutFlow.value,
                portrait = portraitFlow.value,
                sort = sortFlow.value,
                filter = filterFlow.value,
                startupFilter = startupStore.get(),
            ),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val games: Flow<PagingData<Game>> =
        combine(filterFlow, searchQueryFlow, sortFlow) { filter, query, sort -> Triple(filter, query.trim(), sort) }
            .flatMapLatest { (filter, query, sort) ->
                buildFlowPaging(PAGE_SIZE, viewModelScope) {
                    gamesPagingSource(filter, query, sort)
                }
            }

    init {
        // Leave the startup view if the user changes it while already looking at it.
        viewModelScope.launch {
            var previous = startupStore.get()
            startupFilterFlow.collect { startup ->
                if (startup != previous) {
                    if (filterFlow.value == previous) {
                        filterFlow.value = LibraryFilter.All
                    }
                    previous = startup
                }
            }
        }
    }

    fun selectFilter(filter: LibraryFilter) {
        filterFlow.value = filter
    }

    fun setStartupFilter(filter: LibraryFilter) {
        startupStore.set(filter)
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
        return when (preferences.getString(LAYOUT_KEY, LibraryLayout.CONSOLE.name)) {
            LibraryLayout.LIST.name -> LibraryLayout.LIST
            LibraryLayout.GRID.name -> LibraryLayout.GRID
            else -> LibraryLayout.CONSOLE
        }
    }

    private fun readPortrait(): Boolean = preferences.getBoolean(PORTRAIT_KEY, false)

    fun setSort(sort: LibrarySort) {
        sortFlow.value = sort
        preferences.edit().putString(SORT_KEY, sort.name).apply()
    }

    private fun readSort(): LibrarySort {
        val saved = preferences.getString(SORT_KEY, null)
        return LibrarySort.entries.firstOrNull { it.name == saved } ?: LibrarySort.TITLE
    }

    /** A random game from the current console or favorites filter, ignoring the search. */
    suspend fun randomGame(): Game? {
        val dao = retrogradeDb.gameDao()
        return when (val filter = filterFlow.value) {
            is LibraryFilter.Favorites -> dao.selectRandomFavorite()
            is LibraryFilter.System -> dao.selectRandomBySystems(filter.systemIds())
            else -> dao.selectRandom()
        }
    }

    /** Picks up newly added games on launch without prompting for a folder. */
    fun autoSyncLibrary(context: Context) {
        val folderKey = context.getString(com.omnidroid.lib.R.string.pref_key_extenral_folder)
        val folder =
            SharedPreferencesHelper.getLegacySharedPreferences(context)
                .getString(folderKey, null)
        if (folder.isNullOrBlank()) return
        syncLibrary(context)
    }

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
            startupFilterFlow,
        ) { registered, counts, startup ->
            val withGames =
                counts.mapNotNull { count ->
                    if (count.count <= 0) return@mapNotNull null
                    runCatching { GameSystem.findById(count.systemId).metaSystemID() }.getOrNull()
                }.toSet()

            (registered + withGames + listOfNotNull((startup as? LibraryFilter.System)?.metaSystemID))
                .distinct()
                .sortedBy { appContext.getString(it.titleResId) }
        }
    }

    private fun continueGame(): Flow<List<Game>> = retrogradeDb.gameDao().selectLastPlayed()

    private fun gamesPagingSource(
        filter: LibraryFilter,
        query: String,
        sort: LibrarySort,
    ) = retrogradeDb.gameDao().selectPaged(
        systemIds = (filter as? LibraryFilter.System)?.systemIds(),
        favoritesOnly = filter is LibraryFilter.Favorites,
        query = query,
        order = sort.order,
        systemOrder = consoleOrder,
    )

    /** System ids in the dock's console order. */
    private val consoleOrder by lazy {
        MetaSystemID.entries
            .sortedBy { appContext.getString(it.titleResId) }
            .flatMap { meta -> meta.systemIDs.map { it.dbname } }
    }

    private fun LibraryFilter.System.systemIds() = metaSystemID.systemIDs.map { it.dbname }

    companion object {
        private const val PAGE_SIZE = 20
        private const val ZOOM_DENSITY_KEY = "pref_library_zoom_density"
        private const val LAYOUT_KEY = "pref_library_layout"
        private const val SORT_KEY = "pref_library_sort"
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
    CONSOLE,
    GRID,
    LIST,
}

enum class LibrarySort(val order: GameOrder) {
    TITLE(GameOrder.TITLE),
    CONSOLE(GameOrder.CONSOLE),
    RECENTLY_PLAYED(GameOrder.RECENTLY_PLAYED),
    FAVORITES_FIRST(GameOrder.FAVORITES_FIRST),
}
