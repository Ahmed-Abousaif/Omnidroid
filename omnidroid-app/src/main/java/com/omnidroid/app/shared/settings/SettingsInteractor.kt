package com.omnidroid.app.shared.settings

import android.content.Context
import com.omnidroid.app.OmnidroidApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.omnidroid.app.shared.library.LibraryIndexScheduler
import com.omnidroid.app.shared.storage.cache.CacheCleanerWork
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import com.omnidroid.lib.saves.CustomSaveStorage
import com.omnidroid.lib.storage.DirectoriesManager

class SettingsInteractor(
    private val context: Context,
    private val directoriesManager: DirectoriesManager,
) {
    fun changeLocalStorageFolder() {
        StorageFrameworkPickerLauncher.pickFolder(context)
    }

    fun changeCustomSavesFolder() {
        SaveFrameworkPickerLauncher.pickFolder(context)
    }

    suspend fun resetCustomSavesFolder(): CustomSaveStorage.MigrationResult {
        val customSaveStorage = CustomSaveStorage(context, directoriesManager)
        val currentUri = customSaveStorage.getCustomSavesFolderUriString()
            ?: return CustomSaveStorage.MigrationResult.Success

        val result = customSaveStorage.migrateStorage(currentUri, null)
        if (result is CustomSaveStorage.MigrationResult.Success) {
            runCatching {
                val uri = android.net.Uri.parse(currentUri)
                val legacyPrefs = SharedPreferencesHelper.getLegacySharedPreferences(context)
                val romsKey = context.getString(com.omnidroid.lib.R.string.pref_key_extenral_folder)
                val romsUri = legacyPrefs.getString(romsKey, null)
                if (uri.toString() != romsUri) {
                    context.contentResolver.releasePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            }
        }
        return result
    }

    fun resetAllSettings() {
        val legacyPrefs = SharedPreferencesHelper.getLegacySharedPreferences(context)
        val defaultPrefs = SharedPreferencesHelper.getSharedPreferences(context)
        legacyPrefs.edit().clear().apply()
        defaultPrefs.edit().clear().apply()
        LibraryIndexScheduler.scheduleLibrarySync(context.applicationContext)
        CacheCleanerWork.enqueueCleanCacheAll(context.applicationContext)
        OmnidroidApplication.scope(context.applicationContext).launch(Dispatchers.IO) { deleteDownloadedCores() }
    }

    private fun deleteDownloadedCores() {
        directoriesManager.getCoresDirectory()
            .listFiles()
            ?.forEach { runCatching { it.deleteRecursively() } }
    }
}
