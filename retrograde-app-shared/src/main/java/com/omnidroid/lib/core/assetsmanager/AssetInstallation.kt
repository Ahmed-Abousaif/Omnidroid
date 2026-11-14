package com.omnidroid.lib.core.assetsmanager

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import java.util.zip.CRC32

/** Downloads are unpacked and checked before the working installation is replaced. */
internal object AssetInstallation {
    private const val MANIFEST = ".omnidroid-assets"
    private const val MAX_EXPANDED_BYTES = 128L * 1024 * 1024

    fun hasManifest(directory: File): Boolean = File(directory, MANIFEST).isFile

    fun isComplete(directory: File, verifyContents: Boolean = true): Boolean {
        val manifest = File(directory, MANIFEST)
        if (!manifest.isFile) return false
        return runCatching {
            val entries = manifest.readLines()
            entries.isNotEmpty() && entries.all { line ->
                val parts = line.split('\t', limit = 3)
                val file = containedFile(directory, parts[2])
                file.isFile && file.length() == parts[0].toLong() &&
                    (!verifyContents || checksum(file) == parts[1])
            }
        }.getOrDefault(false)
    }

    fun install(
        input: InputStream,
        destination: File,
        mapEntry: (String) -> String? = { it },
        validate: (File) -> Boolean,
        checkCancelled: () -> Unit = {},
    ) {
        val parent = destination.parentFile ?: throw IOException("Missing assets directory")
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Cannot create assets directory")
        val staging = File(parent, ".assets-${UUID.randomUUID()}")
        val backup = File(parent, ".assets-backup-${UUID.randomUUID()}")
        check(staging.mkdir()) { "Cannot prepare assets" }
        var previousMoved = false
        try {
            var expanded = 0L
            ZipInputStream(input).use { zip ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    checkCancelled()
                    val entry = zip.nextEntry ?: break
                    val original = entry.name.replace('\\', '/')
                    containedFile(staging, original)
                    val relative = mapEntry(original) ?: continue
                    val target = containedFile(staging, relative)
                    if (entry.isDirectory || relative.endsWith('/')) {
                        if (!target.isDirectory && !target.mkdirs()) throw IOException("Cannot create asset folder")
                    } else {
                        if (relative == MANIFEST) throw IOException("Invalid asset package")
                        target.parentFile?.mkdirs()
                        target.outputStream().use { output ->
                            while (true) {
                                checkCancelled()
                                val count = zip.read(buffer)
                                if (count < 0) break
                                expanded += count
                                if (expanded > MAX_EXPANDED_BYTES) throw IOException("Asset package is too large")
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            checkCancelled()
            if (!validate(staging)) throw IOException("Asset package is incomplete or invalid")
            recordInstallation(staging)
            if (destination.exists()) {
                if (!destination.renameTo(backup)) throw IOException("Cannot replace existing assets")
                previousMoved = true
            }
            if (!staging.renameTo(destination)) throw IOException("Cannot install assets")
        } catch (error: Throwable) {
            if (previousMoved && !destination.exists() && !backup.renameTo(destination)) {
                error.addSuppressed(IOException("Previous assets remain in ${backup.name}"))
            }
            throw error
        } finally {
            staging.deleteRecursively()
            if (destination.exists()) backup.deleteRecursively()
        }
    }

    fun recordInstallation(directory: File) {
        val files = directory.walkTopDown().filter { it.isFile && it.name != MANIFEST }.toList()
        if (files.isEmpty()) throw IOException("Asset package is empty")
        File(directory, MANIFEST).writeText(
            files.joinToString("\n", postfix = "\n") {
                "${it.length()}\t${checksum(it)}\t${it.relativeTo(directory).invariantSeparatorsPath}"
            },
        )
    }

    private fun checksum(file: File): String {
        val crc = CRC32()
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                crc.update(buffer, 0, count)
            }
        }
        return crc.value.toString(16)
    }

    fun copyAtomically(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        val temp = File(destination.parentFile, ".asset-${UUID.randomUUID()}")
        val backup = File(destination.parentFile, ".asset-backup-${UUID.randomUUID()}")
        try {
            source.copyTo(temp, overwrite = true)
            if (destination.exists() && !destination.renameTo(backup)) throw IOException("Cannot replace asset")
            if (!temp.renameTo(destination)) {
                backup.renameTo(destination)
                throw IOException("Cannot install asset")
            }
        } finally {
            temp.delete()
            if (destination.exists()) backup.delete()
        }
    }

    fun remove(directory: File) {
        if (directory.exists() && !directory.deleteRecursively()) throw IOException("Cannot remove assets")
    }

    private fun containedFile(root: File, relative: String): File {
        if (relative.startsWith('/') || ':' in relative || '\t' in relative || '\n' in relative || '\r' in relative ||
            relative.split('/').any { it == ".." }
        ) throw IOException("Unsafe path in asset package")
        val file = File(root, relative).canonicalFile
        if (file != root.canonicalFile && !file.path.startsWith(root.canonicalPath + File.separator)) {
            throw IOException("Unsafe path in asset package")
        }
        return file
    }
}
