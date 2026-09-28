package com.omnidroid.lib.library.scan

import java.util.zip.Inflater

internal object ZipEntryHasher {
    private const val MAX_NORMALISED = 80L * 1024 * 1024

    fun hash(
        source: RandomAccessBytes,
        entry: ZipEntryInfo,
        mode: HashMode,
    ): String? {
        if (mode != HashMode.RAW && entry.uncompressedSize > MAX_NORMALISED) return null
        val data = readEntry(source, entry, entry.uncompressedSize) ?: return null
        return CartridgeHasher.hash(ByteArraySource(data), mode)
    }

    fun readEntry(
        source: RandomAccessBytes,
        entry: ZipEntryInfo,
        limit: Long,
    ): ByteArray? {
        if (entry.uncompressedSize < 0 || entry.uncompressedSize > limit) return null
        val local = source.read(entry.localHeaderOffset, 30)
        if (local.size < 30) return null
        val nameLength = (local[26].toInt() and 0xFF) or ((local[27].toInt() and 0xFF) shl 8)
        val extraLength = (local[28].toInt() and 0xFF) or ((local[29].toInt() and 0xFF) shl 8)
        val dataOffset = entry.localHeaderOffset + 30 + nameLength + extraLength
        val compressed = source.read(dataOffset, entry.compressedSize.toInt())
        if (compressed.size.toLong() != entry.compressedSize) return null
        if (entry.method == ZipDirectory.METHOD_STORED) return compressed
        if (entry.method != ZipDirectory.METHOD_DEFLATE) return null
        val inflater = Inflater(true)
        return try {
            inflater.setInput(compressed)
            val out = ByteArray(entry.uncompressedSize.toInt())
            var filled = 0
            while (filled < out.size && !inflater.finished()) {
                val wrote = inflater.inflate(out, filled, out.size - filled)
                if (wrote == 0) break
                filled += wrote
            }
            if (filled == 0) null else out.copyOf(filled)
        } catch (_: Exception) {
            null
        } finally {
            inflater.end()
        }
    }
}
