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
            val pcsx2BiosDir = File(systemDir, "pcsx2/bios").apply { mkdirs() }

            // 1. Sync any existing local PS2 BIOS from system/ to system/pcsx2/bios/
            val ps2BiosNames = listOf(
                "scph39001.bin",
                "scph70012.bin",
                "scph77001.bin",
            )
            var hasLocalBios = false
            for (biosName in ps2BiosNames) {
                val src = File(systemDir, biosName)
                val dest = File(pcsx2BiosDir, biosName)
                if (src.exists()) {
                    hasLocalBios = true
                    if (!dest.exists() || dest.length() != src.length()) {
                        try {
                            src.copyTo(dest, overwrite = true)
                            Timber.d("PCEE2AssetsManager: Copied $biosName to ${dest.path}")
                        } catch (e: Exception) {
                            Timber.e(e, "PCEE2AssetsManager: Error copying $biosName to $dest")
                        }
                    }
                } else if (dest.exists()) {
                    hasLocalBios = true
                }
            }

            // 2. If no BIOS is found and update/download is needed, pull the remote assets
            if (updatedRequested(directoriesManager, sharedPreferences, hasLocalBios)) {
                try {
                    val response = coreUpdaterApi.downloadZip(PCEE2_ASSETS_URL.toString())
                    handleSuccess(systemDir, pcsx2BiosDir, response, sharedPreferences)
                } catch (e: Throwable) {
                    Timber.w(e, "PCEE2AssetsManager: Could not download assets from remote")
                }
            }
        }
    }

    private suspend fun handleSuccess(
        systemDir: File,
        pcsx2BiosDir: File,
        response: Response<ResponseBody>,
        sharedPreferences: SharedPreferences,
    ) {
        if (!response.isSuccessful) {
            throw Exception(response.errorBody()?.use { it.string() } ?: "PCEE2 assets download failed")
        }

        response.body()?.use { responseBody ->
            ZipInputStream(responseBody.byteStream()).use { zipInputStream ->
                while (true) {
                    val entry = zipInputStream.nextEntry ?: break
                    Timber.d("PCEE2AssetsManager: Extracting ${entry.name}")

                    val destFile = if (entry.name.endsWith(".bin", ignoreCase = true)) {
                        // Place BIOS binary in pcsx2/bios/ and mirror to system/
                        val baseName = entry.name.substringAfterLast("/")
                        val pcsx2File = File(pcsx2BiosDir, baseName)
                        val systemFile = File(systemDir, baseName)

                        pcsx2File.outputStream().use { output ->
                            zipInputStream.copyTo(output)
                        }
                        try {
                            pcsx2File.copyTo(systemFile, overwrite = true)
                            // If named with full model name (e.g. SCPH-39001_BIOS_V7_USA_160.BIN), also alias to scph39001.bin
                            if (baseName.contains("39001", ignoreCase = true) || baseName.contains("scph", ignoreCase = true)) {
                                val standardName = "scph39001.bin"
                                pcsx2File.copyTo(File(pcsx2BiosDir, standardName), overwrite = true)
                                pcsx2File.copyTo(File(systemDir, standardName), overwrite = true)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error copying extracted bios to system root")
                        }
                        continue
                    } else if (entry.name.startsWith("pcsx2/", ignoreCase = true)) {
                        File(systemDir, entry.name)
                    } else {
                        File(systemDir, "pcsx2/${entry.name}")
                    }

                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        destFile.outputStream().use { output ->
                            zipInputStream.copyTo(output)
                        }
                    }
                }
            }
        } ?: throw Exception("Empty PCEE2 assets body")

        sharedPreferences.edit()
            .putString(PCEE2_ASSETS_VERSION_KEY, PCEE2_ASSETS_VERSION)
            .commit()
    }

    private fun updatedRequested(
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
        hasLocalBios: Boolean,
    ): Boolean {
        val pcsx2BiosDir = File(directoriesManager.getSystemDirectory(), "pcsx2/bios")
        val biosExists = hasLocalBios || (pcsx2BiosDir.exists() && (pcsx2BiosDir.listFiles()?.isNotEmpty() == true))
        val currentVersion = sharedPreferences.getString(PCEE2_ASSETS_VERSION_KEY, "none")
        val hasCurrentVersion = currentVersion == PCEE2_ASSETS_VERSION

        return !biosExists || !hasCurrentVersion
    }

    companion object {
        const val PCEE2_ASSETS_VERSION = "omnidroid-1"

        // Keep path tag in sync with GithubCoreDownloader.CORES_VERSION / OmnidroidCores tags.
        val PCEE2_ASSETS_URL: Uri =
            Uri.parse("https://raw.githubusercontent.com/Ahmed-Abousaif/OmnidroidCores/")
                .buildUpon()
                .appendEncodedPath("2.0.1/assets/pcee2.zip")
                .build()

        const val PCEE2_ASSETS_VERSION_KEY = "pcee2_assets_version_key"
    }
}
