package com.omnidroid.sevenzip

import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class SevenZipArchive private constructor(
    private val handle: Long,
) : Closeable {
    private val closed = AtomicBoolean(false)

    fun entries(): List<SevenZipEntry> {
        checkOpen()
        val names = SevenZipNative.entryNames(handle)
        val info = SevenZipNative.entryInfo(handle)
        val count = minOf(names.size, info.size / 2)
        return List(count) { i ->
            SevenZipEntry(
                index = i,
                name = names[i],
                size = info[i * 2],
                flags = info[i * 2 + 1],
            )
        }
    }

    fun extract(
        index: Int,
        outFd: ParcelFileDescriptor,
        maxMemoryBytes: Long = defaultMaxMemoryBytes(),
    ): SevenZipError? {
        if (closed.get()) return SevenZipError.Cancelled
        val rc = SevenZipNative.extract(handle, index, outFd.fd, maxMemoryBytes)
        return SevenZipError.fromCode(rc)
    }

    fun progress(): Long = if (closed.get()) 0L else SevenZipNative.progress(handle)

    fun cancel() {
        if (!closed.get()) {
            SevenZipNative.cancel(handle)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            SevenZipNative.close(handle)
        }
    }

    private fun checkOpen() {
        check(!closed.get()) { "SevenZipArchive is already closed" }
    }

    companion object {
        const val DEFAULT_MAX_MEMORY_BYTES_64: Long = 256L * 1024L * 1024L
        const val DEFAULT_MAX_MEMORY_BYTES_32: Long = 128L * 1024L * 1024L

        fun defaultMaxMemoryBytes(): Long =
            if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) {
                DEFAULT_MAX_MEMORY_BYTES_64
            } else {
                DEFAULT_MAX_MEMORY_BYTES_32
            }

        fun open(archiveFd: ParcelFileDescriptor): SevenZipArchive {
            val result = SevenZipNative.open(archiveFd.fd)
            if (result <= 0L) {
                val err = SevenZipError.fromCode(result.toInt()) ?: SevenZipError.IoError
                throw SevenZipException(err)
            }
            return SevenZipArchive(result)
        }
    }
}
