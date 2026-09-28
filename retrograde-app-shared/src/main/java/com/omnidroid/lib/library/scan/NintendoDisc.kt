package com.omnidroid.lib.library.scan

import com.omnidroid.lib.library.SystemID
import java.util.zip.Inflater

internal data class NintendoDiscId(
    val system: SystemID,
    val code: String?,
)

internal object NintendoDisc {
    private const val GC_MAGIC = 0xC2339F3DL
    private const val WII_MAGIC = 0x5D1C9EA3L
    private const val GCZ_MAGIC = 0xB10BC001L

    fun fromHeader(header: ByteArray): NintendoDiscId? {
        if (header.size < 0x20) return null
        val system =
            when {
                be32(header, 0x1C) == GC_MAGIC -> SystemID.GAMECUBE
                be32(header, 0x18) == WII_MAGIC -> SystemID.WII
                else -> return null
            }
        return NintendoDiscId(system, ScanKeys.gameCode(header))
    }

    fun fromIso(source: RandomAccessBytes): NintendoDiscId? {
        return fromHeader(source.read(0, 0x20))
    }

    fun fromWbfs(source: RandomAccessBytes): NintendoDiscId? {
        if (!source.read(0, 4).startsWithAscii("WBFS")) return null
        return fromHeader(source.read(0x200, 0x20))
    }

    fun fromCiso(source: RandomAccessBytes): NintendoDiscId? {
        if (!source.read(0, 4).startsWithAscii("CISO")) return null
        return fromHeader(source.read(0x8000, 0x20)) ?: fromHeader(source.read(0x8008, 0x20))
    }

    fun fromTgc(source: RandomAccessBytes): NintendoDiscId? {
        if (source.u32le(0) != 0xA2380FAEL) return null
        val discOffset = source.u32le(8)
        if (discOffset <= 0) return null
        return fromHeader(source.read(discOffset, 0x20))
    }

    fun fromRvz(source: RandomAccessBytes): NintendoDiscId? {
        val magic = source.read(0, 4)
        val rvz = magic.startsWithAscii("RVZ")
        val wia = magic.startsWithAscii("WIA")
        if (!rvz && !wia) return null
        return fromHeader(source.read(0x58, 0x80))
    }

    fun fromGcz(source: RandomAccessBytes): NintendoDiscId? {
        if (source.u32be(0) != GCZ_MAGIC) return null
        val blockSize = source.u32le(24).toInt()
        val blocks = source.u32le(28).toInt()
        if (blockSize <= 0 || blocks <= 0 || blockSize > 16 * 1024 * 1024) return null
        val first = source.u64le(32)
        val second = if (blocks > 1) source.u64le(40) else source.size
        if (first < 0 || second <= first) return null
        val compressed = source.read(first, minOf(second - first, blockSize * 2L).toInt())
        val inflated = inflate(compressed, blockSize) ?: return null
        return fromHeader(inflated)
    }

    private fun inflate(
        compressed: ByteArray,
        expected: Int,
    ): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(compressed)
            val out = ByteArray(expected)
            val wrote = inflater.inflate(out)
            if (wrote <= 0) null else out.copyOf(wrote)
        } catch (_: Exception) {
            null
        } finally {
            inflater.end()
        }
    }

    private fun be32(
        bytes: ByteArray,
        offset: Int,
    ): Long {
        if (offset + 3 >= bytes.size) return -1
        return ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)
    }
}
