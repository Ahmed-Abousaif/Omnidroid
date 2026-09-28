package com.omnidroid.lib.library.scan

internal data class ZipEntryInfo(
    val name: String,
    val crc: String,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val method: Int,
    val localHeaderOffset: Long,
) {
    val extension: String
        get() = ScanKeys.extension(name.substringAfterLast('/'))
}

internal object ZipDirectory {
    private const val EOCD_SIGNATURE = 0x06054b50L
    private const val CENTRAL_SIGNATURE = 0x02014b50L
    const val METHOD_STORED = 0
    const val METHOD_DEFLATE = 8

    fun read(source: RandomAccessBytes): List<ZipEntryInfo>? {
        val eocd = findEocd(source) ?: return null
        val count = u16(source, eocd + 10)
        val directorySize = u32(source, eocd + 12)
        val directoryOffset = u32(source, eocd + 16)
        if (count < 0 || directoryOffset < 0 || directorySize < 0) return null
        if (directoryOffset + directorySize > source.size) return null
        val directory = source.read(directoryOffset, directorySize.toInt())
        if (directory.size.toLong() != directorySize) return null
        val entries = ArrayList<ZipEntryInfo>(count)
        var cursor = 0
        repeat(count) {
            if (cursor + 46 > directory.size) return entries
            val signature = u32(directory, cursor)
            if (signature != CENTRAL_SIGNATURE) return entries
            val method = u16(directory, cursor + 10)
            val crc = u32(directory, cursor + 16)
            val compressed = u32(directory, cursor + 20)
            val uncompressed = u32(directory, cursor + 24)
            val nameLength = u16(directory, cursor + 28)
            val extraLength = u16(directory, cursor + 30)
            val commentLength = u16(directory, cursor + 32)
            val localOffset = u32(directory, cursor + 42)
            val nameStart = cursor + 46
            val nameEnd = nameStart + nameLength
            if (nameEnd > directory.size || method < 0 || crc < 0 || localOffset < 0) return entries
            val name = directory.copyOfRange(nameStart, nameEnd).toString(Charsets.UTF_8)
            entries +=
                ZipEntryInfo(
                    name = name,
                    crc = ScanKeys.crcHex(crc),
                    compressedSize = compressed,
                    uncompressedSize = uncompressed,
                    method = method,
                    localHeaderOffset = localOffset,
                )
            cursor = nameEnd + extraLength + commentLength
        }
        return entries
    }

    fun dominantEntry(entries: List<ZipEntryInfo>): ZipEntryInfo? {
        val files = entries.filter { !it.name.endsWith("/") && it.uncompressedSize > 0 }
        val total = files.sumOf { it.compressedSize }.takeIf { it > 0 } ?: return null
        val largest = files.maxByOrNull { it.compressedSize } ?: return null
        return largest.takeIf { largest.compressedSize.toDouble() / total.toDouble() > 0.9 }
    }

    private fun findEocd(source: RandomAccessBytes): Long? {
        val window = minOf(source.size, 66L * 1024).toInt()
        if (window < 22) return null
        val start = source.size - window
        val bytes = source.read(start, window)
        for (index in bytes.size - 22 downTo 0) {
            if (u32(bytes, index) == EOCD_SIGNATURE) return start + index
        }
        return null
    }

    private fun u16(
        bytes: ByteArray,
        offset: Int,
    ): Int {
        if (offset + 1 >= bytes.size) return -1
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun u32(
        bytes: ByteArray,
        offset: Int,
    ): Long {
        if (offset + 3 >= bytes.size) return -1
        return (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)
    }

    private fun u16(
        source: RandomAccessBytes,
        offset: Long,
    ): Int = source.u16le(offset)

    private fun u32(
        source: RandomAccessBytes,
        offset: Long,
    ): Long = source.u32le(offset)
}
