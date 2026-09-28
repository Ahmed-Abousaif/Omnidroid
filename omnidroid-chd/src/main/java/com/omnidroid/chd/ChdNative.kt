package com.omnidroid.chd

object ChdNative {
    init {
        System.loadLibrary("omnidroid-chd")
    }

    @JvmStatic
    external fun open(fd: Int): Long

    @JvmStatic
    external fun isDreamcast(handle: Long): Boolean

    @JvmStatic
    external fun readSector(
        handle: Long,
        lba: Int,
    ): ByteArray?

    @JvmStatic
    external fun close(handle: Long)
}
