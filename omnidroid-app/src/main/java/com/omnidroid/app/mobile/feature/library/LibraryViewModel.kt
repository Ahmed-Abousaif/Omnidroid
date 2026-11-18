package com.omnidroid.app.mobile.feature.library

import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.omnidroid.R
import com.omnidroid.app.shared.library.LibraryIndexScheduler
import com.omnidroid.app.shared.library.PendingOperationsMonitor
import com.omnidroid.app.shared.settings.StorageFrameworkPickerLauncher
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers

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
        val sort: LibrarySort,
    )

    private val preferences = SharedPreferencesHelper.getSharedPreferences(appContext)
    private val startupFilterFlow = startupStore.observe()
    private val filterFlow = MutableStateFlow(startupStore.get())
    private val searchQueryFlow = MutableStateFlow("")
    private val scanRequested = MutableStateFlow(false)
    private val zoomDensityFlow = MutableStateFlow(readZoomDensity())
    private val portraitFlow =
        MutableStateFlow(appContext.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
    private val separateLayoutsFlow = MutableStateFlow(readSeparateLayouts())
    private val landscapeLayoutFlow = MutableStateFlow(readLayout(portrait = false))
    private val portraitLayoutFlow = MutableStateFlow(readLayout(portrait = true))
    private val layoutFlow =
        combine(portraitFlow, separateLayoutsFlow, landscapeLayoutFlow, portraitLayoutFlow) { portrait, separate, landscape, portraitLayout ->
            LibraryLayout.forOrientation(portrait, separate, landscape, portraitLayout)
        }

    // Settings > Display turns separate layouts on and off while the library stays alive.
    private val layoutPreferenceListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                SEPARATE_LAYOUTS_KEY -> separateLayoutsFlow.value = readSeparateLayouts()
                LAYOUT_KEY -> landscapeLayoutFlow.value = readLayout(portrait = false)
                PORTRAIT_LAYOUT_KEY -> portraitLayoutFlow.value = readLayout(portrait = true)
            }
        }

    init {
        preferences.registerOnSharedPreferenceChangeListener(layoutPreferenceListener)
    }

    override fun onCleared() {
        preferences.unregisterOnSharedPreferenceChangeListener(layoutPreferenceListener)
        super.onCleared()
    }
    private val sortFlow = MutableStateFlow(readSort())
    private val syncProgressFlow = SyncProgressTracker.progress
    private val presentationFlow =
        combine(zoomDensityFlow, layoutFlow, sortFlow) { zoom, layout, sort ->
            LibraryPresentation(zoom, layout, sort)
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
                sort = chrome.presentation.sort,
                selectedSystemGameCount = selectedCount,
                startupFilter = chrome.startupFilter,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            UiState(
                zoomDensity = zoomDensityFlow.value,
                layout = currentLayout(),
                sort = sortFlow.value,
                filter = filterFlow.value,
                startupFilter = startupStore.get(),
            ),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val games: Flow<PagingData<Game>> =
        combine(filterFlow, searchQueryFlow, sortFlow) { filter, query, sort -> Triple(filter, query.trim(), sort) }
            .flatMapLatest { (filter, query, sort) ->
                Pager(LibraryPaging.config()) {
                    gamesPagingSource(filter, query, sort)
                }.flow
            }.cachedIn(viewModelScope)

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

    fun setZoomDensity(density: Int) {
        val value = density.coerceIn(0, MAX_ZOOM_DENSITY)
        zoomDensityFlow.value = value
        preferences.edit().putInt(ZOOM_DENSITY_KEY, value).apply()
    }

    /** The screen's orientation; with separate layouts on, the library shows the one chosen for it. */
    fun setPortrait(portrait: Boolean) {
        portraitFlow.value = portrait
    }

    /** Changes the layout: for the current orientation when layouts are separate, else for both. */
    fun setLayout(layout: LibraryLayout) {
        val portraitOnly = portraitFlow.value && separateLayoutsFlow.value
        (if (portraitOnly) portraitLayoutFlow else landscapeLayoutFlow).value = layout
        preferences.edit().putString(layoutKey(portraitOnly), layout.name).apply()
    }

    private fun currentLayout(): LibraryLayout =
        LibraryLayout.forOrientation(
            portraitFlow.value,
            separateLayoutsFlow.value,
            landscapeLayoutFlow.value,
            portraitLayoutFlow.value,
        )

    private fun readSeparateLayouts(): Boolean = preferences.getBoolean(SEPARATE_LAYOUTS_KEY, false)

    /** Portrait's own layout; until one is picked there, the one already chosen. */
    private fun readLayout(portrait: Boolean): LibraryLayout =
        LibraryLayout.fromName(
            preferences.getString(layoutKey(portrait), null) ?: preferences.getString(LAYOUT_KEY, null),
        )

    private fun readZoomDensity(): Int {
        return preferences.getInt(ZOOM_DENSITY_KEY, 0).coerceIn(0, MAX_ZOOM_DENSITY)
    }

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

    /** Reconcile folder changes on resume / watcher without prompting for a folder. */
    fun autoSyncLibrary(context: Context) {
        if (!preferences.getBoolean(context.getString(R.string.pref_key_auto_library_scan), true)) return
        val folderKey = context.getString(com.omnidroid.lib.R.string.pref_key_extenral_folder)
        val folder =
            SharedPreferencesHelper.getLegacySharedPreferences(context)
                .getString(folderKey, null)
        if (folder.isNullOrBlank()) return
        syncLibrary(context, forceDeep = false)
    }

    suspend fun clearMissingGames(): Int =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val missing = retrogradeDb.gameDao().selectUnavailable()
            missing.forEach { game ->
                game.customCoverPath?.let { path ->
                    runCatching { java.io.File(path.substringBefore('?')).delete() }
                }
            }
            if (missing.isNotEmpty()) {
                retrogradeDb.gameDao().delete(missing)
            }
            missing.size
        }

    fun zoomIn() {
        if (currentLayout() != LibraryLayout.GRID) return
        setZoomDensity(zoomDensityFlow.value - 1)
    }

    fun zoomOut() {
        if (currentLayout() != LibraryLayout.GRID) return
        setZoomDensity(zoomDensityFlow.value + 1)
    }

    fun syncLibrary(
        context: Context,
        forceDeep: Boolean = true,
    ) {
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
                LibraryIndexScheduler.scheduleLibrarySync(
                    context.applicationContext,
                    forceDeep = forceDeep,
                )
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
        private const val ZOOM_DENSITY_KEY = "pref_library_zoom_density"
        /** The layout everywhere, or in landscape when portrait has its own ([PORTRAIT_LAYOUT_KEY]). */
        private const val LAYOUT_KEY = "pref_library_layout"
        private const val PORTRAIT_LAYOUT_KEY = "pref_library_layout_portrait"

        /** Settings > Display: portrait and landscape remember their own layout. */
        const val SEPARATE_LAYOUTS_KEY = "pref_library_layout_per_orientation"

        private fun layoutKey(portraitOnly: Boolean): String = if (portraitOnly) PORTRAIT_LAYOUT_KEY else LAYOUT_KEY
        private const val SORT_KEY = "pref_library_sort"
        const val MAX_ZOOM_DENSITY = 2
    }
}

enum class LibraryLayout {
    CONSOLE,
    GRID,
    LIST,
    ;

    companion object {
        fun fromName(name: String?): LibraryLayout = entries.firstOrNull { it.name == name } ?: CONSOLE

        /** The layout to show: portrait's own only when [separate] layouts are on. */
        fun forOrientation(
            portrait: Boolean,
            separate: Boolean,
            landscapeLayout: LibraryLayout,
            portraitLayout: LibraryLayout,
        ): LibraryLayout = if (portrait && separate) portraitLayout else landscapeLayout
    }
}

enum class LibrarySort(val order: GameOrder) {
    TITLE(GameOrder.TITLE),
    CONSOLE(GameOrder.CONSOLE),
    RECENTLY_PLAYED(GameOrder.RECENTLY_PLAYED),
    FAVORITES_FIRST(GameOrder.FAVORITES_FIRST),
}
