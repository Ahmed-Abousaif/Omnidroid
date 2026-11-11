package com.omnidroid.app.mobile.feature.addconsole

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omnidroid.R
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
        val installedDatabaseCount: Int = if (databaseInstalled) 1 else 0,
        val expectedDatabaseCount: Int = 1,
        val hasDownloadedCore: Boolean = coreInstalled && !coreBundled,
    ) {
        val hasDatabase: Boolean get() = installedDatabaseCount > 0
        val databaseAvailable: Boolean get() = expectedDatabaseCount > 0
        val needsAttention: Boolean
            get() =
                (registeredOrHasGames || hasDatabase || hasDownloadedCore) &&
                    (!coreInstalled || (databaseAvailable && !databaseInstalled))
        val canRemove: Boolean get() = registeredOrHasGames || hasDatabase || hasDownloadedCore
    }

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
        val loading: Boolean = true,
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
            corePresenceRevision,
            coresSelection.getSelectedCores(),
            libretroDb.dbInstance.sliceDao().observeAll(),
        ) { registered, counts, revision, _, dbSlices ->
            val directSlices =
                withContext(Dispatchers.IO) {
                    runCatching { libretroDb.dbInstance.sliceDao().all() }.getOrNull()
                }
            LibrarySnapshot(
                registered = registeredSystemsStore.get() + registered,
                counts = counts,
                slices = directSlices ?: dbSlices,
                corePresenceRevision = revision,
            )
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
                            val selectedCores = ConsoleManager.coresFor(meta, coresSelection)
                            val catalogCores = (selectedCores + ConsoleManager.catalogCoresFor(meta)).distinct()
                            val coreInstalled =
                                selectedCores.isNotEmpty() &&
                                    selectedCores.all {
                                        ConsoleManager.isCorePresent(applicationContext, it)
                                    }
                            val coreBundled =
                                selectedCores.isNotEmpty() &&
                                    selectedCores.all {
                                        ConsoleManager.isCoreBundled(applicationContext, it)
                                    }
                            ConsoleItem(
                                metaSystemID = meta,
                                registeredOrHasGames = meta in installedMeta,
                                coreInstalled = coreInstalled,
                                coreBundled = coreBundled,
                                coreDisplayName = selectedCores.joinToString { it.coreDisplayName }.ifBlank { null },
                                databaseInstalled = databaseInstalled,
                                databaseGameCount = databaseGameCount,
                                libraryGameCount = libraryGameCount,
                                installedDatabaseCount = installedRelated.size,
                                expectedDatabaseCount = relatedSlices.size,
                                hasDownloadedCore =
                                    catalogCores.any {
                                        ConsoleManager.isCorePresent(applicationContext, it) &&
                                            !ConsoleManager.isCoreBundled(applicationContext, it)
                                    },
                            )
                        }
                        .sortedWith(
                            compareByDescending<ConsoleItem> { it.registeredOrHasGames || it.coreInstalled }
                                .thenBy { applicationContext.getString(it.metaSystemID.titleResId) },
                        ),
                busyMetaSystemID = busy.busyMeta,
                busyAction = busy.busyAction,
                errorMessage = busy.errorMessage,
                loading = false,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun install(metaSystemID: MetaSystemID) {
        runExclusive(metaSystemID, BusyAction.INSTALL) {
            val cores = ConsoleManager.coresFor(metaSystemID, coresSelection)
            check(cores.isNotEmpty()) { applicationContext.getString(R.string.consoles_emulator_unavailable) }
            val missing = cores.filterNot { ConsoleManager.isCorePresent(applicationContext, it) }
            if (missing.isNotEmpty()) coreUpdater.downloadCores(applicationContext, missing)
            check(cores.all { ConsoleManager.isCorePresent(applicationContext, it) }) {
                applicationContext.getString(R.string.consoles_install_failed)
            }
            registeredSystemsStore.register(metaSystemID)
            corePresenceRevision.value = corePresenceRevision.value + 1
            runCatching { sliceInstaller.ensureSlices(applicationContext, cores) }
        }
    }

    fun refetchDatabase(metaSystemID: MetaSystemID) {
        runExclusive(metaSystemID, BusyAction.REFETCH_DATABASE) {
            val cores =
                ConsoleManager.coresFor(metaSystemID, coresSelection)
                    .ifEmpty { ConsoleManager.catalogCoresFor(metaSystemID) }
            sliceInstaller.ensureSlices(applicationContext, cores, force = true)
            checkDatabase(metaSystemID)
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
                (
                    ConsoleManager.coresFor(metaSystemID, coresSelection) +
                        ConsoleManager.catalogCoresFor(metaSystemID)
                ).distinct()
            val systemsWithGames =
                retrogradeDb.gameDao().selectSystemsWithCount().first()
                    .filter { it.count > 0 }
                    .mapNotNull { runCatching { GameSystem.findById(it.systemId).metaSystemID() }.getOrNull() }
            val remaining = registeredSystemsStore.get() + systemsWithGames
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

    private suspend fun checkDatabase(metaSystemID: MetaSystemID) {
        val expected = ConsoleManager.sliceIdsFor(metaSystemID)
        if (expected.isEmpty()) return
        val installedIds = libretroDb.dbInstance.sliceDao().all().map { it.sliceId }.toSet()
        check(installedIds.containsAll(expected)) {
            applicationContext.getString(R.string.consoles_database_failed)
        }
    }

    private fun runExclusive(
        metaSystemID: MetaSystemID,
        action: BusyAction,
        block: suspend () -> Unit,
    ) {
        if (busyMetaFlow.value != null) return
        // Claim the operation before launching, including multiple clicks in the same frame.
        busyMetaFlow.value = metaSystemID
        busyActionFlow.value = action
        errorFlow.value = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.e(error, "Console action %s failed for %s", action, metaSystemID.name)
                errorFlow.value = error.message?.takeIf { it.isNotBlank() }
                    ?: applicationContext.getString(R.string.consoles_action_failed)
            } finally {
                corePresenceRevision.value = corePresenceRevision.value + 1
                busyMetaFlow.value = null
                busyActionFlow.value = null
            }
        }
    }
}
