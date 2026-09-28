package com.omnidroid.lib.library.scan

import java.io.Closeable

interface RandomAccessBytes : Closeable {
    val size: Long

    fun read(
        offset: Long,
        length: Int,
    ): ByteArray
}

class ByteArraySource(
    private val data: ByteArray,
) : RandomAccessBytes {
    override val size: Long = data.size.toLong()

    override fun read(
        offset: Long,
        length: Int,
    ): ByteArray {
        if (length <= 0 || offset < 0 || offset >= data.size) return ByteArray(0)
        val start = offset.toInt()
        val end = minOf(data.size, start + length)
        return data.copyOfRange(start, end)
    }

    override fun close() = Unit
}

internal fun RandomAccessBytes.u8(offset: Long): Int {
    val byte = read(offset, 1)
    if (byte.isEmpty()) return -1
    return byte[0].toInt() and 0xFF
}

internal fun RandomAccessBytes.u16le(offset: Long): Int {
    val bytes = read(offset, 2)
    if (bytes.size < 2) return -1
    return (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
}

internal fun RandomAccessBytes.u32le(offset: Long): Long {
    val bytes = read(offset, 4)
    if (bytes.size < 4) return -1
    return (bytes[0].toLong() and 0xFF) or
        ((bytes[1].toLong() and 0xFF) shl 8) or
        ((bytes[2].toLong() and 0xFF) shl 16) or
        ((bytes[3].toLong() and 0xFF) shl 24)
}

internal fun RandomAccessBytes.u32be(offset: Long): Long {
    val bytes = read(offset, 4)
    if (bytes.size < 4) return -1
    return ((bytes[0].toLong() and 0xFF) shl 24) or
        ((bytes[1].toLong() and 0xFF) shl 16) or
        ((bytes[2].toLong() and 0xFF) shl 8) or
        (bytes[3].toLong() and 0xFF)
}

internal fun RandomAccessBytes.u64le(offset: Long): Long {
    val bytes = read(offset, 8)
    if (bytes.size < 8) return -1
    var value = 0L
    for (index in 0 until 8) {
        value = value or ((bytes[index].toLong() and 0xFF) shl (8 * index))
    }
    return value
}

internal fun RandomAccessBytes.ascii(
    offset: Long,
    length: Int,
): String {
    return read(offset, length)
        .takeWhile { it.toInt() != 0 }
        .toByteArray()
        .toString(Charsets.US_ASCII)
        .trim()
}

internal fun ByteArray.ascii(): String {
    val end = indexOf(0).let { if (it < 0) size else it }
    return copyOfRange(0, end).toString(Charsets.US_ASCII).trim()
}

internal fun ByteArray.startsWithAscii(text: String): Boolean {
    val expected = text.toByteArray(Charsets.US_ASCII)
    if (size < expected.size) return false
    return expected.indices.all { this[it] == expected[it] }
}

internal fun ByteArray.containsAscii(text: String): Boolean {
    val expected = text.toByteArray(Charsets.US_ASCII)
    if (expected.isEmpty() || size < expected.size) return false
    for (start in 0..size - expected.size) {
        if (expected.indices.all { this[start + it] == expected[it] }) return true
    }
    return false
}
