package com.omnidroid.lib.storage.local

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.omnidroid.lib.library.scan.CartridgeHasher
import com.omnidroid.lib.library.scan.HashMode
import com.omnidroid.lib.library.scan.RandomAccessBytes
import com.omnidroid.lib.library.scan.SevenZipReader
import com.omnidroid.lib.library.scan.SevenZipScanEntry
import com.omnidroid.sevenzip.ArchiveExtractor
import com.omnidroid.sevenzip.SevenZipArchive
import com.omnidroid.sevenzip.SevenZipEntry
import com.omnidroid.sevenzip.SevenZipError
import com.omnidroid.sevenzip.SevenZipException
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object SevenZipImages {
    private const val SCAN_TMP_SUBDIR = "7z-scan-tmp"
    private const val MAX_CARTRIDGE_HASH_BYTES = 64L * 1024L * 1024L

    fun open(
        context: Context,
        uri: Uri,
    ): SevenZipReader? {
        return try {
            val scanTempDir = File(context.cacheDir, SCAN_TMP_SUBDIR).apply { mkdirs() }
            var copiedTempFile: File? = null

            fun openFd(): ParcelFileDescriptor? {
                val localCopy = copiedTempFile
                return if (localCopy != null) {
                    ParcelFileDescriptor.open(localCopy, ParcelFileDescriptor.MODE_READ_ONLY)
                } else {
                    context.contentResolver.openFileDescriptor(uri, "r")
                }
            }

            fun openArchiveHandle(): SevenZipArchive? {
                val pfd = openFd() ?: return null
                return try {
                    pfd.use { SevenZipArchive.open(it) }
                } catch (e: SevenZipException) {
                    if (e.error is SevenZipError.NotSeekable && copiedTempFile == null) {
                        val copy = copyUriToTemp(context, uri, scanTempDir) ?: return null
                        copiedTempFile = copy
                        ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY).use { copyPfd ->
                            SevenZipArchive.open(copyPfd)
                        }
                    } else {
                        throw e
                    }
                }
            }

            val initialArchive = openArchiveHandle() ?: return null
            val nativeEntries = initialArchive.use { it.entries() }
            NativeSevenZipReader(
                nativeEntries = nativeEntries,
                openArchive = ::openArchiveHandle,
                scanTempDir = scanTempDir,
                onCloseTempFile = { copiedTempFile?.delete() },
            )
        } catch (e: UnsatisfiedLinkError) {
            Timber.w(e, "omnidroid-7z native library unavailable")
            null
        } catch (e: SevenZipException) {
            Timber.d(e, "Failed to open 7z for scanning: %s", uri)
            null
        } catch (e: RuntimeException) {
            Timber.w(e, "Error opening 7z for scanning: %s", uri)
            null
        }
    }

    private fun copyUriToTemp(
        context: Context,
        uri: Uri,
        tempDir: File,
    ): File? {
        val temp = File(tempDir, ".scan_${System.nanoTime()}${ArchiveExtractor.TEMP_FILE_SUFFIX}")
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { src ->
                temp.outputStream().use { dst ->
                    src.copyTo(dst)
                }
            }
            temp
        } catch (t: Throwable) {
            temp.delete()
            null
        }
    }

    private class NativeSevenZipReader(
        private val nativeEntries: List<SevenZipEntry>,
        private val openArchive: () -> SevenZipArchive?,
        private val scanTempDir: File,
        private val onCloseTempFile: () -> Unit,
    ) : SevenZipReader {
        private val closed = AtomicBoolean(false)
        private val scanEntries: List<SevenZipScanEntry> =
            nativeEntries.map { entry ->
                SevenZipScanEntry(
                    index = entry.index,
                    name = entry.name,
                    size = entry.size,
                    crc = entry.crc32Hex,
                    isDirectory = entry.isDirectory,
                    isEncrypted = entry.isEncrypted,
                    isSupportedCoder = entry.isSupportedCoder,
                )
            }

        override fun entries(): List<SevenZipScanEntry> = scanEntries

        override fun openEntryHeader(
            index: Int,
            maxBytes: Long,
        ): RandomAccessBytes? {
            if (closed.get()) return null
            val entry = nativeEntries.getOrNull(index) ?: return null
            if (entry.isDirectory || entry.size <= 0L) return HeaderWindowSource(ByteArray(0), 0L)
            val limit = minOf(entry.size, maxBytes).toInt()
            if (limit <= 0) return HeaderWindowSource(ByteArray(0), entry.size)

            val tempArchive = runCatching { openArchive() }.getOrNull() ?: return null
            val pipe = ParcelFileDescriptor.createPipe()
            val readEnd = pipe[0]
            val writeEnd = pipe[1]
            var extractErr: SevenZipError? = null

            val worker =
                Thread {
                    try {
                        writeEnd.use { w ->
                            extractErr = tempArchive.extract(index, w)
                        }
                    } finally {
                        runCatching { tempArchive.close() }
                    }
                }
            worker.start()

            val collected = ByteArrayOutputStream(minOf(limit, 64 * 1024))
            val buf = ByteArray(16 * 1024)
            try {
                ParcelFileDescriptor.AutoCloseInputStream(readEnd).use { input ->
                    while (collected.size() < limit) {
                        val want = minOf(buf.size, limit - collected.size())
                        val n = input.read(buf, 0, want)
                        if (n <= 0) break
                        collected.write(buf, 0, n)
                    }
                    tempArchive.cancel()
                    while (input.read(buf) > 0) {
                        // Drain until worker closes writeEnd to prevent SIGPIPE.
                    }
                }
            } finally {
                worker.join()
            }

            val bytes = collected.toByteArray()
            if (bytes.isEmpty()) return null
            if (bytes.size.toLong() < entry.size && extractErr != null && extractErr != SevenZipError.Cancelled) {
                return null
            }
            return HeaderWindowSource(bytes, entry.size)
        }

        override fun computeEntryCrc(
            index: Int,
            mode: HashMode,
        ): String? {
            if (closed.get()) return null
            val entry = nativeEntries.getOrNull(index) ?: return null
            if (entry.isDirectory || entry.size <= 0L || entry.size > MAX_CARTRIDGE_HASH_BYTES) return null

            val tempFile = File(scanTempDir, ".hash_${System.nanoTime()}${ArchiveExtractor.TEMP_FILE_SUFFIX}")
            return try {
                val archive = runCatching { openArchive() }.getOrNull() ?: return null
                val err =
                    archive.use { ar ->
                        ParcelFileDescriptor
                            .open(
                                tempFile,
                                ParcelFileDescriptor.MODE_CREATE or
                                    ParcelFileDescriptor.MODE_TRUNCATE or
                                    ParcelFileDescriptor.MODE_READ_WRITE,
                            ).use { outPfd ->
                                ar.extract(index, outPfd)
                            }
                    }
                if (err != null) return null
                val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                ChannelBytes(java.io.FileInputStream(pfd.fileDescriptor).channel, pfd).use { access ->
                    CartridgeHasher.hash(access, mode)
                }
            } catch (_: Throwable) {
                null
            } finally {
                tempFile.delete()
            }
        }

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                onCloseTempFile()
            }
        }
    }

    private class HeaderWindowSource(
        private val header: ByteArray,
        override val size: Long,
    ) : RandomAccessBytes {
        override fun read(
            offset: Long,
            length: Int,
        ): ByteArray {
            if (length <= 0 || offset < 0 || offset >= header.size.toLong()) return ByteArray(0)
            val start = offset.toInt()
            val end = minOf(header.size, start + length)
            return header.copyOfRange(start, end)
        }

        override fun close() = Unit
    }
}
