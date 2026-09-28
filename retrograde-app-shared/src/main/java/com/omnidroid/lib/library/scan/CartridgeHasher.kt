package com.omnidroid.lib.library.scan

import java.util.zip.CRC32

internal enum class HashMode {
    RAW,
    SNES,
    SMD,
    N64,
}

internal object CartridgeHasher {
    private const val BUFFER = 64 * 1024

    fun hash(
        source: RandomAccessBytes,
        mode: HashMode,
    ): String {
        val crc = CRC32()
        when (mode) {
            HashMode.RAW -> hashRange(source, 0, source.size, crc)
            HashMode.SNES -> {
                val skip = if (source.size % 1024L == 512L) 512L else 0L
                hashRange(source, skip, source.size - skip, crc)
            }
            HashMode.SMD -> hashSmd(source, crc)
            HashMode.N64 -> hashN64(source, crc)
        }
        return ScanKeys.crcHex(crc.value)
    }

    fun needsNormalisedHash(
        extension: String,
        uncompressedSize: Long,
    ): Boolean {
        return when (extension) {
            "n64", "v64", "smd" -> true
            "smc", "sfc", "fig", "swc", "bs" -> uncompressedSize % 1024L == 512L
            else -> false
        }
    }

    private fun hashSmd(
        source: RandomAccessBytes,
        crc: CRC32,
    ) {
        if (source.size <= 512) {
            hashRange(source, 0, source.size, crc)
            return
        }
        var offset = 512L
        val block = ByteArray(16 * 1024)
        val out = ByteArray(16 * 1024)
        while (offset + block.size <= source.size) {
            val read = source.read(offset, block.size)
            if (read.size < block.size) break
            for (index in 0 until 8192) {
                out[index * 2] = read[index]
                out[index * 2 + 1] = read[8192 + index]
            }
            crc.update(out)
            offset += block.size
        }
    }

    private fun hashN64(
        source: RandomAccessBytes,
        crc: CRC32,
    ) {
        val magic = source.read(0, 4)
        val swap =
            when {
                magic.size < 4 -> 0
                magic[0] == 0x80.toByte() -> 0
                magic[0] == 0x37.toByte() -> 2
                magic[0] == 0x40.toByte() -> 4
                else -> 0
            }
        if (swap == 0) {
            hashRange(source, 0, source.size, crc)
            return
        }
        var offset = 0L
        val buffer = ByteArray(BUFFER)
        while (offset < source.size) {
            val read = source.read(offset, BUFFER)
            if (read.isEmpty()) break
            if (swap == 2) {
                var index = 0
                while (index + 1 < read.size) {
                    val tmp = read[index]
                    read[index] = read[index + 1]
                    read[index + 1] = tmp
                    index += 2
                }
            } else {
                var index = 0
                while (index + 3 < read.size) {
                    val b0 = read[index]
                    val b1 = read[index + 1]
                    read[index] = read[index + 3]
                    read[index + 1] = read[index + 2]
                    read[index + 2] = b1
                    read[index + 3] = b0
                    index += 4
                }
            }
            crc.update(read)
            offset += read.size
        }
    }

    private fun hashRange(
        source: RandomAccessBytes,
        start: Long,
        length: Long,
        crc: CRC32,
    ) {
        var offset = start
        val end = start + length
        val buffer = ByteArray(BUFFER)
        while (offset < end) {
            val want = minOf(BUFFER.toLong(), end - offset).toInt()
            val read = source.read(offset, want)
            if (read.isEmpty()) break
            crc.update(read)
            offset += read.size
        }
    }
}
