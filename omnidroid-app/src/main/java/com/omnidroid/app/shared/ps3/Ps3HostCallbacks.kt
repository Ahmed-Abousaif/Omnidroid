package com.omnidroid.app.shared.ps3

import android.content.Context
import androidx.annotation.Keep
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Omnidroid-owned callbacks passed explicitly to its JNI adapter. */
@Keep
internal object Ps3HostCallbacks {
    private data class Installation(val progress: (String) -> Unit, val completion: CompletableFuture<String>)
    private val nextId = AtomicLong(1)
    private val installations = ConcurrentHashMap<Long, Installation>()
    private var context: Context? = null

    fun attach(context: Context) { this.context = context.applicationContext }
    fun register(progress: (String) -> Unit, completion: CompletableFuture<String>): Long =
        nextId.getAndIncrement().also { installations[it] = Installation(progress, completion) }
    fun remove(id: Long) { installations.remove(id) }

    @JvmStatic fun onProgress(id: Long, value: Long, maximum: Long, message: String) {
        installations[id]?.progress?.invoke(
            if (maximum > 0) "$message: ${value * 100 / maximum}%" else message,
        )
    }
    @JvmStatic fun onInstalled(id: Long, result: Int, message: String) {
        installations[id]?.completion?.let {
            if (result == 0) it.complete(message)
            else it.completeExceptionally(IllegalStateException(message))
        }
    }
    @JvmStatic fun onFirmware(version: String) {
        context?.getSharedPreferences("ps3", Context.MODE_PRIVATE)?.edit()
            ?.putString("firmware_version", version)?.apply()
    }
    @JvmStatic fun onDialog(token: Long, kind: Int, json: String) {
        Ps3GuestDialogs.deliver(token, kind, json)
    }
}
