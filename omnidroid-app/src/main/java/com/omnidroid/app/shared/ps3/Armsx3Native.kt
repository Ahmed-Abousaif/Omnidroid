package com.omnidroid.app.shared.ps3

import android.view.Surface

/** Loaded explicitly after checking device support, never during app startup. */
internal object Armsx3Native {
    external fun cpuError(): String?

    external fun open()

    external fun initialize(
        root: String,
        cache: String,
        host: Class<*>,
        storage: Class<*>,
    ): Boolean

    external fun boot(path: String): Int

    external fun displayRate(hz: Float)

    external fun performanceStats(): LongArray

    external fun performanceOptions(options: Int)

    external fun state(): Int

    external fun probe(
        path: String,
        icon: String,
    ): String

    external fun control(action: Int)

    external fun surface(
        surface: Surface?,
        width: Int,
        height: Int,
    )

    external fun pad(
        port: Int,
        d1: Int,
        d2: Int,
        lx: Int,
        ly: Int,
        rx: Int,
        ry: Int,
    )

    external fun setting(
        path: String,
        value: String,
    ): Boolean

    external fun settings(path: String): String

    external fun stateOperationFailed(): Boolean

    external fun stateSlot(
        save: Boolean,
        slot: Int,
    ): Boolean

    external fun install(
        kind: Int,
        fd: Int,
        id: Long,
    ): Boolean

    external fun dialogResult(
        token: Long,
        status: Int,
        text: String,
    ): Boolean
}
