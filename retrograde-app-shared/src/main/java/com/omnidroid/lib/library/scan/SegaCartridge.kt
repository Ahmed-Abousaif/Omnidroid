package com.omnidroid.lib.library.scan

internal object SegaCartridge {
    fun is32x(
        source: RandomAccessBytes,
        smd: Boolean = false,
    ): Boolean {
        val header =
            if (smd) {
                val block = source.read(512, 16 * 1024)
                if (block.size < 16 * 1024) return false
                ByteArray(16) { index ->
                    val offset = 0x100 + index
                    block[offset / 2 + if (offset % 2 == 0) 8192 else 0]
                }
            } else {
                source.read(0x100, 16)
            }
        return header.toString(Charsets.US_ASCII).startsWith("SEGA 32X")
    }
}
