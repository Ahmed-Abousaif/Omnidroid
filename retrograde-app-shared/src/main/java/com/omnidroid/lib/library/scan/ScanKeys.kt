package com.omnidroid.lib.library.scan

import java.util.Locale

/**
 * Lookup keys stored by tools/libretro-db-builder. Keep the two copies in step.
 */
object ScanKeys {
    private val PLAYSTATION_SERIAL = Regex("([A-Z]{3,5})[-_]?([0-9]{3,5})(?:\\.([0-9]{2}))?")
    private val NINTENDO_DISC_CODE = Regex("(?:DOL|RVL)-([A-Z0-9]{4})")
    private val SEGA_CD_SERIAL = Regex("([A-Z]+)?-?([0-9]+) ?-?([0-9]*)")
    private val GAME_CODE = Regex("^[A-Z0-9]{4}$")

    fun playstationSerial(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val match = PLAYSTATION_SERIAL.find(raw.uppercase(Locale.US)) ?: return null
        val prefix = match.groupValues[1]
        val number = match.groupValues[2]
        val fraction = match.groupValues[3]
        return if (fraction.isNotEmpty()) "$prefix-$number$fraction" else "$prefix-$number"
    }

    fun nintendoDiscCode(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return NINTENDO_DISC_CODE.find(raw.uppercase(Locale.US))?.groupValues?.get(1)
    }

    fun gameCode(bytes: ByteArray): String? {
        if (bytes.size < 4) return null
        val text = bytes.take(4).map { it.toInt().toChar() }.joinToString("")
        return text.takeIf { GAME_CODE.matches(it) }
    }

    fun segaCdSerial(
        raw: String?,
        region: String? = null,
    ): String? {
        if (raw.isNullOrBlank()) return null
        val groups = SEGA_CD_SERIAL.find(raw.uppercase(Locale.US)) ?: return null
        val prefix = groups.groupValues[1].ifBlank { null }
        val number = groups.groupValues[2].ifBlank { null } ?: return null
        var postfix = groups.groupValues[3].ifBlank { null }
        if (region == "E") postfix = "50"
        if (postfix == "00") postfix = null
        return listOfNotNull(prefix, number, postfix)
            .filter { it.isNotBlank() }
            .joinToString("-")
            .ifBlank { null }
    }

    fun romHash(fileName: String): Long? {
        val base = romBase(fileName)
        if (base.isBlank()) return null
        var hash = 0xcbf29ce484222325uL
        val prime = 0x100000001b3uL
        for (byte in base.encodeToByteArray()) {
            hash = hash xor byte.toUByte().toULong()
            hash *= prime
        }
        return hash.toLong()
    }

    fun romBase(fileName: String): String {
        val trimmed = fileName.trim()
        val withoutExtension = trimmed.substringBeforeLast('.', trimmed)
        return withoutExtension.lowercase(Locale.US)
    }

    fun extension(fileName: String): String {
        return fileName.substringAfterLast('.', "").lowercase(Locale.US)
    }

    fun crcHex(value: Long): String = "%08X".format(value and 0xFFFFFFFFL)
}
