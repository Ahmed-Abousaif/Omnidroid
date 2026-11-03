package com.omnidroid.lib.backend

@JvmInline
value class BackendID(val value: String) {
    companion object {
        val LIBRETRO = BackendID("libretro")
        val SWITCH = BackendID("switch")
    }

    override fun toString(): String = value
}

