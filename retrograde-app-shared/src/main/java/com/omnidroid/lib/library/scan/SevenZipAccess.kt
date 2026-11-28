package com.omnidroid.lib.library.scan

data class SevenZipScanEntry(
    val index: Int,
    val name: String,
    val size: Long,
    val crc: String?,
    val isDirectory: Boolean,
    val isEncrypted: Boolean,
    val isSupportedCoder: Boolean,
) {
    val extension: String
        get() = ScanKeys.extension(name.replace('\\', '/').substringAfterLast('/'))
}

interface SevenZipReader : AutoCloseable {
    fun entries(): List<SevenZipScanEntry>

    fun openEntryHeader(
        index: Int,
        maxBytes: Long,
    ): RandomAccessBytes?

    fun computeEntryCrc(
        index: Int,
        mode: HashMode,
    ): String?
}

fun interface SevenZipOpener {
    fun open(): SevenZipReader?
}
