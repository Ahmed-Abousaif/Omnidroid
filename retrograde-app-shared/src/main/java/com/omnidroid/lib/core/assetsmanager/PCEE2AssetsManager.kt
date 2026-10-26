package com.omnidroid.lib.core.assetsmanager

import android.content.SharedPreferences
import android.net.Uri
import com.omnidroid.lib.bios.BiosManager
import com.omnidroid.lib.bios.Ps2Bios
import com.omnidroid.lib.core.CoreUpdater
import com.omnidroid.lib.core.GithubCoreDownloader
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import timber.log.Timber
import java.io.File
import java.util.zip.ZipFile

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
            systemDir.mkdirs()
            if (BiosManager.refreshPs2Library(systemDir).isNotEmpty()) return@withContext
            try {
                val response = coreUpdaterApi.downloadZip(PCEE2_ASSETS_URL.toString())
                installServedBios(systemDir, response, sharedPreferences, ServedRegion.USA)
            } catch (e: Throwable) {
                Timber.w(e, "PCEE2AssetsManager: Could not download PS2 BIOS")
            }
        }
    }

    private fun installServedBios(
        systemDir: File,
        response: Response<ResponseBody>,
        sharedPreferences: SharedPreferences,
        region: ServedRegion,
    ) {
        if (!response.isSuccessful) {
            throw Exception(response.errorBody()?.use { it.string() } ?: "PS2 BIOS download failed")
        }
        val body = response.body() ?: throw Exception("Empty PS2 BIOS download")
        val archive = File.createTempFile("ps2-bios", ".zip", systemDir)
        try {
            body.use { responseBody ->
                archive.outputStream().use { output -> responseBody.byteStream().copyTo(output) }
            }
            ZipFile(archive).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory || !entry.name.endsWith(".bin", ignoreCase = true)) continue
                    if (!region.matches(entry.name)) continue
                    val temp = File.createTempFile("ps2-bios", ".bin", systemDir)
                    try {
                        zip.getInputStream(entry).use { input ->
                            temp.outputStream().use { output -> input.copyTo(output) }
                        }
                        if (BiosManager.adoptPs2File(systemDir, temp)) {
                            sharedPreferences.edit().putString(PCEE2_ASSETS_VERSION_KEY, PCEE2_ASSETS_VERSION).commit()
                            return
                        }
                    } finally {
                        temp.delete()
                    }
                }
            }
            throw Exception("PS2 BIOS package has no ${region.name} image")
        } finally {
            archive.delete()
        }
    }

    suspend fun installServedRegion(
        coreUpdaterApi: CoreUpdater.CoreManagerApi,
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
        region: ServedRegion,
    ) {
        withContext(Dispatchers.IO) {
            val systemDir = directoriesManager.getSystemDirectory()
            systemDir.mkdirs()
            val response = coreUpdaterApi.downloadZip(PCEE2_ASSETS_URL.toString())
            installServedBios(systemDir, response, sharedPreferences, region)
        }
    }

    companion object {
        const val PCEE2_ASSETS_VERSION = "omnidroid-1"

        // Keep path tag in sync with GithubCoreDownloader.CORES_VERSION / OmnidroidCores tags.
        val PCEE2_ASSETS_URL: Uri =
            Uri.parse("https://raw.githubusercontent.com/Ahmed-Abousaif/OmnidroidCores/")
                .buildUpon()
                .appendEncodedPath("${GithubCoreDownloader.CORES_VERSION}/assets/pcee2.zip")
                .build()

        const val PCEE2_ASSETS_VERSION_KEY = "pcee2_assets_version_key"
    }

    enum class ServedRegion {
        USA,
        EUROPE,
        JAPAN,
        ;

        fun matches(entryName: String): Boolean {
            val name = entryName.uppercase()
            return when (this) {
                USA -> "39001" in name
                EUROPE -> "39004" in name
                JAPAN -> "30000" in name
            }
        }

        fun fileName(): String =
            when (this) {
                USA -> Ps2Bios.USA
                EUROPE -> Ps2Bios.EUROPE
                JAPAN -> Ps2Bios.JAPAN_SERVED
            }
    }
}
