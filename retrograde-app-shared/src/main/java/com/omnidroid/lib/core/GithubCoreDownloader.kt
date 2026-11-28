package com.omnidroid.lib.core

import android.net.Uri
import android.os.Build
import com.omnidroid.common.files.safeDelete
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.File
import java.util.zip.ZipInputStream

class GithubCoreDownloader(
    private val directoriesManager: DirectoriesManager,
    private val api: CoreUpdater.CoreManagerApi,
) {
    suspend fun retrieve(coreID: CoreID): File =
        downloadMutex.withLock {
            val mainCoresDirectory = directoriesManager.getCoresDirectory()
            findCached(mainCoresDirectory, coreID)?.let { return@withLock it }

            Timber.i("Downloading core $coreID from github")
            val version = versionFor(coreID)
            val coresDirectory =
                File(mainCoresDirectory, version).apply {
                    mkdirs()
                }

            val destFile = File(coresDirectory, coreID.libretroFileName)
            runCatching {
                deleteOutdatedCores(mainCoresDirectory)
            }

            // Use raw.githubusercontent.com so downloads work without relying on github.com redirects.
            val uri =
                BASE_URI
                    .buildUpon()
                    .appendEncodedPath("$version/omnidroid_core_${coreID.coreName}/src/main/jniLibs/")
                    .appendPath(Build.SUPPORTED_ABIS.first())
                    .appendPath(coreID.libretroFileName)
                    .build()

            val stagedFile = File(coresDirectory, "${coreID.libretroFileName}.part")
            try {
                if (coreID.buildbotName != null) {
                    downloadBuildbotCore(coreID, stagedFile)
                } else {
                    downloadFile(uri, stagedFile)
                }
                check(stagedFile.renameTo(destFile)) { "Could not install ${destFile.name}" }
                destFile
            } catch (error: Throwable) {
                destFile.safeDelete()
                stagedFile.safeDelete()
                throw error
            }
        }

    private suspend fun downloadBuildbotCore(
        coreID: CoreID,
        destination: File,
    ) {
        val upstream = requireNotNull(coreID.buildbotName)
        val fileName = "${upstream}_libretro_android.so"
        val url = "https://buildbot.libretro.com/nightly/android/latest/${Build.SUPPORTED_ABIS.first()}/$fileName.zip"
        val response = api.downloadZip(url)
        check(response.isSuccessful) { "Core download failed: HTTP ${response.code()}" }
        val body = response.body() ?: error("Empty core archive")
        body.use {
            ZipInputStream(it.byteStream()).use { zip ->
                var entry = zip.nextEntry
                var found = false
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.substringAfterLast('/') == fileName) {
                        destination.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var total = 0L
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                total += count
                                check(total <= 256L * 1024 * 1024) { "Core archive is too large" }
                                output.write(buffer, 0, count)
                            }
                        }
                        found = true
                        break
                    }
                    entry = zip.nextEntry
                }
                check(found && destination.length() > 4) { "Core archive did not contain $fileName" }
            }
        }
        destination.inputStream().use { input ->
            check(input.read() == 0x7f && input.read() == 0x45 && input.read() == 0x4c && input.read() == 0x46) {
                "Downloaded core is not an ELF library"
            }
        }
        destination.setReadable(true, false)
        destination.setExecutable(true, false)
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
        mainCoresDirectory
            .listFiles()
            ?.filter { it.name !in CoreID.values().map(::versionFor).toSet() }
            ?.forEach { it.deleteRecursively() }
    }

    companion object {
        private val downloadMutex = Mutex()

        fun findCached(
            coresDirectory: File,
            coreID: CoreID,
        ): File? =
            File(File(coresDirectory, versionFor(coreID)), coreID.libretroFileName)
                .takeIf { it.isFile && it.length() > 0L }

        fun versionFor(coreID: CoreID): String = CORES_VERSION

        // Keep in sync with omnidroid-app versionName / OmnidroidCores git tags.
        const val CORES_VERSION = "3.2.0"
        val BASE_URI = Uri.parse("https://raw.githubusercontent.com/Ahmed-Abousaif/OmnidroidCores/")
    }
}
