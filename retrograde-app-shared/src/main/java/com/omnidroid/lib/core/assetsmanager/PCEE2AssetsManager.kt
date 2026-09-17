package com.omnidroid.lib.core.assetsmanager

import android.content.SharedPreferences
import com.omnidroid.lib.core.CoreUpdater
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class PCEE2AssetsManager : CoreID.AssetsManager {
    override suspend fun clearAssets(directoriesManager: DirectoriesManager) {
        withContext(Dispatchers.IO) {
            File(directoriesManager.getSystemDirectory(), "pcsx2").deleteRecursively()
        }
    }

    override suspend fun retrieveAssetsIfNeeded(
        coreUpdaterApi: CoreUpdater.CoreManagerApi,
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
    ) {
        withContext(Dispatchers.IO) {
            val systemDir = directoriesManager.getSystemDirectory()
            val pcsx2BiosDir = File(systemDir, "pcsx2/bios")
            if (!pcsx2BiosDir.exists()) {
                pcsx2BiosDir.mkdirs()
            }

            // Sync any PS2 BIOS placed in system/ to system/pcsx2/bios/
            val ps2BiosNames = listOf(
                "scph39001.bin",
                "scph70012.bin",
                "scph77001.bin",
            )
            for (biosName in ps2BiosNames) {
                val src = File(systemDir, biosName)
                val dest = File(pcsx2BiosDir, biosName)
                if (src.exists() && (!dest.exists() || dest.length() != src.length())) {
                    try {
                        src.copyTo(dest, overwrite = true)
                        Timber.d("PCEE2AssetsManager: Copied $biosName to ${dest.path}")
                    } catch (e: Exception) {
                        Timber.e(e, "PCEE2AssetsManager: Error copying $biosName to $dest")
                    }
                }
            }
        }
    }
}
