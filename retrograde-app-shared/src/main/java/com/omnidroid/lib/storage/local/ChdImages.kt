package com.omnidroid.lib.storage.local

import android.content.Context
import android.net.Uri
import com.omnidroid.chd.ChdNative
import com.omnidroid.lib.library.scan.ChdSectors

object ChdImages {
    fun open(
        context: Context,
        uri: Uri,
    ): ChdSectors? {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        return try {
            val handle = ChdNative.open(descriptor.fd)
            if (handle == 0L) {
                descriptor.close()
                null
            } else {
                descriptor.close()
                NativeChd(handle, ChdNative.isDreamcast(handle))
            }
        } catch (error: UnsatisfiedLinkError) {
            descriptor.close()
            null
        } catch (error: RuntimeException) {
            descriptor.close()
            null
        }
    }
}

private class NativeChd(
    private val handle: Long,
    override val dreamcast: Boolean,
) : ChdSectors {
    override fun readUserSector(lba: Int): ByteArray? = ChdNative.readSector(handle, lba)

    override fun close() {
        ChdNative.close(handle)
    }
}
