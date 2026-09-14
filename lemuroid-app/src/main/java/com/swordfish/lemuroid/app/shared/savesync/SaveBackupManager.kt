package com.swordfish.lemuroid.app.shared.savesync

import android.content.Context
import android.net.Uri
import android.os.Build
import com.swordfish.lemuroid.lib.savesync.CloudSaveFolder
import com.swordfish.lemuroid.lib.savesync.ConflictStore
import com.swordfish.lemuroid.lib.savesync.SaveConflict
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import org.json.JSONObject
import java.io.File
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

    fun importFrom(uri: Uri) {
        val conflicts = ConflictStore(context)
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && name != "manifest.json") {
                        mergeEntry(zip, name, conflicts)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: throw IllegalStateException("Cannot open import source")
    }

    private fun mergeEntry(
        zip: ZipInputStream,
        name: String,
        conflicts: ConflictStore,
    ) {
        val folderName = name.substringBefore('/')
        val relative = name.substringAfter('/', missingDelimiterValue = "")
        if (relative.isEmpty()) return
        val root = folderMap().entries.firstOrNull { it.key.remoteName == folderName }?.value ?: return
        val dest = File(root, relative)
        dest.parentFile?.mkdirs()
        if (dest.exists() && dest.length() > 0) {
            val copy = File(root, conflictCopyPath(relative))
            copy.parentFile?.mkdirs()
            copy.outputStream().use { zip.copyTo(it) }
            conflicts.add(
                SaveConflict(
                    id = UUID.randomUUID().toString(),
                    providerId = "backup",
                    folder = folderName,
                    relativePath = relative,
                    localPath = dest.absolutePath,
                    remoteCopyPath = copy.absolutePath,
                ),
            )
        } else {
            dest.outputStream().use { zip.copyTo(it) }
        }
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
            return "lemuroid-saves-$date.zip"
        }
    }
}
