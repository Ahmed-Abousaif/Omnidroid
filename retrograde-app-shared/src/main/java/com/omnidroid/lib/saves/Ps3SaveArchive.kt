package com.omnidroid.lib.saves

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Native save folders use the existing save storage/sync transport as a per-title archive. */
object Ps3SaveArchive {
    private const val MAX_BYTES = 128L * 1024 * 1024

    fun capture(
        savedata: File,
        titleId: String,
    ): ByteArray? {
        require(titleId.matches(Regex("[A-Z]{4}[0-9]{5}")))
        val files =
            savedata
                .listFiles()
                .orEmpty()
                .filter { it.isDirectory && it.name.startsWith(titleId) }
                .flatMap { it.walkTopDown().filter(File::isFile).toList() }
        if (files.isEmpty()) return null
        require(
            files.size <= 4096 && files.sumOf { it.length() } <= MAX_BYTES,
        ) { "PS3 save data is too large to back up." }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for (file in files) {
                check(file.canonicalFile.toPath().startsWith(savedata.canonicalFile.toPath()))
                zip.putNextEntry(
                    ZipEntry(file.relativeTo(savedata).invariantSeparatorsPath).apply {
                        time =
                            file.lastModified()
                    },
                )
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    fun restore(
        savedata: File,
        titleId: String,
        archive: ByteArray,
    ) {
        require(titleId.matches(Regex("[A-Z]{4}[0-9]{5}")))
        val staging = File(savedata.parentFile, ".ps3-save-restore-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        var total = 0L
        var count = 0
        val restored = mutableListOf<Pair<File, Long>>()
        try {
            ZipInputStream(archive.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val parts = entry.name.split('/')
                    require(
                        ++count <= 4096 &&
                            parts.size >= 2 &&
                            parts.first().startsWith(titleId) &&
                            parts.none { it.isBlank() || it == "." || it == ".." || '\\' in it || ':' in it },
                    ) { "Invalid PS3 save archive." }
                    if (entry.isDirectory) continue
                    val target = File(staging, entry.name)
                    check(target.canonicalFile.toPath().startsWith(staging.canonicalFile.toPath()))
                    target.parentFile!!.mkdirs()
                    target.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= MAX_BYTES) { "PS3 save archive is too large." }
                            output.write(buffer, 0, read)
                        }
                    }
                    restored += target to entry.time
                }
            }
            for ((file, time) in restored) {
                val destination = File(savedata, file.relativeTo(staging).path)
                check(destination.canonicalFile.toPath().startsWith(savedata.canonicalFile.toPath()))
                // Native saves written after the last backup take precedence.
                if (!destination.exists() || destination.lastModified() < time) {
                    destination.parentFile!!.mkdirs()
                    file.copyTo(destination, overwrite = true)
                    if (time > 0) destination.setLastModified(time)
                }
            }
        } finally {
            staging.deleteRecursively()
        }
    }
}
