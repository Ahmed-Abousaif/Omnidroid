package com.omnidroid.lib.library.scan

import com.omnidroid.lib.library.SystemID
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater

internal data class SectorLayout(
    val sectorSize: Int,
    val userOffset: Int,
    val fileOffset: Long = 0,
)

interface UserSectors {
    fun readUserSector(lba: Int): ByteArray?
}

internal object DiscImages {
    private val SYNC =
        byteArrayOf(
            0x00,
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0x00,
        )

    fun layout(
        source: RandomAccessBytes,
        base: Long = 0,
    ): SectorLayout {
        val head = source.read(base, 32 * 1024)
        if (head.size > 0x8006 && head.copyOfRange(0x8001, minOf(head.size, 0x8006)).startsWithAscii("CD001")) {
            return SectorLayout(2048, 0, base)
        }
        if (head.size >= 12 && head.copyOfRange(0, 12).contentEquals(SYNC)) {
            if (head.size > 2448 + 12 && head.copyOfRange(2448, 2460).contentEquals(SYNC)) {
                return SectorLayout(2448, 16, base)
            }
            return SectorLayout(2352, 16, base)
        }
        return SectorLayout(2048, 0, base)
    }

    fun userSectors(
        source: RandomAccessBytes,
        layout: SectorLayout,
    ): UserSectors {
        return object : UserSectors {
            override fun readUserSector(lba: Int): ByteArray? {
                val offset = layout.fileOffset + lba.toLong() * layout.sectorSize + layout.userOffset
                val bytes = source.read(offset, 2048)
                return bytes.takeIf { it.size == 2048 }
            }
        }
    }
}

internal object DiscIdentifier {
    fun identify(sectors: UserSectors): DiscHit? {
        val sector0 = sectors.readUserSector(0) ?: return null
        if (sector0.startsWithAscii("SEGADISCSYSTEM")) {
            val serial = ScanKeys.segaCdSerial(sector0.asciiAt(0x183, 16), sector0.asciiAt(0x1F0, 1))
            return DiscHit(SystemID.SEGACD, serial)
        }
        if (sector0.startsWithAscii("SEGA SEGAKATANA")) {
            return DiscHit(SystemID.DREAMCAST, null)
        }
        val pvd = sectors.readUserSector(16) ?: return null
        if (pvd.size < 8 || !pvd.copyOfRange(1, 6).startsWithAscii("CD001")) return null
        val system = pvd.asciiAt(8, 32)
        val volume = pvd.asciiAt(40, 32)
        return when {
            system.startsWith("PSP GAME") -> DiscHit(SystemID.PSP, pspSerial(sectors))
            system.startsWith("PLAYSTATION") -> playstation(sectors, volume)
            else -> null
        }
    }

    private fun playstation(
        sectors: UserSectors,
        volume: String,
    ): DiscHit {
        val config = Iso9660.readFile(sectors, "SYSTEM.CNF")?.toString(Charsets.US_ASCII)
        val ps2 = config?.contains("BOOT2") == true
        val system = if (ps2) SystemID.PS2 else SystemID.PSX
        val fromBoot = config?.let { ScanKeys.playstationSerial(it) }
        val serial = fromBoot ?: ScanKeys.playstationSerial(volume)
        return DiscHit(system, serial)
    }

    private fun pspSerial(sectors: UserSectors): String? {
        val text = Iso9660.readFile(sectors, "UMD_DATA.BIN")?.toString(Charsets.US_ASCII) ?: return null
        val first = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return null
        return ScanKeys.playstationSerial(first.substringBefore('|'))
    }
}

internal data class DiscHit(
    val system: SystemID,
    val serial: String?,
)

private fun ByteArray.asciiAt(
    offset: Int,
    length: Int,
): String {
    if (offset < 0 || offset >= size) return ""
    val end = minOf(size, offset + length)
    return copyOfRange(offset, end).ascii()
}

internal object Iso9660 {
    fun readFile(
        sectors: UserSectors,
        wanted: String,
    ): ByteArray? {
        val pvd = sectors.readUserSector(16) ?: return null
        if (pvd.size < 190) return null
        val root = pvd.copyOfRange(156, minOf(pvd.size, 156 + 34 + pvd[188].toInt().and(0xFF)))
        val extent = le32(root, 2)
        val length = le32(root, 10)
        if (extent < 0 || length <= 0) return null
        val directory = readExtent(sectors, extent, length) ?: return null
        var cursor = 0
        while (cursor + 33 < directory.size) {
            val recordLength = directory[cursor].toInt() and 0xFF
            if (recordLength == 0) {
                cursor = ((cursor / 2048) + 1) * 2048
                continue
            }
            if (cursor + recordLength > directory.size) break
            val nameLength = directory[cursor + 32].toInt() and 0xFF
            val name =
                if (nameLength == 1 && directory[cursor + 33].toInt() <= 1) {
                    ""
                } else {
                    directory.copyOfRange(cursor + 33, cursor + 33 + nameLength).toString(Charsets.US_ASCII)
                }
            val bare = name.substringBefore(';').uppercase()
            if (bare == wanted.uppercase()) {
                val fileExtent = le32(directory, cursor + 2)
                val fileLength = le32(directory, cursor + 10)
                if (fileExtent < 0 || fileLength <= 0 || fileLength > 256 * 1024) return null
                return readExtent(sectors, fileExtent, fileLength)
            }
            cursor += recordLength
        }
        return null
    }

    private fun readExtent(
        sectors: UserSectors,
        start: Int,
        length: Int,
    ): ByteArray? {
        val out = ByteArray(length)
        var filled = 0
        var lba = start
        while (filled < length) {
            val sector = sectors.readUserSector(lba) ?: return null
            val copy = minOf(2048, length - filled)
            sector.copyInto(out, filled, 0, copy)
            filled += copy
            lba += 1
        }
        return out
    }

    private fun le32(
        bytes: ByteArray,
        offset: Int,
    ): Int {
        if (offset + 3 >= bytes.size) return -1
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }
}

internal object CsoImage {
    fun open(source: RandomAccessBytes): UserSectors? {
        val magic = source.read(0, 4).toString(Charsets.US_ASCII)
        val lz4 = magic == "ZISO"
        if (magic != "CISO" && !lz4) return null
        val total = source.u64le(8)
        val blockSize = source.u32le(16).toInt()
        val align = source.u8(21).let { if (it < 0) 0 else it }
        if (total <= 0 || blockSize <= 0 || blockSize > 1024 * 1024) return null
        val blocks = ((total + blockSize - 1) / blockSize).toInt()
        if (blocks <= 0 || blocks > 2_000_000) return null
        val indexBytes = source.read(24, (blocks + 1) * 4)
        if (indexBytes.size < (blocks + 1) * 4) return null
        return object : UserSectors {
            override fun readUserSector(lba: Int): ByteArray? {
                val offset = lba.toLong() * 2048
                if (offset + 2048 > total) return null
                val block = (offset / blockSize).toInt()
                val decoded = decodeBlock(source, indexBytes, block, blocks, blockSize, align, lz4) ?: return null
                val start = (offset % blockSize).toInt()
                if (start + 2048 > decoded.size) return null
                return decoded.copyOfRange(start, start + 2048)
            }
        }
    }

    private fun decodeBlock(
        source: RandomAccessBytes,
        index: ByteArray,
        block: Int,
        blocks: Int,
        blockSize: Int,
        align: Int,
        lz4: Boolean,
    ): ByteArray? {
        val current = le32(index, block * 4)
        val next = le32(index, (block + 1) * 4)
        val plain = current and 0x80000000.toInt() != 0
        val start = (current.toLong() and 0x7FFFFFFF) shl align
        val end = (next.toLong() and 0x7FFFFFFF) shl align
        if (start < 0 || end < start || end - start > blockSize * 4L) return null
        val compressed = source.read(start, (end - start).toInt())
        if (plain || compressed.size == blockSize) return compressed
        val out = ByteArray(blockSize)
        return if (lz4) {
            if (Lz4.decompress(compressed, out)) out else null
        } else {
            val inflater = Inflater(false)
            try {
                inflater.setInput(compressed)
                var filled = 0
                while (filled < out.size && !inflater.finished()) {
                    val wrote = inflater.inflate(out, filled, out.size - filled)
                    if (wrote == 0) break
                    filled += wrote
                }
                if (filled == 0) null else out
            } catch (_: Exception) {
                null
            } finally {
                inflater.end()
            }
        }
    }

    private fun le32(
        bytes: ByteArray,
        offset: Int,
    ): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }
}

internal object Lz4 {
    fun decompress(
        src: ByteArray,
        dst: ByteArray,
    ): Boolean {
        var s = 0
        var d = 0
        while (s < src.size && d < dst.size) {
            val token = src[s].toInt() and 0xFF
            s++
            var literals = token ushr 4
            if (literals == 15) {
                var extra: Int
                do {
                    if (s >= src.size) return false
                    extra = src[s].toInt() and 0xFF
                    s++
                    literals += extra
                } while (extra == 255)
            }
            if (s + literals > src.size || d + literals > dst.size) return d > 0
            src.copyInto(dst, d, s, s + literals)
            s += literals
            d += literals
            if (d >= dst.size) return true
            if (s + 2 > src.size) return d > 0
            val offset = (src[s].toInt() and 0xFF) or ((src[s + 1].toInt() and 0xFF) shl 8)
            s += 2
            if (offset <= 0 || offset > d) return false
            var match = (token and 0x0F) + 4
            if ((token and 0x0F) == 15) {
                var extra: Int
                do {
                    if (s >= src.size) return false
                    extra = src[s].toInt() and 0xFF
                    s++
                    match += extra
                } while (extra == 255)
            }
            var from = d - offset
            repeat(match) {
                if (d >= dst.size) return true
                dst[d++] = dst[from++]
            }
        }
        return d > 0
    }
}

internal object GzipImage {
    private const val LIMIT = 2L * 1024 * 1024

    fun open(source: RandomAccessBytes): RandomAccessBytes? {
        val magic = source.read(0, 2)
        if (magic.size < 2 || magic[0] != 0x1F.toByte() || magic[1] != 0x8B.toByte()) return null
        val compressed = source.read(0, minOf(source.size, LIMIT).toInt())
        return try {
            GZIPInputStream(ByteArrayInputStream(compressed)).use { stream ->
                val out = ByteArray(LIMIT.toInt())
                var filled = 0
                while (filled < out.size) {
                    val read = stream.read(out, filled, out.size - filled)
                    if (read < 0) break
                    filled += read
                }
                ByteArraySource(out.copyOf(filled))
            }
        } catch (_: Exception) {
            null
        }
    }
}
