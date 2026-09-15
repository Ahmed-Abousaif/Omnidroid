package com.swordfish.lemuroid.lib.core

import android.net.Uri
import android.os.Build
import com.swordfish.lemuroid.common.files.safeDelete
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

        // Use raw.githubusercontent.com so downloads work without relying on github.com redirects.
        val uri =
            BASE_URI.buildUpon()
                .appendEncodedPath("$CORES_VERSION/lemuroid_core_${coreID.coreName}/src/main/jniLibs/")
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
            val message = response.errorBody()?.use { it.string() } ?: "Download error"
            Timber.e("Download core response was unsuccessful: HTTP ${response.code()} $message")
            throw Exception(message)
        }
        val body = response.body() ?: throw Exception("Empty download body")
        body.use { responseBody ->
            responseBody.byteStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        if (!destFile.exists() || destFile.length() == 0L) {
            destFile.safeDelete()
            throw Exception("Downloaded core file was empty")
        }
    }

    private fun deleteOutdatedCores(mainCoresDirectory: File) {
        mainCoresDirectory.listFiles()
            ?.filter { it.name != CORES_VERSION }
            ?.forEach { it.deleteRecursively() }
    }

    companion object {
        const val CORES_VERSION = "1.17.0"
        private val BASE_URI = Uri.parse("https://raw.githubusercontent.com/Swordfish90/LemuroidCores/")
    }
}
