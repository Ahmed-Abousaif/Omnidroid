package com.omnidroid.app.mobile.feature.addconsole

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omnidroid.app.mobile.feature.library.RegisteredSystemsStore
import com.omnidroid.app.shared.covers.CustomCoverManager
import com.omnidroid.lib.core.ConsoleManager
import com.omnidroid.lib.core.CoreUpdater
import com.omnidroid.lib.core.CoresSelection
import com.omnidroid.lib.core.MetadataSliceInstaller
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.MetaSystemID
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.dao.SystemCount
import com.omnidroid.lib.library.metaSystemID
import com.omnidroid.metadata.libretrodb.db.LibretroDBManager
import com.omnidroid.metadata.libretrodb.db.entity.InstalledSlice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class AddConsoleViewModel(
    appContext: Context,
    private val retrogradeDb: RetrogradeDatabase,
    private val registeredSystemsStore: RegisteredSystemsStore,
    private val coreUpdater: CoreUpdater,
    private val coresSelection: CoresSelection,
    private val libretroDb: LibretroDBManager,
    private val sliceInstaller: MetadataSliceInstaller,
) : ViewModel() {
    class Factory(
        private val appContext: Context,
        private val retrogradeDb: RetrogradeDatabase,
        private val registeredSystemsStore: RegisteredSystemsStore,
        private val coreUpdater: CoreUpdater,
        private val coresSelection: CoresSelection,
        private val libretroDb: LibretroDBManager,
        private val sliceInstaller: MetadataSliceInstaller,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AddConsoleViewModel(
                appContext,
                retrogradeDb,
                registeredSystemsStore,
                coreUpdater,
                coresSelection,
                libretroDb,
                sliceInstaller,
            ) as T
        }
    }

    data class ConsoleItem(
        val metaSystemID: MetaSystemID,
        val registeredOrHasGames: Boolean,
        val coreInstalled: Boolean,
        val coreBundled: Boolean,
        val coreDisplayName: String?,
        val databaseInstalled: Boolean,
        val databaseGameCount: Int,
        val libraryGameCount: Int,
    )

    enum class BusyAction {
        INSTALL,
        REFETCH_DATABASE,
        DELETE_DATABASE,
        REMOVE_CONSOLE,
    }

    data class UiState(
        val consoles: List<ConsoleItem> = emptyList(),
        val busyMetaSystemID: MetaSystemID? = null,
        val busyAction: BusyAction? = null,
        val errorMessage: String? = null,
    )

    private data class LibrarySnapshot(
        val registered: Set<MetaSystemID>,
        val counts: List<SystemCount>,
        val slices: List<InstalledSlice>,
        val corePresenceRevision: Int,
    )

    private data class BusySnapshot(
        val busyMeta: MetaSystemID?,
        val busyAction: BusyAction?,
        val errorMessage: String?,
    )

    private val applicationContext = appContext.applicationContext
    private val busyMetaFlow = MutableStateFlow<MetaSystemID?>(null)
    private val busyActionFlow = MutableStateFlow<BusyAction?>(null)
    private val errorFlow = MutableStateFlow<String?>(null)
    private val corePresenceRevision = MutableStateFlow(0)

    private val librarySnapshot =
        combine(
            registeredSystemsStore.observe(),
            retrogradeDb.gameDao().selectSystemsWithCount(),
            libretroDb.dbInstance.sliceDao().observeAll(),
            corePresenceRevision,
        ) { registered, counts, slices, revision ->
            LibrarySnapshot(registered, counts, slices, revision)
        }

    private val busySnapshot =
        combine(busyMetaFlow, busyActionFlow, errorFlow) { busyMeta, busyAction, errorMessage ->
            BusySnapshot(busyMeta, busyAction, errorMessage)
        }

    val state: StateFlow<UiState> =
        combine(librarySnapshot, busySnapshot) { library, busy ->
            val countBySystem = library.counts.associate { it.systemId to it.count }
            val withGames =
                library.counts.mapNotNull { count ->
                    if (count.count <= 0) return@mapNotNull null
                    runCatching { GameSystem.findById(count.systemId).metaSystemID() }.getOrNull()
                }.toSet()
            val installedMeta = library.registered + withGames
            val slicesById = library.slices.associateBy { it.sliceId }

            UiState(
                consoles =
                    MetaSystemID.values()
                        .map { meta ->
                            val systemNames = ConsoleManager.systemDbNames(meta)
                            val libraryGameCount = systemNames.sumOf { countBySystem[it] ?: 0 }
                            val relatedSlices = ConsoleManager.slicesFor(meta)
                            val installedRelated = relatedSlices.mapNotNull { slicesById[it.id] }
                            val databaseInstalled =
                                relatedSlices.isNotEmpty() && installedRelated.size == relatedSlices.size
                            val databaseGameCount = installedRelated.sumOf { it.rows }
                            val catalogCores = ConsoleManager.catalogCoresFor(meta)
                            val primaryCore = catalogCores.firstOrNull()
                            val coreInstalled =
                                catalogCores.any { ConsoleManager.isCorePresent(applicationContext, it) }
                            val coreBundled =
                                catalogCores.any { ConsoleManager.isCoreBundled(applicationContext, it) }
                            ConsoleItem(
                                metaSystemID = meta,
                                registeredOrHasGames = meta in installedMeta,
                                coreInstalled = coreInstalled,
                                coreBundled = coreBundled,
                                coreDisplayName = primaryCore?.coreDisplayName,
                                databaseInstalled = databaseInstalled,
                                databaseGameCount = databaseGameCount,
                                libraryGameCount = libraryGameCount,
                            )
                        }
                        .sortedWith(
                            compareByDescending<ConsoleItem> { it.registeredOrHasGames || it.coreInstalled }
                                .thenBy { applicationContext.getString(it.metaSystemID.titleResId) },
                        ),
                busyMetaSystemID = busy.busyMeta,
                busyAction = busy.busyAction,
                errorMessage = busy.errorMessage,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun install(metaSystemID: MetaSystemID) {
        runExclusive(metaSystemID, BusyAction.INSTALL) {
            val cores = ConsoleManager.coresFor(metaSystemID, coresSelection)
            coreUpdater.downloadCores(applicationContext, cores)
            registeredSystemsStore.register(metaSystemID)
            corePresenceRevision.value = corePresenceRevision.value + 1
        }
    }

    fun refetchDatabase(metaSystemID: MetaSystemID) {
        runExclusive(metaSystemID, BusyAction.REFETCH_DATABASE) {
            val cores =
                ConsoleManager.coresFor(metaSystemID, coresSelection)
                    .ifEmpty { ConsoleManager.catalogCoresFor(metaSystemID) }
            sliceInstaller.ensureSlices(applicationContext, cores, force = true)
        }
    }

    fun deleteDatabase(metaSystemID: MetaSystemID) {
        runExclusive(metaSystemID, BusyAction.DELETE_DATABASE) {
            sliceInstaller.removeSlices(ConsoleManager.sliceIdsFor(metaSystemID))
        }
    }

    fun removeConsole(metaSystemID: MetaSystemID) {
        runExclusive(metaSystemID, BusyAction.REMOVE_CONSOLE) {
            val cores =
                ConsoleManager.coresFor(metaSystemID, coresSelection)
                    .ifEmpty { ConsoleManager.catalogCoresFor(metaSystemID) }
            val remaining = registeredSystemsStore.get()
            val safeToDelete =
                ConsoleManager.coresSafeToUninstall(
                    applicationContext,
                    metaSystemID,
                    cores,
                    remaining,
                )

            sliceInstaller.removeSlices(ConsoleManager.sliceIdsFor(metaSystemID))

            val systemIds = ConsoleManager.systemDbNames(metaSystemID)
            val games = retrogradeDb.gameDao().selectBySystemIds(systemIds)
            if (games.isNotEmpty()) {
                val coverManager = CustomCoverManager(applicationContext)
                games.forEach { game ->
                    runCatching { coverManager.deleteCustomCover(game.id, game.customCoverPath) }
                }
                retrogradeDb.gameDao().deleteBySystemIds(systemIds)
            }

            registeredSystemsStore.unregister(metaSystemID)

            if (safeToDelete.isNotEmpty()) {
                coreUpdater.uninstallCores(applicationContext, safeToDelete)
            }
            corePresenceRevision.value = corePresenceRevision.value + 1
        }
    }

    fun consumeError() {
        errorFlow.value = null
    }

    private fun runExclusive(
        metaSystemID: MetaSystemID,
        action: BusyAction,
        block: suspend () -> Unit,
    ) {
        if (busyMetaFlow.value != null) return
        viewModelScope.launch {
            busyMetaFlow.value = metaSystemID
            busyActionFlow.value = action
            errorFlow.value = null
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (error: Throwable) {
                Timber.e(error, "Console action %s failed for %s", action, metaSystemID.name)
                errorFlow.value = error.message
            } finally {
                busyMetaFlow.value = null
                busyActionFlow.value = null
            }
        }
    }
}
