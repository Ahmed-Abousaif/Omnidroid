package com.omnidroid.app.shared.savesync

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.omnidroid.lib.savesync.CloudSaveFolder
import com.omnidroid.lib.savesync.ConflictStore
import com.omnidroid.lib.savesync.SaveConflict
import com.omnidroid.lib.storage.DirectoriesManager
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SaveBackupManager(
    private val context: Context,
    private val directoriesManager: DirectoriesManager,
) {
    fun exportTo(uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest().toByteArray())
                zip.closeEntry()
                folderMap().forEach { (folder, root) ->
                    addDirectory(zip, root, folder.remoteName)
                }
            }
        } ?: throw IllegalStateException("Cannot open export destination")
    }

    fun importFrom(uri: Uri): Int {
        val conflicts = ConflictStore(context)
        var count = 0
        context.contentResolver.openInputStream(uri)?.use { rawInput ->
            val bis = BufferedInputStream(rawInput)
            bis.mark(4)
            val header = ByteArray(4)
            val read = bis.read(header)
            bis.reset()
            val isZip = read >= 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()

            if (isZip) {
                ZipInputStream(bis).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (!entry.isDirectory && !name.endsWith("manifest.json", ignoreCase = true)) {
                            if (mergeStream(zip, name, conflicts)) {
                                count++
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } else {
                val fileName = getFileNameFromUri(uri)
                if (fileName != null && mergeStream(bis, fileName, conflicts)) {
                    count++
                }
            }
        } ?: throw IllegalStateException("Cannot open import source")
        return count
    }

    fun importFromFolder(treeUri: Uri): Int {
        val conflicts = ConflictStore(context)
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
        var count = 0
        traverseDocumentFile(rootDoc, "", 0) { docFile, pathFromRoot ->
            if (!docFile.isDirectory && docFile.name != null && !docFile.name.equals("manifest.json", ignoreCase = true)) {
                context.contentResolver.openInputStream(docFile.uri)?.use { stream ->
                    if (mergeStream(stream, pathFromRoot, conflicts)) {
                        count++
                    }
                }
            }
        }
        return count
    }

    private fun traverseDocumentFile(
        doc: DocumentFile,
        currentPath: String,
        depth: Int,
        onFile: (DocumentFile, String) -> Unit,
    ) {
        if (depth > 10) return
        if (doc.isDirectory) {
            val children = doc.listFiles()
            for (child in children) {
                val childName = child.name ?: continue
                val childPath = if (currentPath.isEmpty()) childName else "$currentPath/$childName"
                if (child.isDirectory) {
                    traverseDocumentFile(child, childPath, depth + 1, onFile)
                } else {
                    onFile(child, childPath)
                }
            }
        } else {
            val name = doc.name ?: return
            val path = if (currentPath.isEmpty()) name else "$currentPath/$name"
            onFile(doc, path)
        }
    }

    private fun mergeStream(
        input: InputStream,
        name: String,
        conflicts: ConflictStore,
    ): Boolean {
        val destination = resolveDestination(name) ?: return false
        val (folder, relative) = destination
        if (relative.isEmpty()) return false
        val root = folderMap()[folder] ?: return false
        val dest = File(root, relative)
        dest.parentFile?.mkdirs()

        if (dest.exists() && dest.length() > 0) {
            val copy = File(root, conflictCopyPath(relative))
            copy.parentFile?.mkdirs()
            copy.outputStream().use { input.copyTo(it) }

            // If identical to existing file, discard duplicate and don't create conflict
            if (dest.length() == copy.length() && dest.readBytes().contentEquals(copy.readBytes())) {
                copy.delete()
                return true
            }

            conflicts.add(
                SaveConflict(
                    id = UUID.randomUUID().toString(),
                    providerId = "backup",
                    folder = folder.remoteName,
                    relativePath = relative,
                    localPath = dest.absolutePath,
                    remoteCopyPath = copy.absolutePath,
                ),
            )
        } else {
            dest.outputStream().use { input.copyTo(it) }
        }
        return true
    }

    /**
     * Resolves an arbitrary path inside a ZIP or folder into a target folder & relative path.
     * Supports standard Omnidroid backups, raw Lemuroid dumps (com.swordfish.lemuroid/files/...),
     * subfolders, and loose .srm / .state files.
     */
    private fun resolveDestination(name: String): Pair<CloudSaveFolder, String>? {
        val normalized = name.replace('\\', '/').trimStart('/')

        return when {
            normalized.startsWith("saves/") ->
                CloudSaveFolder.SAVES to normalized.substringAfter("saves/")
            normalized.contains("/saves/") ->
                CloudSaveFolder.SAVES to normalized.substringAfter("/saves/")

            normalized.startsWith("states/") ->
                CloudSaveFolder.STATES to normalized.substringAfter("states/")
            normalized.contains("/states/") ->
                CloudSaveFolder.STATES to normalized.substringAfter("/states/")

            normalized.startsWith("state-previews/") ->
                CloudSaveFolder.STATE_PREVIEWS to normalized.substringAfter("state-previews/")
            normalized.contains("/state-previews/") ->
                CloudSaveFolder.STATE_PREVIEWS to normalized.substringAfter("/state-previews/")

            normalized.startsWith("profile/") ->
                CloudSaveFolder.PROFILE to normalized.substringAfter("profile/")
            normalized.contains("/profile/") ->
                CloudSaveFolder.PROFILE to normalized.substringAfter("/profile/")

            normalized.endsWith(".srm", ignoreCase = true) || normalized.endsWith(".sav", ignoreCase = true) ->
                CloudSaveFolder.SAVES to normalized.substringAfterLast('/')

            normalized.endsWith(".state", ignoreCase = true) ||
                normalized.endsWith(".state.metadata", ignoreCase = true) ||
                normalized.matches(Regex(""".*\.slot[0-9]+(\.metadata)?""")) -> {
                val parts = normalized.split('/')
                if (parts.size >= 2) {
                    val core = parts[parts.size - 2]
                    val fileName = parts.last()
                    CloudSaveFolder.STATES to "$core/$fileName"
                } else {
                    null
                }
            }

            normalized.endsWith(".jpg", ignoreCase = true) -> {
                val parts = normalized.split('/')
                if (parts.size >= 2) {
                    val core = parts[parts.size - 2]
                    val fileName = parts.last()
                    CloudSaveFolder.STATE_PREVIEWS to "$core/$fileName"
                } else {
                    null
                }
            }

            else -> null
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        return cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) {
                // Ignore fallback to lastPathSegment
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun addDirectory(
        zip: ZipOutputStream,
        root: File,
        prefix: String,
    ) {
        root.walkTopDown()
            .filter { it.isFile && it.length() > 0 }
            .forEach { file ->
                val relative = file.toRelativeString(root)
                zip.putNextEntry(ZipEntry("$prefix/$relative"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
    }

    private fun folderMap() =
        mapOf(
            CloudSaveFolder.SAVES to directoriesManager.getSavesDirectory(),
            CloudSaveFolder.STATES to directoriesManager.getStatesDirectory(),
            CloudSaveFolder.STATE_PREVIEWS to directoriesManager.getStatesPreviewDirectory(),
            CloudSaveFolder.PROFILE to directoriesManager.getProfileDirectory(),
        )

    private fun manifest(): String {
        return JSONObject()
            .put("version", 1)
            .put("createdAt", System.currentTimeMillis())
            .put("device", Build.MODEL)
            .toString()
    }

    private fun conflictCopyPath(relativePath: String): String {
        val slash = relativePath.lastIndexOf('/')
        val dir = if (slash >= 0) relativePath.substring(0, slash + 1) else ""
        val name = if (slash >= 0) relativePath.substring(slash + 1) else relativePath
        val dot = name.lastIndexOf('.')
        val base = if (dot >= 0) name.substring(0, dot) else name
        val ext = if (dot >= 0) name.substring(dot) else ""
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val device = Build.MODEL.replace(Regex("""[\\/:*?"<>|]"""), "_")
        return "$dir$base ($device, $date)$ext"
    }

    companion object {
        fun suggestedFileName(): String {
            val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            return "omnidroid-saves-$date.zip"
        }
    }
}
