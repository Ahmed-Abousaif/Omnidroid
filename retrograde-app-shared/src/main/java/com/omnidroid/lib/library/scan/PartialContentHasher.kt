package com.omnidroid.lib.library.scan

import java.security.MessageDigest

object PartialContentHasher {
    const val WINDOW_BYTES = 64 * 1024

    /**
     * Partial content identity: file size + first/last 64KB.
     * Empty files hash to a size-only digest. Files ≤64KB are hashed once (no double-read).
     */
    fun hash(access: RandomAccessBytes): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(access.size.toString().toByteArray(Charsets.UTF_8))
        digest.update(0)
        if (access.size <= 0L) {
            return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }
        val firstLen = minOf(WINDOW_BYTES.toLong(), access.size).toInt()
        val head = access.read(0L, firstLen)
        digest.update(head)
        // Only sample the tail when the file is larger than one window and the tail
        // does not fully overlap the head (size > 2*WINDOW would be ideal; > WINDOW is enough).
        if (access.size > WINDOW_BYTES) {
            val lastOffset = access.size - WINDOW_BYTES
            // Avoid re-hashing the same bytes when size is between WINDOW and 2*WINDOW
            // by reading the true tail; overlap is intentional for collision resistance.
            val tail = access.read(lastOffset, WINDOW_BYTES)
            digest.update(tail)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
