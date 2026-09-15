package com.omnidroid.lib.core.assetsmanager

import android.content.SharedPreferences
import com.omnidroid.lib.core.CoreUpdater
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.storage.DirectoriesManager

class NoAssetsManager : CoreID.AssetsManager {
    override suspend fun clearAssets(directoriesManager: DirectoriesManager) {}

    override suspend fun retrieveAssetsIfNeeded(
        coreUpdaterApi: CoreUpdater.CoreManagerApi,
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
    ) {
    }
}
