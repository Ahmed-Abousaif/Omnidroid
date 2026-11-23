package com.omnidroid.lib.backend

@JvmInline
value class BackendID(val value: String) {
    companion object {
        val LIBRETRO = BackendID("libretro")
        val SWITCH = BackendID("switch")
        val PS3 = BackendID("ps3")
    }

    override fun toString(): String = value
}

