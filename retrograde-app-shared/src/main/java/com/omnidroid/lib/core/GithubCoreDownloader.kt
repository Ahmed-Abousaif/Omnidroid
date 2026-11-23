package com.omnidroid.lib.core

import android.net.Uri
import android.os.Build
import com.omnidroid.common.files.safeDelete
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.storage.DirectoriesManager
import timber.log.Timber
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GithubCoreDownloader(
    private val directoriesManager: DirectoriesManager,
    private val api: CoreUpdater.CoreManagerApi,
) {
    suspend fun retrieve(coreID: CoreID): File = downloadMutex.withLock {
        val mainCoresDirectory = directoriesManager.getCoresDirectory()
        findCached(mainCoresDirectory, coreID)?.let { return@withLock it }

        Timber.i("Downloading core $coreID from github")
        val coresDirectory =
            File(mainCoresDirectory, CORES_VERSION).apply {
                mkdirs()
            }

        val destFile = File(coresDirectory, coreID.libretroFileName)
        runCatching {
            deleteOutdatedCores(mainCoresDirectory)
        }

        // Use raw.githubusercontent.com so downloads work without relying on github.com redirects.
        val uri =
            BASE_URI.buildUpon()
                .appendEncodedPath("$CORES_VERSION/omnidroid_core_${coreID.coreName}/src/main/jniLibs/")
                .appendPath(Build.SUPPORTED_ABIS.first())
                .appendPath(coreID.libretroFileName)
                .build()

        val stagedFile = File(coresDirectory, "${coreID.libretroFileName}.part")
        try {
            downloadFile(uri, stagedFile)
            check(stagedFile.renameTo(destFile)) { "Could not install ${destFile.name}" }
            destFile
        } catch (error: Throwable) {
            destFile.safeDelete()
            stagedFile.safeDelete()
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
        destFile.setReadable(true, false)
        destFile.setExecutable(true, false)
    }

    private fun deleteOutdatedCores(mainCoresDirectory: File) {
        mainCoresDirectory.listFiles()
            ?.filter { it.name != CORES_VERSION }
            ?.forEach { it.deleteRecursively() }
    }

    companion object {
        private val downloadMutex = Mutex()
        fun findCached(
            coresDirectory: File,
            coreID: CoreID,
        ): File? =
            File(File(coresDirectory, CORES_VERSION), coreID.libretroFileName)
                .takeIf { it.isFile && it.length() > 0L }

        // Keep in sync with omnidroid-app versionName / OmnidroidCores git tags.
        const val CORES_VERSION = "3.1.0"
        val BASE_URI = Uri.parse("https://raw.githubusercontent.com/Ahmed-Abousaif/OmnidroidCores/")
    }
}
