package com.swordfish.lemuroid.lib.core

import android.net.Uri
import android.os.Build
import com.swordfish.lemuroid.common.files.safeDelete
import com.swordfish.lemuroid.common.kotlin.writeToFile
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import timber.log.Timber
import java.io.File

class GithubCoreDownloader(
    private val directoriesManager: DirectoriesManager,
    private val api: CoreUpdater.CoreManagerApi,
) {
    suspend fun retrieve(coreID: CoreID): File {
        Timber.i("Downloading core $coreID from github")

        val mainCoresDirectory = directoriesManager.getCoresDirectory()
        val coresDirectory =
            File(mainCoresDirectory, CORES_VERSION).apply {
                mkdirs()
            }

        val destFile = File(coresDirectory, coreID.libretroFileName)
        if (destFile.exists()) {
            return destFile
        }

        runCatching {
            deleteOutdatedCores(mainCoresDirectory)
        }

        val uri =
            BASE_URI.buildUpon()
                .appendEncodedPath("raw/$CORES_VERSION/lemuroid_core_${coreID.coreName}/src/main/jniLibs/")
                .appendPath(Build.SUPPORTED_ABIS.first())
                .appendPath(coreID.libretroFileName)
                .build()

        try {
            downloadFile(uri, destFile)
            return destFile
        } catch (error: Throwable) {
            destFile.safeDelete()
            throw error
        }
    }

    private suspend fun downloadFile(
        uri: Uri,
        destFile: File,
    ) {
        val response = api.downloadFile(uri.toString())
        if (!response.isSuccessful) {
            Timber.e("Download core response was unsuccessful")
            throw Exception(response.errorBody()?.string() ?: "Download error")
        }
        response.body()?.writeToFile(destFile)
    }

    private fun deleteOutdatedCores(mainCoresDirectory: File) {
        mainCoresDirectory.listFiles()
            ?.filter { it.name != CORES_VERSION }
            ?.forEach { it.deleteRecursively() }
    }

    companion object {
        const val CORES_VERSION = "1.17.0"
        private val BASE_URI = Uri.parse("https://github.com/Swordfish90/LemuroidCores/")
    }
}
