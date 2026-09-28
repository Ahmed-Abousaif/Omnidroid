package com.omnidroid.lib.library.scan

import com.omnidroid.lib.library.SystemID

internal data class PbpInfo(
    val system: SystemID,
    val serial: String?,
)

internal object PbpReader {
    private val PSP_PREFIXES =
        listOf(
            "ULES",
            "ULUS",
            "ULJS",
            "ULEM",
            "ULUM",
            "ULJM",
            "ULKS",
            "ULAS",
            "UCES",
            "UCUS",
            "UCJS",
            "UCAS",
            "NPEH",
            "NPUH",
            "NPJH",
            "NPEG",
            "NPEX",
            "NPUG",
            "NPJG",
            "NPJJ",
            "NPHG",
            "NPEZ",
            "NPUZ",
            "NPJZ",
            "NPUF",
            "NPUX",
        )

    fun read(source: RandomAccessBytes): PbpInfo? {
        val magic = source.read(0, 4)
        if (magic.size < 4 || magic[0] != 0.toByte() || !magic.copyOfRange(1, 4).startsWithAscii("PBP")) return null
        val paramOffset = source.u32le(8)
        val nextOffset = source.u32le(12)
        if (paramOffset < 0 || nextOffset <= paramOffset) return null
        val sfo = source.read(paramOffset, (nextOffset - paramOffset).toInt())
        val values = Sfo.parse(sfo)
        val serial = ScanKeys.playstationSerial(values["DISC_ID"])
        val category = values["CATEGORY"].orEmpty()
        val system =
            when {
                category == "ME" -> SystemID.PSX
                category == "MG" -> SystemID.PSP
                serial != null && PSP_PREFIXES.any { serial.startsWith(it) } -> SystemID.PSP
                serial != null -> SystemID.PSX
                else -> return null
            }
        return PbpInfo(system, serial)
    }
}

internal object Sfo {
    fun parse(bytes: ByteArray): Map<String, String> {
        if (bytes.size < 20 || !bytes.copyOfRange(0, 4).startsWithAscii("\u0000PSF")) return emptyMap()
        val keyTable = le32(bytes, 8)
        val dataTable = le32(bytes, 12)
        val count = le32(bytes, 16)
        if (keyTable < 0 || dataTable < 0 || count < 0 || count > 256) return emptyMap()
        val values = LinkedHashMap<String, String>()
        for (index in 0 until count) {
            val entry = 20 + index * 16
            if (entry + 16 > bytes.size) break
            val keyOffset = le16(bytes, entry)
            val format = le16(bytes, entry + 2)
            val dataLength = le32(bytes, entry + 4)
            val dataOffset = le32(bytes, entry + 12)
            val keyStart = keyTable + keyOffset
            val dataStart = dataTable + dataOffset
            if (keyStart < 0 || dataStart < 0 || keyStart >= bytes.size || dataStart >= bytes.size) continue
            val keyEnd = bytes.indexOfFrom(0, keyStart).let { if (it < 0) bytes.size else it }
            val key = bytes.copyOfRange(keyStart, keyEnd).toString(Charsets.UTF_8)
            if (format == 0x0204 && dataLength > 0 && dataStart + dataLength <= bytes.size) {
                values[key] = bytes.copyOfRange(dataStart, dataStart + dataLength).ascii()
            }
        }
        return values
    }

    private fun ByteArray.indexOfFrom(
        value: Byte,
        start: Int,
    ): Int {
        for (index in start until size) {
            if (this[index] == value) return index
        }
        return -1
    }

    private fun le16(
        bytes: ByteArray,
        offset: Int,
    ): Int {
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
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

internal object ElfHeader {
    const val MACHINE_MIPS = 8
    const val MACHINE_PPC = 20

    fun machine(source: RandomAccessBytes): Int? {
        val header = source.read(0, 20)
        if (header.size < 20 || header[0] != 0x7F.toByte() || !header.copyOfRange(1, 4).startsWithAscii("ELF")) {
            return null
        }
        val little = header[5].toInt() == 1
        val machineOffset = 18
        val low = header[machineOffset].toInt() and 0xFF
        val high = header[machineOffset + 1].toInt() and 0xFF
        return if (little) low or (high shl 8) else (low shl 8) or high
    }
}

internal object ChdHeader {
    fun isVersion5(source: RandomAccessBytes): Boolean {
        val header = source.read(0, 64)
        if (header.size < 64 || !header.copyOfRange(0, 8).startsWithAscii("MComprHD")) return false
        val version = be32(header, 12)
        val hunk = be32(header, 56)
        if (version != 5L) return false
        return hunk in 512..(16L * 1024 * 1024) && hunk % 512L == 0L
    }

    private fun be32(
        bytes: ByteArray,
        offset: Int,
    ): Long {
        return ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)
    }
}
