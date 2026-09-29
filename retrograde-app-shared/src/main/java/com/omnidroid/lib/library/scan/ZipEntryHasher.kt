package com.omnidroid.lib.library.scan

import java.util.zip.CRC32
import java.util.zip.Inflater

internal object ZipEntryHasher {
    private const val MAX_NORMALISED = 64L * 1024 * 1024

    fun hash(
        source: RandomAccessBytes,
        entry: ZipEntryInfo,
        mode: HashMode,
    ): String? {
        if (entry.uncompressedSize <= 0) return null
        if (mode == HashMode.RAW || mode == HashMode.SNES) {
            return when (entry.method) {
                ZipDirectory.METHOD_STORED -> {
                    val opened = open(source, entry) ?: return null
                    opened.use { CartridgeHasher.hash(it, mode) }
                }
                ZipDirectory.METHOD_DEFLATE -> hashDeflateStreaming(source, entry, mode)
                else -> null
            }
        }
        if (entry.uncompressedSize > MAX_NORMALISED) return null
        val data = readEntry(source, entry, entry.uncompressedSize) ?: return null
        return CartridgeHasher.hash(ByteArraySource(data), mode)
    }

    private fun hashDeflateStreaming(
        source: RandomAccessBytes,
        entry: ZipEntryInfo,
        mode: HashMode,
    ): String? {
        val dataOffset = dataOffset(source, entry) ?: return null
        val inputEnd = dataOffset + entry.compressedSize
        var inputOffset = dataOffset
        val inflater = Inflater(true)
        val crc = CRC32()
        val inBuf = ByteArray(16 * 1024)
        val outBuf = ByteArray(64 * 1024)
        val skip = if (mode == HashMode.SNES && entry.uncompressedSize % 1024L == 512L) 512L else 0L
        var skipped = 0L
        try {
            while (!inflater.finished()) {
                if (inflater.needsInput()) {
                    if (inputOffset >= inputEnd) break
                    val toRead = minOf(inBuf.size.toLong(), inputEnd - inputOffset).toInt()
                    val got = source.read(inputOffset, toRead)
                    if (got.isEmpty()) break
                    inflater.setInput(got)
                    inputOffset += got.size
                }
                val wrote =
                    try {
                        inflater.inflate(outBuf)
                    } catch (_: Exception) {
                        return null
                    }
                if (wrote == 0) {
                    if (inflater.finished() || (!inflater.needsInput() && inputOffset >= inputEnd)) break
                    continue
                }
                if (mode == HashMode.RAW) {
                    crc.update(outBuf, 0, wrote)
                } else if (mode == HashMode.SNES) {
                    var offset = 0
                    var len = wrote
                    if (skipped < skip) {
                        val needed = (skip - skipped).toInt()
                        val skipNow = minOf(needed, len)
                        offset += skipNow
                        len -= skipNow
                        skipped += skipNow
                    }
                    if (len > 0) {
                        crc.update(outBuf, offset, len)
                    }
                }
            }
            return ScanKeys.crcHex(crc.value)
        } finally {
            inflater.end()
        }
    }

    fun open(
        source: RandomAccessBytes,
        entry: ZipEntryInfo,
    ): RandomAccessBytes? {
        if (entry.uncompressedSize <= 0) return null
        val dataOffset = dataOffset(source, entry) ?: return null
        return when (entry.method) {
            ZipDirectory.METHOD_STORED -> WindowSource(source, dataOffset, entry.uncompressedSize)
            ZipDirectory.METHOD_DEFLATE ->
                DeflateSource(source, dataOffset, entry.compressedSize, entry.uncompressedSize)
            else -> null
        }
    }

    private fun dataOffset(
        source: RandomAccessBytes,
        entry: ZipEntryInfo,
    ): Long? {
        val local = source.read(entry.localHeaderOffset, 30)
        if (local.size < 30) return null
        val nameLength = (local[26].toInt() and 0xFF) or ((local[27].toInt() and 0xFF) shl 8)
        val extraLength = (local[28].toInt() and 0xFF) or ((local[29].toInt() and 0xFF) shl 8)
        return entry.localHeaderOffset + 30 + nameLength + extraLength
    }

    fun readEntry(
        source: RandomAccessBytes,
        entry: ZipEntryInfo,
        limit: Long,
    ): ByteArray? {
        if (entry.uncompressedSize < 0 || entry.uncompressedSize > limit) return null
        val dataOffset = dataOffset(source, entry) ?: return null
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

private class WindowSource(
    private val parent: RandomAccessBytes,
    private val dataOffset: Long,
    override val size: Long,
) : RandomAccessBytes {
    override fun read(
        offset: Long,
        length: Int,
    ): ByteArray {
        if (length <= 0 || offset < 0 || offset >= size) return ByteArray(0)
        val count = minOf(length.toLong(), size - offset).toInt()
        return parent.read(dataOffset + offset, count)
    }

    override fun close() = Unit
}

private class DeflateSource(
    private val parent: RandomAccessBytes,
    private val dataOffset: Long,
    compressedSize: Long,
    override val size: Long,
) : RandomAccessBytes {
    private val inflater = Inflater(true)
    private val inputEnd = dataOffset + compressedSize
    private var inputOffset = dataOffset
    private var buffer = ByteArray(minOf(size, 64 * 1024).toInt())
    private var bufferedLength = 0
    private var done = false

    override fun read(
        offset: Long,
        length: Int,
    ): ByteArray {
        if (length <= 0 || offset < 0 || offset >= size) return ByteArray(0)
        val end = minOf(size, offset + length)
        if (end > MAX_HEADER_BYTES) return ByteArray(0)
        ensure(end.toInt())
        if (offset >= bufferedLength) return ByteArray(0)
        val available = minOf(end.toInt(), bufferedLength) - offset.toInt()
        if (available <= 0) return ByteArray(0)
        val result = ByteArray(available)
        System.arraycopy(buffer, offset.toInt(), result, 0, available)
        return result
    }

    override fun close() {
        inflater.end()
    }

    private fun ensure(target: Int) {
        val clampedTarget = minOf(target.toLong(), minOf(size, MAX_HEADER_BYTES)).toInt()
        if (clampedTarget > buffer.size) {
            var newCap = buffer.size.toLong()
            while (newCap < clampedTarget) {
                newCap = minOf(MAX_HEADER_BYTES, newCap * 2)
            }
            val grown = ByteArray(newCap.toInt())
            System.arraycopy(buffer, 0, grown, 0, bufferedLength)
            buffer = grown
        }

        val inChunk = ByteArray(16 * 1024)
        while (bufferedLength < clampedTarget && !done) {
            if (inflater.needsInput()) {
                if (inputOffset >= inputEnd) {
                    done = true
                    break
                }
                val count = minOf(inChunk.size.toLong(), inputEnd - inputOffset).toInt()
                val got = parent.read(inputOffset, count)
                if (got.isEmpty()) {
                    done = true
                    break
                }
                inflater.setInput(got)
                inputOffset += got.size
            }
            val wrote =
                try {
                    inflater.inflate(buffer, bufferedLength, buffer.size - bufferedLength)
                } catch (_: Exception) {
                    done = true
                    break
                }
            if (wrote > 0) {
                bufferedLength += wrote
            } else if (inflater.finished() || !inflater.needsInput()) {
                done = true
            }
        }
    }

    private companion object {
        const val MAX_HEADER_BYTES = 2L * 1024 * 1024
    }
}
