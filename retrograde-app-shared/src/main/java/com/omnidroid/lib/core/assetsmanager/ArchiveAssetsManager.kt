package com.omnidroid.lib.core.assetsmanager

import android.content.SharedPreferences
import com.omnidroid.lib.core.CoreUpdater
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.storage.DirectoriesManager
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

abstract class ArchiveAssetsManager : CoreID.AssetsManager {
    protected abstract val directoryName: String
    protected abstract val versionKey: String
    protected abstract val version: String
    protected abstract val downloadUrl: String

    protected fun directory(directories: DirectoriesManager) = File(directories.getSystemDirectory(), directoryName)
    protected open fun mapEntry(name: String): String? = name
    protected abstract fun validate(directory: File): Boolean
    protected open fun publish(directory: File, directories: DirectoriesManager, preferences: SharedPreferences) {}

    override suspend fun isReady(directoriesManager: DirectoriesManager): Boolean =
        withContext(Dispatchers.IO) {
            val destination = directory(directoriesManager)
            if (AssetInstallation.hasManifest(destination)) AssetInstallation.isComplete(destination, verifyContents = false)
            else validate(destination)
        }

    override suspend fun retrieveAssetsIfNeeded(
        coreUpdaterApi: CoreUpdater.CoreManagerApi,
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
        force: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            val destination = directory(directoriesManager)
            if (!force && !AssetInstallation.hasManifest(destination) &&
                sharedPreferences.getString(versionKey, null) == version && validate(destination)
            ) {
                File(destination, ".omnidroid-version").writeText(version)
                AssetInstallation.recordInstallation(destination)
            }
            val currentVersion = File(destination, ".omnidroid-version").takeIf { it.isFile }?.readText()
            if (force || currentVersion != version || !AssetInstallation.isComplete(destination)) {
                val response = coreUpdaterApi.downloadZip(downloadUrl)
                if (!response.isSuccessful) {
                    response.errorBody()?.close()
                    response.body()?.close()
                    throw IOException("Support files download failed (HTTP ${response.code()})")
                }
                val body = response.body() ?: throw IOException("Support files download was empty")
                val coroutine = currentCoroutineContext()
                body.use {
                    AssetInstallation.install(
                        it.byteStream(), destination, ::mapEntry,
                        validate = { stage ->
                            if (validate(stage)) {
                                File(stage, ".omnidroid-version").writeText(version)
                                true
                            } else false
                        },
                        checkCancelled = { coroutine.ensureActive() },
                    )
                }
            }
            publish(destination, directoriesManager, sharedPreferences)
            if (!isReady(directoriesManager)) throw IOException("Support files are incomplete")
            if (!sharedPreferences.edit().putString(versionKey, version).commit()) {
                throw IOException("Could not save support files installation status")
            }
        }
    }

    override suspend fun clearAssets(directoriesManager: DirectoriesManager, sharedPreferences: SharedPreferences) {
        withContext(Dispatchers.IO) {
            AssetInstallation.remove(directory(directoriesManager))
            sharedPreferences.edit().remove(versionKey).commit()
        }
    }
}
