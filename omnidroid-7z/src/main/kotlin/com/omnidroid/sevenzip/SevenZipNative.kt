package com.omnidroid.sevenzip

object SevenZipNative {
    init {
        System.loadLibrary("omnidroid-7z")
    }

    @JvmStatic
    external fun open(archiveFd: Int): Long

    @JvmStatic
    external fun entryNames(handle: Long): Array<String>

    @JvmStatic
    external fun entryInfo(handle: Long): LongArray

    @JvmStatic
    external fun extract(
        handle: Long,
        index: Int,
        outFd: Int,
        maxMemory: Long,
    ): Int

    @JvmStatic
    external fun progress(handle: Long): Long

    @JvmStatic
    external fun cancel(handle: Long)

    @JvmStatic
    external fun close(handle: Long)
}
