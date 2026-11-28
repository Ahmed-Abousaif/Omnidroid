package com.omnidroid.sevenzip

import android.os.ParcelFileDescriptor
import android.os.Process
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ArchiveExtractor {
    const val TEMP_FILE_SUFFIX = ".7zextract.tmp"
    private const val PROGRESS_POLL_INTERVAL_MS = 250L

    fun sweepStaleTempFiles(rootDir: File) {
        if (!rootDir.exists()) return
        rootDir
            .walkTopDown()
            .filter { it.isFile && it.name.endsWith(TEMP_FILE_SUFFIX) }
            .forEach { runCatching { it.delete() } }
    }

    suspend fun <T> withSeekableArchive(
        openSourceFd: () -> ParcelFileDescriptor?,
        tempDir: File,
        openStreamFallback: (() -> InputStream?)? = null,
        block: suspend (SevenZipArchive) -> T,
    ): T {
        var tempArchiveCopy: File? = null
        try {
            val primaryPfd =
                openSourceFd()
                    ?: throw SevenZipException(SevenZipError.IoError)
            val archive =
                try {
                    primaryPfd.use { pfd -> SevenZipArchive.open(pfd) }
                } catch (e: SevenZipException) {
                    if (e.error is SevenZipError.NotSeekable && openStreamFallback != null) {
                        val copy = copyStreamToTempFile(openStreamFallback, tempDir)
                        tempArchiveCopy = copy
                        ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY).use { copyPfd ->
                            SevenZipArchive.open(copyPfd)
                        }
                    } else {
                        throw e
                    }
                }
            return archive.use { block(it) }
        } finally {
            tempArchiveCopy?.delete()
        }
    }

    fun <T> withSeekableArchiveBlocking(
        openSourceFd: () -> ParcelFileDescriptor?,
        tempDir: File,
        openStreamFallback: (() -> InputStream?)? = null,
        block: (SevenZipArchive) -> T,
    ): T {
        var tempArchiveCopy: File? = null
        try {
            val primaryPfd =
                openSourceFd()
                    ?: throw SevenZipException(SevenZipError.IoError)
            val archive =
                try {
                    primaryPfd.use { pfd -> SevenZipArchive.open(pfd) }
                } catch (e: SevenZipException) {
                    if (e.error is SevenZipError.NotSeekable && openStreamFallback != null) {
                        val copy = copyStreamToTempFile(openStreamFallback, tempDir)
                        tempArchiveCopy = copy
                        ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY).use { copyPfd ->
                            SevenZipArchive.open(copyPfd)
                        }
                    } else {
                        throw e
                    }
                }
            return archive.use(block)
        } finally {
            tempArchiveCopy?.delete()
        }
    }

    suspend fun extractEntryToFile(
        openSourceFd: () -> ParcelFileDescriptor?,
        entryName: String,
        destFile: File,
        maxMemoryBytes: Long = SevenZipArchive.defaultMaxMemoryBytes(),
        openStreamFallback: (() -> InputStream?)? = null,
        onProgress: ((decodedBytes: Long, totalBytes: Long) -> Unit)? = null,
    ) {
        val parentDir = destFile.parentFile ?: throw IOException("Destination has no parent directory: $destFile")
        parentDir.mkdirs()

        withSeekableArchive(openSourceFd, parentDir, openStreamFallback) { archive ->
            val entries = archive.entries()
            val entry =
                findMatchingEntry(entries, entryName)
                    ?: throw SevenZipException(SevenZipError.IndexOutOfRange)

            extractResolvedEntry(
                archive = archive,
                entry = entry,
                destFile = destFile,
                maxMemoryBytes = maxMemoryBytes,
                onProgress = onProgress,
            )
        }
    }

    fun extractEntryToFileBlocking(
        openSourceFd: () -> ParcelFileDescriptor?,
        entryName: String,
        destFile: File,
        maxMemoryBytes: Long = SevenZipArchive.defaultMaxMemoryBytes(),
        openStreamFallback: (() -> InputStream?)? = null,
    ) {
        val parentDir = destFile.parentFile ?: throw IOException("Destination has no parent directory: $destFile")
        parentDir.mkdirs()

        withSeekableArchiveBlocking(openSourceFd, parentDir, openStreamFallback) { archive ->
            val entry =
                findMatchingEntry(archive.entries(), entryName)
                    ?: throw SevenZipException(SevenZipError.IndexOutOfRange)
            val tempFile = createTempOutputFile(parentDir, destFile.name)
            try {
                val err =
                    ParcelFileDescriptor
                        .open(
                            tempFile,
                            ParcelFileDescriptor.MODE_CREATE or
                                ParcelFileDescriptor.MODE_TRUNCATE or
                                ParcelFileDescriptor.MODE_READ_WRITE,
                        ).use { outPfd ->
                            archive.extract(entry.index, outPfd, maxMemoryBytes)
                        }
                if (err != null) {
                    throw SevenZipException(err)
                }
                promoteTempFile(tempFile, destFile)
            } catch (t: Throwable) {
                tempFile.delete()
                throw t
            }
        }
    }

    suspend fun extractResolvedEntry(
        archive: SevenZipArchive,
        entry: SevenZipEntry,
        destFile: File,
        maxMemoryBytes: Long = SevenZipArchive.defaultMaxMemoryBytes(),
        onProgress: ((decodedBytes: Long, totalBytes: Long) -> Unit)? = null,
    ) {
        val parentDir = destFile.parentFile ?: throw IOException("Destination has no parent directory: $destFile")
        parentDir.mkdirs()
        val tempFile = createTempOutputFile(parentDir, destFile.name)
        try {
            withContext(Dispatchers.IO) {
                coroutineScope {
                    val poller: Job? =
                        onProgress?.let { progressCallback ->
                            launch {
                                var lastReported = -1L
                                while (isActive) {
                                    val decoded = archive.progress()
                                    if (decoded != lastReported) {
                                        lastReported = decoded
                                        progressCallback(decoded, entry.size)
                                    }
                                    delay(PROGRESS_POLL_INTERVAL_MS)
                                }
                            }
                        }

                    val outcomeRef = AtomicReference<Result<SevenZipError?>>()
                    val worker =
                        launch(Dispatchers.IO) {
                            val result =
                                runCatching {
                                    ParcelFileDescriptor
                                        .open(
                                            tempFile,
                                            ParcelFileDescriptor.MODE_CREATE or
                                                ParcelFileDescriptor.MODE_TRUNCATE or
                                                ParcelFileDescriptor.MODE_READ_WRITE,
                                        ).use { outPfd ->
                                            archive.extract(entry.index, outPfd, maxMemoryBytes)
                                        }
                                }
                            outcomeRef.set(result)
                        }

                    try {
                        val err =
                            suspendCancellableCoroutine<SevenZipError?> { continuation ->
                                continuation.invokeOnCancellation { archive.cancel() }
                                worker.invokeOnCompletion { cause ->
                                    if (!continuation.isActive) return@invokeOnCompletion
                                    val result = outcomeRef.get()
                                    when {
                                        result != null ->
                                            result.fold(
                                                onSuccess = { continuation.resume(it) },
                                                onFailure = { continuation.resumeWithException(it) },
                                            )
                                        cause != null -> continuation.resumeWithException(cause)
                                        else -> continuation.resume(SevenZipError.Cancelled)
                                    }
                                }
                            }
                        if (err == SevenZipError.Cancelled) {
                            throw CancellationException("7z extraction cancelled")
                        }
                        if (err != null) {
                            throw SevenZipException(err)
                        }
                        onProgress?.invoke(entry.size, entry.size)
                    } finally {
                        archive.cancel()
                        withContext(NonCancellable) {
                            poller?.cancelAndJoin()
                            worker.join()
                        }
                    }
                }
            }
            promoteTempFile(tempFile, destFile)
        } catch (t: Throwable) {
            tempFile.delete()
            throw t
        }
    }

    fun findMatchingEntry(
        entries: List<SevenZipEntry>,
        entryName: String,
    ): SevenZipEntry? {
        val normalizedTarget = entryName.replace('\\', '/')
        val simpleTarget = normalizedTarget.substringAfterLast('/')
        return entries.firstOrNull { !it.isDirectory && it.name.replace('\\', '/') == normalizedTarget }
            ?: entries.firstOrNull { !it.isDirectory && it.simpleName == simpleTarget }
    }

    private fun copyStreamToTempFile(
        openStream: () -> InputStream?,
        tempDir: File,
    ): File {
        tempDir.mkdirs()
        val tempCopy = createTempOutputFile(tempDir, "source.7z")
        try {
            val stream =
                openStream()
                    ?: throw SevenZipException(SevenZipError.IoError)
            stream.use { input ->
                tempCopy.outputStream().use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            return tempCopy
        } catch (t: Throwable) {
            tempCopy.delete()
            throw t
        }
    }

    private fun createTempOutputFile(
        parentDir: File,
        baseName: String,
    ): File {
        val safeBase = baseName.replace('\\', '/').substringAfterLast('/').ifBlank { "entry" }
        return File(parentDir, ".$safeBase.${Process.myPid()}.${System.nanoTime()}$TEMP_FILE_SUFFIX")
    }

    private fun promoteTempFile(
        tempFile: File,
        destFile: File,
    ) {
        if (tempFile.renameTo(destFile)) {
            return
        }
        if (destFile.exists() && destFile.delete() && tempFile.renameTo(destFile)) {
            return
        }
        throw IOException("Failed to rename $tempFile to $destFile")
    }
}
