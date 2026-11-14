package com.omnidroid.lib.core

import android.content.Context
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import com.omnidroid.lib.storage.DirectoriesManager
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.OverlappingFileLockException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CoreAssetsException(val coreID: CoreID, cause: Throwable) :
    IOException("Could not install ${coreID.coreDisplayName} support files. Check your connection and retry in Consoles.", cause)

object CoreAssets {
    private val changes = MutableStateFlow(0)
    val revision = changes.asStateFlow()
    fun hasAssets(coreID: CoreID): Boolean = coreID in setOf(CoreID.PPSSPP, CoreID.ARMSX2, CoreID.DOLPHIN, CoreID.CEMU)

    suspend fun isReady(context: Context, coreID: CoreID): Boolean =
        withContext(Dispatchers.IO) {
            CoreID.getAssetManager(coreID).isReady(DirectoriesManager(context.applicationContext))
        }

    suspend fun install(
        context: Context,
        coreIDs: List<CoreID>,
        api: CoreUpdater.CoreManagerApi,
        directories: DirectoriesManager,
        force: Boolean = false,
    ) {
        val preferences = SharedPreferencesHelper.getSharedPreferences(context.applicationContext)
        try {
            coreIDs.distinct().filter(::hasAssets).forEach { core ->
                try {
                    withLock(directories, core) {
                        val manager = CoreID.getAssetManager(core)
                        retryAssetInstall {
                            manager.retrieveAssetsIfNeeded(api, directories, preferences, force)
                            if (!manager.isReady(directories)) throw IOException("Support files are incomplete")
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    throw CoreAssetsException(core, error)
                }
            }
        } finally {
            changes.update { it + 1 }
        }
    }

    suspend fun remove(context: Context, coreIDs: List<CoreID>, directories: DirectoriesManager) {
        val preferences = SharedPreferencesHelper.getSharedPreferences(context.applicationContext)
        try {
            coreIDs.distinct().filter(::hasAssets).forEach { core ->
                withLock(directories, core) { CoreID.getAssetManager(core).clearAssets(directories, preferences) }
            }
        } finally {
            changes.update { it + 1 }
        }
    }

    internal suspend fun withLock(directories: DirectoriesManager, core: CoreID, block: suspend () -> Unit) {
        withContext(Dispatchers.IO) {
            RandomAccessFile(java.io.File(directories.getSystemDirectory(), ".${core.coreName}-assets.lock"), "rw").channel.use { channel ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val lock = try { channel.tryLock() } catch (_: OverlappingFileLockException) { null }
                    if (lock != null) {
                        lock.use { block() }
                        break
                    }
                    delay(50)
                }
            }
        }
    }
}

internal suspend fun retryAssetInstall(attempts: Int = 3, block: suspend () -> Unit) {
    repeat(attempts) { attempt ->
        try {
            block()
            return
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            if (attempt == attempts - 1) throw error
            delay(500L * (attempt + 1))
        }
    }
}
