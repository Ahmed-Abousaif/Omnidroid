package com.omnidroid.app.shared.game.backend

import android.content.Context
import android.net.Uri
import com.omnidroid.app.shared.switch_emu.EdenBridge
import com.omnidroid.lib.backend.RequirementAction
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.utils.FileUtil
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object RequirementImporter {
    data class ImportResult(
        val success: Boolean,
        val message: String? = null,
    )

    fun import(context: Context, uri: Uri, action: RequirementAction): ImportResult {
        return when (action) {
            is RequirementAction.FileImport -> importFile(context, uri, action)
            is RequirementAction.ArchiveImport -> importArchive(context, uri, action)
            else -> ImportResult(false, "Unsupported action")
        }
    }

    private fun importFile(context: Context, uri: Uri, action: RequirementAction.FileImport): ImportResult {
        return try {
            val filename = FileUtil.getFilename(uri)
            val extension = FileUtil.getExtension(uri)

            // If the user picked a zip file for an action that expects firmware/contents, route to archive import
            if (extension == "zip" && action.destinationDir.contains("registered")) {
                return importArchive(
                    context,
                    uri,
                    RequirementAction.ArchiveImport(
                        destinationDir = action.destinationDir,
                        fileFilterPattern = ".*\\.nca$",
                    ),
                )
            }

            context.contentResolver.openInputStream(uri)?.use { stream ->
                val destDir = File(context.filesDir, action.destinationDir).apply { mkdirs() }
                val destFile = File(destDir, action.destinationFileName ?: filename)
                FileOutputStream(destFile).use { out ->
                    stream.copyTo(out)
                }

                if (destFile.name == "prod.keys") {
                    runCatching {
                        if (NativeLibrary.isLoaded) {
                            NativeLibrary.reloadKeys()
                        }
                    }
                }
                ImportResult(true, destFile.name)
            } ?: ImportResult(false, "Cannot open stream")
        } catch (e: Exception) {
            Timber.e(e, "Error importing requirement file")
            ImportResult(false, e.message)
        }
    }

    private fun importArchive(context: Context, uri: Uri, action: RequirementAction.ArchiveImport): ImportResult {
        return try {
            val filename = FileUtil.getFilename(uri)
            val extension = FileUtil.getExtension(uri)

            // If the user selected a single .nca file directly instead of a zip
            if (extension == "nca") {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val destDir = File(context.filesDir, action.destinationDir).apply { mkdirs() }
                    val destFile = File(destDir, filename)
                    FileOutputStream(destFile).use { out ->
                        stream.copyTo(out)
                    }
                    if (action.destinationDir.contains("switch")) {
                        runCatching {
                            if (NativeLibrary.isLoaded) {
                                NativeLibrary.initializeSystem(true)
                            }
                        }
                    }
                    return ImportResult(true, "1 file installed")
                }
            }

            // Standard archive (zip) extraction
            context.contentResolver.openInputStream(uri)?.use { stream ->
                if (action.destinationDir.contains("switch") || action.destinationDir.contains("registered")) {
                    val (success, ver) = EdenBridge.importFirmware(context, stream)
                    return ImportResult(success, ver)
                }

                val tempDir = File(context.cacheDir, "import_archive_" + System.currentTimeMillis()).apply { mkdirs() }
                var fileCount = 0
                val pattern = action.fileFilterPattern?.let { Regex(it, RegexOption.IGNORE_CASE) }

                ZipInputStream(BufferedInputStream(stream)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val entryName = File(entry.name).name
                            if (pattern == null || entryName.matches(pattern)) {
                                val outFile = File(tempDir, entryName)
                                FileOutputStream(outFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                                fileCount++
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }

                if (fileCount == 0) {
                    tempDir.deleteRecursively()
                    return ImportResult(false, "No matching files found in archive")
                }

                val targetDir = File(context.filesDir, action.destinationDir).apply { mkdirs() }
                tempDir.copyRecursively(targetDir, overwrite = true)
                tempDir.deleteRecursively()
                ImportResult(true, "$fileCount files imported")
            } ?: ImportResult(false, "Cannot open stream")
        } catch (e: Exception) {
            Timber.e(e, "Error importing requirement archive")
            ImportResult(false, e.message)
        }
    }
}

