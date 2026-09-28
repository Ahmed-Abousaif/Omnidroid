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

class CemuAssetsManager : CoreID.AssetsManager {
    override suspend fun clearAssets(directoriesManager: DirectoriesManager) {
        withContext(Dispatchers.IO) {
            keysFiles(directoriesManager).forEach { it.delete() }
        }
    }

    override suspend fun retrieveAssetsIfNeeded(
        coreUpdaterApi: CoreUpdater.CoreManagerApi,
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
    ) {
        withContext(Dispatchers.IO) {
            if (!updatedRequested(directoriesManager, sharedPreferences)) {
                return@withContext
            }

            try {
                val response = coreUpdaterApi.downloadZip(CEMU_ASSETS_URL.toString())
                handleSuccess(directoriesManager, response, sharedPreferences)
            } catch (e: Throwable) {
                Timber.w(e, "CemuAssetsManager: Could not download assets from remote")
            }
        }
    }

    private fun handleSuccess(
        directoriesManager: DirectoriesManager,
        response: Response<ResponseBody>,
        sharedPreferences: SharedPreferences,
    ) {
        if (!response.isSuccessful) {
            throw Exception(response.errorBody()?.use { it.string() } ?: "Cemu assets download failed")
        }

        val keysBytes =
            response.body()?.use { responseBody ->
                readKeys(responseBody)
            } ?: throw Exception("Empty Cemu assets body")

        keysFiles(directoriesManager).forEach { destFile ->
            destFile.parentFile?.mkdirs()
            destFile.writeBytes(keysBytes)
        }

        sharedPreferences.edit()
            .putString(CEMU_ASSETS_VERSION_KEY, CEMU_ASSETS_VERSION)
            .commit()
    }

    private fun readKeys(responseBody: ResponseBody): ByteArray {
        ZipInputStream(responseBody.byteStream()).use { zipInputStream ->
            while (true) {
                val entry = zipInputStream.nextEntry ?: break
                val entryName = entry.name.replace('\\', '/')
                if (entry.isDirectory || entryName.endsWith("/")) {
                    continue
                }
                if (entryName.split('/').any { it == ".." }) {
                    continue
                }
                if (!entryName.substringAfterLast('/').equals(KEYS_FILE_NAME, ignoreCase = true)) {
                    continue
                }
                val bytes = zipInputStream.readBytes()
                if (bytes.isNotEmpty()) {
                    Timber.d("CemuAssetsManager: Read $KEYS_FILE_NAME (${bytes.size} bytes)")
                    return bytes
                }
            }
        }
        throw Exception("keys.txt missing from Cemu assets")
    }

    private fun updatedRequested(
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
    ): Boolean {
        val keysPresent = keysFiles(directoriesManager).all { it.exists() && it.length() > 0L }
        val currentVersion = sharedPreferences.getString(CEMU_ASSETS_VERSION_KEY, "none")
        val hasCurrentVersion = currentVersion == CEMU_ASSETS_VERSION
        return !keysPresent || !hasCurrentVersion
    }

    private fun keysFiles(directoriesManager: DirectoriesManager): List<File> {
        val systemDir = directoriesManager.getSystemDirectory()
        return listOf(
            File(systemDir, "Cemu/$KEYS_FILE_NAME"),
            File(systemDir, KEYS_FILE_NAME),
        )
    }

    companion object {
        const val CEMU_ASSETS_VERSION = "omnidroid-1"

        // Keep path tag in sync with GithubCoreDownloader.CORES_VERSION / OmnidroidCores tags.
        val CEMU_ASSETS_URL: Uri =
            Uri.parse("https://raw.githubusercontent.com/Ahmed-Abousaif/OmnidroidCores/")
                .buildUpon()
                .appendEncodedPath("2.4.0/assets/cemu.zip")
                .build()

        const val CEMU_ASSETS_VERSION_KEY = "cemu_assets_version_key"

        private const val KEYS_FILE_NAME = "keys.txt"
    }
}
