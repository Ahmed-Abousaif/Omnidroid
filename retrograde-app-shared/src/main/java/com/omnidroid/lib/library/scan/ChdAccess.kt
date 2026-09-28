package com.omnidroid.lib.library.scan

interface ChdSectors : UserSectors, AutoCloseable {
    val dreamcast: Boolean
}

fun interface ChdOpener {
    fun open(): ChdSectors?
}
