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

class PPSSPPAssetsManager : CoreID.AssetsManager {
    override suspend fun clearAssets(directoriesManager: DirectoriesManager) {
        getAssetsDirectory(directoriesManager).deleteRecursively()
    }

    override suspend fun retrieveAssetsIfNeeded(
        coreUpdaterApi: CoreUpdater.CoreManagerApi,
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
    ) {
        if (!updatedRequested(directoriesManager, sharedPreferences)) {
            return
        }

        try {
            val response = coreUpdaterApi.downloadZip(PPSSPP_ASSETS_URL.toString())
            handleSuccess(directoriesManager, response, sharedPreferences)
        } catch (e: Throwable) {
            getAssetsDirectory(directoriesManager).deleteRecursively()
        }
    }

    private suspend fun handleSuccess(
        directoriesManager: DirectoriesManager,
        response: Response<ResponseBody>,
        sharedPreferences: SharedPreferences,
    ) {
        if (!response.isSuccessful) {
            throw Exception(response.errorBody()?.use { it.string() } ?: "PPSSPP assets download failed")
        }

        val coreAssetsDirectory = getAssetsDirectory(directoriesManager)
        coreAssetsDirectory.deleteRecursively()
        coreAssetsDirectory.mkdirs()

        response.body()?.use { responseBody ->
            ZipInputStream(responseBody.byteStream()).use { zipInputStream ->
                while (true) {
                    val entry = zipInputStream.nextEntry ?: break
                    Timber.d("Writing file: ${entry.name}")
                    val entryName = entry.name.replace('\\', '/')
                    val destFile =
                        File(
                            coreAssetsDirectory,
                            entryName,
                        )
                    if (entry.isDirectory || entryName.endsWith("/")) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        destFile.outputStream().use { output ->
                            zipInputStream.copyTo(output)
                        }
                    }
                }
            }
        } ?: throw Exception("Empty PPSSPP assets body")

        sharedPreferences.edit()
            .putString(PPSSPP_ASSETS_VERSION_KEY, PPSSPP_ASSETS_VERSION)
            .commit()
    }

    private suspend fun updatedRequested(
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val directoryExists = getAssetsDirectory(directoriesManager).exists()

            val currentVersion = sharedPreferences.getString(PPSSPP_ASSETS_VERSION_KEY, "none")
            val hasCurrentVersion = currentVersion == PPSSPP_ASSETS_VERSION

            !directoryExists || !hasCurrentVersion
        }

    private suspend fun getAssetsDirectory(directoriesManager: DirectoriesManager): File {
        return withContext(Dispatchers.IO) {
            File(directoriesManager.getSystemDirectory(), PPSSPP_ASSETS_FOLDER_NAME)
        }
    }

    companion object {
        // Cache-bust key; keep path tag in sync with GithubCoreDownloader.CORES_VERSION.
        const val PPSSPP_ASSETS_VERSION = "omnidroid-1"

        val PPSSPP_ASSETS_URL: Uri =
            Uri.parse("https://raw.githubusercontent.com/Ahmed-Abousaif/OmnidroidCores/")
                .buildUpon()
                .appendEncodedPath("2.1.0/assets/ppsspp.zip")
                .build()

        const val PPSSPP_ASSETS_VERSION_KEY = "ppsspp_assets_version_key"

        const val PPSSPP_ASSETS_FOLDER_NAME = "PPSSPP"
    }
}
