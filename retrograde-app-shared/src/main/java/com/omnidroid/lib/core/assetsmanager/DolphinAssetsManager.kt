package com.omnidroid.lib.core.assetsmanager

import android.content.SharedPreferences
import android.net.Uri
import com.omnidroid.lib.core.CoreUpdater
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import timber.log.Timber
import java.io.File
import java.util.zip.ZipInputStream

class DolphinAssetsManager : CoreID.AssetsManager {
    override suspend fun clearAssets(directoriesManager: DirectoriesManager) {
        withContext(Dispatchers.IO) {
            getSysDirectory(directoriesManager).deleteRecursively()
        }
    }

    override suspend fun retrieveAssetsIfNeeded(
        coreUpdaterApi: CoreUpdater.CoreManagerApi,
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
    ) {
        withContext(Dispatchers.IO) {
            val dolphinDir = File(directoriesManager.getSystemDirectory(), DOLPHIN_FOLDER_NAME)
            val sysDir = File(dolphinDir, "Sys").apply { mkdirs() }
            File(dolphinDir, "User").apply { mkdirs() }

            if (!updatedRequested(directoriesManager, sharedPreferences)) {
                return@withContext
            }

            try {
                val response = coreUpdaterApi.downloadZip(DOLPHIN_ASSETS_URL.toString())
                handleSuccess(dolphinDir, sysDir, response, sharedPreferences)
            } catch (e: Throwable) {
                Timber.w(e, "DolphinAssetsManager: Could not download assets from remote")
            }
        }
    }

    private suspend fun handleSuccess(
        dolphinDir: File,
        sysDir: File,
        response: Response<ResponseBody>,
        sharedPreferences: SharedPreferences,
    ) {
        if (!response.isSuccessful) {
            throw Exception(response.errorBody()?.use { it.string() } ?: "Dolphin assets download failed")
        }

        sysDir.deleteRecursively()
        sysDir.mkdirs()

        response.body()?.use { responseBody ->
            ZipInputStream(responseBody.byteStream()).use { zipInputStream ->
                while (true) {
                    val entry = zipInputStream.nextEntry ?: break
                    Timber.d("DolphinAssetsManager: Extracting ${entry.name}")

                    val entryName = entry.name.replace('\\', '/')
                    val normalizedName = when {
                        entryName.startsWith("dolphin-emu/", ignoreCase = true) -> entryName.substring("dolphin-emu/".length)
                        entryName.startsWith("Sys/", ignoreCase = true) -> entryName
                        else -> "Sys/$entryName"
                    }

                    val destFile = File(dolphinDir, normalizedName)
                    if (entry.isDirectory || normalizedName.endsWith("/")) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        destFile.outputStream().use { output ->
                            zipInputStream.copyTo(output)
                        }
                    }
                }
            }
        } ?: throw Exception("Empty Dolphin assets body")

        sharedPreferences.edit()
            .putString(DOLPHIN_ASSETS_VERSION_KEY, DOLPHIN_ASSETS_VERSION)
            .commit()
    }

    private fun updatedRequested(
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
    ): Boolean {
        val sysDir = getSysDirectory(directoriesManager)
        val sysExists = sysDir.exists() && (sysDir.listFiles()?.isNotEmpty() == true)
        val currentVersion = sharedPreferences.getString(DOLPHIN_ASSETS_VERSION_KEY, "none")
        val hasCurrentVersion = currentVersion == DOLPHIN_ASSETS_VERSION

        return !sysExists || !hasCurrentVersion
    }

    private fun getSysDirectory(directoriesManager: DirectoriesManager): File {
        return File(directoriesManager.getSystemDirectory(), "$DOLPHIN_FOLDER_NAME/Sys")
    }

    companion object {
        const val DOLPHIN_ASSETS_VERSION = "omnidroid-3"

        val DOLPHIN_ASSETS_URL: Uri =
            Uri.parse("https://raw.githubusercontent.com/Ahmed-Abousaif/OmnidroidCores/")
                .buildUpon()
                .appendEncodedPath("2.1.0/assets/dolphin.zip")
                .build()

        const val DOLPHIN_ASSETS_VERSION_KEY = "dolphin_assets_version_key"

        const val DOLPHIN_FOLDER_NAME = "dolphin-emu"
    }
}
