package com.omnidroid.sevenzip

import java.util.Locale

data class SevenZipEntry(
    val index: Int,
    val name: String,
    val size: Long,
    val flags: Long,
) {
    val isDirectory: Boolean
        get() = (flags and FLAG_DIR) != 0L

    val hasCrc: Boolean
        get() = (flags and FLAG_HAS_CRC) != 0L

    val crc32Hex: String?
        get() =
            if (hasCrc) {
                "%08X".format(Locale.US, (flags ushr 32) and 0xFFFFFFFFL)
            } else {
                null
            }

    val isEncrypted: Boolean
        get() = (flags and FLAG_ENCRYPTED) != 0L

    val isSupportedCoder: Boolean
        get() = (flags and FLAG_SUPPORTED_CODER) != 0L

    val simpleName: String
        get() = name.replace('\\', '/').substringAfterLast('/')

    val extension: String
        get() = simpleName.substringAfterLast('.', "").lowercase(Locale.US)

    companion object {
        const val FLAG_DIR: Long = 1L shl 0
        const val FLAG_HAS_CRC: Long = 1L shl 1
        const val FLAG_ENCRYPTED: Long = 1L shl 2
        const val FLAG_SUPPORTED_CODER: Long = 1L shl 3
    }
}
