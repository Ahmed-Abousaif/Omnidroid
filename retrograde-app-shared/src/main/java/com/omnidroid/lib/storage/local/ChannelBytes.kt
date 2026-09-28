package com.omnidroid.lib.storage.local

import android.content.Context
import android.net.Uri
import com.omnidroid.lib.library.scan.RandomAccessBytes
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer

class ChannelBytes(
    private val channel: java.nio.channels.FileChannel,
    private val closeable: Closeable,
) : RandomAccessBytes {
    override val size: Long = channel.size()

    override fun read(
        offset: Long,
        length: Int,
    ): ByteArray {
        if (length <= 0 || offset < 0 || offset >= size) return ByteArray(0)
        val out = ByteArray(minOf(length.toLong(), size - offset).toInt())
        val buffer = ByteBuffer.wrap(out)
        var position = offset
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer, position)
            if (read < 0) break
            position += read
        }
        return if (buffer.position() == out.size) out else out.copyOf(buffer.position())
    }

    override fun close() {
        runCatching { channel.close() }
        runCatching { closeable.close() }
    }

    companion object {
        fun open(
            context: Context,
            uri: Uri,
        ): ChannelBytes? {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            return ChannelBytes(FileInputStream(descriptor.fileDescriptor).channel, descriptor)
        }
    }
}
