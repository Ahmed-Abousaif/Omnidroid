/*
 * FileKt.kt
 *
 * Copyright (C) 2017 Retrograde Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.omnidroid.common.kotlin

import android.util.AtomicFile
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.PushbackInputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipInputStream

private const val CRC32_BYTE_ARRAY_SIZE = 16 * 1024
private const val GZIP_INPUT_STREAM_BUFFER_SIZE = 8 * 1024

fun InputStream.calculateCrc32(): String =
    this.use { fileStream ->
        val buffer = ByteArray(CRC32_BYTE_ARRAY_SIZE)
        return CheckedInputStream(fileStream, CRC32()).use { crcStream ->
            while (crcStream.read(buffer) != -1) {
                // Read file in completely
            }
            crcStream.checksum.value.toStringCRC32()
        }
    }

fun File.calculateMd5(): String {
    val bytes =
        MessageDigest
            .getInstance("MD5")
            .digest(this.readBytes())
    return bytes.toHexString()
}

fun InputStream.writeToFile(file: File) {
    this.use { inputStream ->
        file.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
        }
    }
}

fun ZipInputStream.extractEntryToFile(
    entryName: String,
    gameFile: File,
) {
    this.use { inputStream ->
        val buffer = ByteArray(8192)
        val simpleTargetName = entryName.substringAfterLast('/')

        while (true) {
            val entry = inputStream.nextEntry ?: break
            if (entry.isDirectory) continue

            val simpleEntryName = entry.name.substringAfterLast('/')
            if (entry.name == entryName || simpleEntryName == simpleTargetName) {
                gameFile.outputStream().use { out ->
                    var len: Int
                    while (inputStream.read(buffer).also { len = it } > 0) {
                        out.write(buffer, 0, len)
                    }
                }
                return
            }
        }
    }

    // Exact match not found: re-opening is not possible on a consumed stream.
    // Callers that need fallback behaviour should supply the correct entryName
    // (e.g. via peekZipInnerRom) before calling this method.
}

fun File.isZipped() = extension.equals("zip", ignoreCase = true)

fun DocumentFile.isZipped(): Boolean {
    val t = type?.lowercase()
    if (t == "application/zip" || t == "application/x-zip" || t == "application/x-zip-compressed") {
        return true
    }
    return name?.endsWith(".zip", ignoreCase = true) == true || uri.path?.endsWith(".zip", ignoreCase = true) == true
}

/** Returns the uncompressed input stream if gzip compressed. */
private fun InputStream.uncompressedInputStream(): InputStream {
    val pb = PushbackInputStream(this, 2)
    val signature = ByteArray(2)
    val len = pb.read(signature)
    pb.unread(signature, 0, len)
    return if (signature[0] == 0x1f.toByte() && signature[1] == 0x8b.toByte()) {
        GZIPInputStream(pb, GZIP_INPUT_STREAM_BUFFER_SIZE)
    } else {
        pb
    }
}

/** Read bytes from input stream. If the stream is compressed with GZIP the uncompressed data is returned.*/
private fun readBytesUncompressedStream(inputStream: InputStream): ByteArray =
    inputStream.use { input ->
        val b = ByteArray(GZIP_INPUT_STREAM_BUFFER_SIZE)
        val os = ByteArrayOutputStream()
        os.use { usedOutputStream ->
            var c: Int
            while (input.read(b).also { c = it } != -1) {
                usedOutputStream.write(b, 0, c)
            }
        }
        return os.toByteArray()
    }

/** Write bytes to file using AtomicFile to prevent corruption. */
fun File.writeBytesAtomic(data: ByteArray) {
    val atomicFile = AtomicFile(this)
    val outputStream = atomicFile.startWrite()
    try {
        outputStream.use { it.write(data) }
        atomicFile.finishWrite(outputStream)
    } catch (e: Throwable) {
        atomicFile.failWrite(outputStream)
        throw e
    }
}

/** Read text from file using AtomicFile. */
fun File.readTextAtomic(): String {
    val atomicFile = AtomicFile(this)
    return atomicFile.openRead().bufferedReader().use { it.readText() }
}

/** Write text to file using AtomicFile. */
fun File.writeTextAtomic(text: String) {
    val atomicFile = AtomicFile(this)
    val outputStream = atomicFile.startWrite()
    try {
        outputStream.writer().use { it.write(text) }
        atomicFile.finishWrite(outputStream)
    } catch (e: Throwable) {
        atomicFile.failWrite(outputStream)
        throw e
    }
}

/** Write bytes to file using GZIP compression via AtomicFile. */
fun File.writeBytesCompressedAtomic(array: ByteArray) {
    val atomicFile = AtomicFile(this)
    val outputStream = atomicFile.startWrite()
    try {
        ByteArrayInputStream(array).use { input ->
            GZIPOutputStream(outputStream).use { usedOutput ->
                input.copyTo(usedOutput)
            }
        }
        atomicFile.finishWrite(outputStream)
    } catch (e: Throwable) {
        atomicFile.failWrite(outputStream)
        throw e
    }
}

/** Read bytes using AtomicFile. If the file is compressed with GZIP the uncompressed data is returned. */
fun File.readBytesUncompressedAtomic(): ByteArray {
    val atomicFile = AtomicFile(this)
    return atomicFile.openRead().use { input ->
        readBytesUncompressedStream(input.uncompressedInputStream())
    }
}
