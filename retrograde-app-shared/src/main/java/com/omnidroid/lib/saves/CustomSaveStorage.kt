package com.omnidroid.lib.saves

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.omnidroid.lib.R
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Manages reading, writing, and querying game saves and save states from a user-selected custom directory
 * or default internal app storage without unnecessary duplication.
 *
 * Supports offline stashing with deferred sync when a network folder is unreachable,
 * and verified transactional migration when changing save locations.
 */
class CustomSaveStorage(
    private val context: Context,
    private val directoriesManager: DirectoriesManager = DirectoriesManager(context),
) {
    sealed class MigrationResult {
        object Success : MigrationResult()
        data class Failure(val error: String) : MigrationResult()
    }

    private fun getPreferenceKey(): String =
        runCatching { context.getString(R.string.pref_key_custom_saves_folder) }
            .getOrDefault(PREF_KEY_CUSTOM_SAVES_FOLDER)

    fun getCustomSavesFolderUriString(): String? {
        val prefs = SharedPreferencesHelper.getLegacySharedPreferences(context)
        val key = getPreferenceKey()
        return prefs.getString(key, null)?.takeIf { it.isNotBlank() }
    }

    fun isConfigured(): Boolean = getCustomSavesFolderUriString() != null

    fun isReachable(folderUriString: String? = null): Boolean {
        val folderStr = folderUriString ?: getCustomSavesFolderUriString() ?: return true
        return runCatching {
            val uri = Uri.parse(folderStr)
            if (isContentUri(uri)) {
                val doc = DocumentFile.fromTreeUri(context, uri)
                doc != null && doc.exists() && doc.canWrite()
            } else {
                val file = getFileDirectory(uri, folderStr)
                file.exists() && file.canWrite()
            }
        }.getOrDefault(false)
    }

    fun clear() {
        val prefs = SharedPreferencesHelper.getLegacySharedPreferences(context)
        val key = getPreferenceKey()
        prefs.edit().remove(key).apply()
    }

    fun getDisplayName(): String? {
        val folderStr = getCustomSavesFolderUriString() ?: return null
        return runCatching {
            val uri = Uri.parse(folderStr)
            if (isContentUri(uri)) {
                DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment
            } else {
                val path =
                    if (folderStr.startsWith("file://")) {
                        uri.path ?: folderStr
                    } else {
                        folderStr
                    }
                val file = File(path)
                file.name.ifBlank { file.path }
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: folderStr
    }

    // --- Offline Stash Store ---

    private fun getOfflineStashedPaths(): MutableSet<String> {
        val prefs = SharedPreferencesHelper.getLegacySharedPreferences(context)
        return prefs.getStringSet(PREF_KEY_OFFLINE_STASH, emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    private fun markStashedLocally(relativePath: String) {
        val prefs = SharedPreferencesHelper.getLegacySharedPreferences(context)
        val current = getOfflineStashedPaths()
        current.add(relativePath)
        prefs.edit().putStringSet(PREF_KEY_OFFLINE_STASH, current).apply()
    }

    private fun unmarkStashedLocally(relativePath: String) {
        val prefs = SharedPreferencesHelper.getLegacySharedPreferences(context)
        val current = getOfflineStashedPaths()
        if (current.remove(relativePath)) {
            prefs.edit().putStringSet(PREF_KEY_OFFLINE_STASH, current).apply()
        }
    }

    fun isStashedLocally(relativePath: String): Boolean =
        getOfflineStashedPaths().contains(relativePath)

    // --- Unified File Operations ---

    fun readSave(relativePath: String): ByteArray? {
        // If file was stashed locally while offline, read the stashed version
        if (isStashedLocally(relativePath)) {
            val data = readInternal(relativePath)
            if (data != null && data.isNotEmpty()) return data
        }
        val folderStr = getCustomSavesFolderUriString()
        if (folderStr != null) {
            val uri = Uri.parse(folderStr)
            val customData = readFromTarget(uri, folderStr, relativePath)
            if (customData != null && customData.isNotEmpty()) {
                return customData
            }
            // Fallback to internal storage if custom is unreachable or file only exists locally
            return readInternal(relativePath)
        }
        return readInternal(relativePath)
    }

    fun writeSave(
        relativePath: String,
        data: ByteArray,
    ): Boolean {
        if (data.isEmpty()) return false
        val folderStr = getCustomSavesFolderUriString()
        if (folderStr != null) {
            val uri = Uri.parse(folderStr)
            val success = writeToTarget(uri, folderStr, relativePath, data)
            if (success) {
                // Successfully written to custom location; clean up local stash if present
                if (isStashedLocally(relativePath)) {
                    deleteInternal(relativePath)
                    unmarkStashedLocally(relativePath)
                }
                return true
            } else {
                // Custom storage is unreachable; stash locally in internal storage for deferred sync
                Timber.w("Custom storage unreachable; stashed $relativePath locally in internal storage")
                val stashedOk = writeInternal(relativePath, data)
                if (stashedOk) {
                    markStashedLocally(relativePath)
                }
                return stashedOk
            }
        }
        return writeInternal(relativePath, data)
    }

    fun getSaveInfo(relativePath: String): SaveInfo? {
        if (isStashedLocally(relativePath)) {
            val info = getInfoInternal(relativePath)
            if (info != null && info.exists) return info
        }
        val folderStr = getCustomSavesFolderUriString()
        if (folderStr != null) {
            val uri = Uri.parse(folderStr)
            val info = getInfoFromTarget(uri, folderStr, relativePath)
            if (info != null && info.exists) return info
            return getInfoInternal(relativePath)
        }
        return getInfoInternal(relativePath)
    }

    fun deleteSave(relativePath: String): Boolean {
        val folderStr = getCustomSavesFolderUriString()
        if (folderStr != null) {
            val uri = Uri.parse(folderStr)
            val deletedCustom = deleteFromTarget(uri, folderStr, relativePath)
            deleteInternal(relativePath)
            unmarkStashedLocally(relativePath)
            return deletedCustom
        }
        return deleteInternal(relativePath)
    }

    fun renameSave(
        oldRelativePath: String,
        newRelativePath: String,
    ): Boolean {
        val folderStr = getCustomSavesFolderUriString()
        if (folderStr != null) {
            val uri = Uri.parse(folderStr)
            val renamed = renameInTarget(uri, folderStr, oldRelativePath, newRelativePath)
            renameInternal(oldRelativePath, newRelativePath)
            if (isStashedLocally(oldRelativePath)) {
                unmarkStashedLocally(oldRelativePath)
                markStashedLocally(newRelativePath)
            }
            return renamed
        }
        return renameInternal(oldRelativePath, newRelativePath)
    }

    fun listSaveFiles(subPath: String? = null): List<String> {
        val folderStr = getCustomSavesFolderUriString()
        if (folderStr != null) {
            val uri = Uri.parse(folderStr)
            val customFiles = listFromTarget(uri, folderStr, subPath)
            val stashed =
                getOfflineStashedPaths().filter {
                    if (subPath.isNullOrBlank()) true else it.startsWith("$subPath/")
                }.map { it.substringAfterLast('/') }
            return (customFiles + stashed).distinct()
        }
        return listInternal(subPath)
    }

    // --- Deferred Sync for Stashed Files ---

    fun syncOfflineStash(): Boolean {
        if (!isConfigured()) return true
        val folderStr = getCustomSavesFolderUriString() ?: return true
        val stashed = getOfflineStashedPaths()
        if (stashed.isEmpty()) return true

        val uri = Uri.parse(folderStr)
        if (!isReachable(folderStr)) return false

        val toRemove = mutableListOf<String>()
        for (path in stashed) {
            val localFile = getInternalFile(path)
            if (!localFile.exists()) {
                toRemove.add(path)
                continue
            }
            val data = runCatching { localFile.readBytes() }.getOrNull() ?: continue
            val success = writeToTarget(uri, folderStr, path, data)
            if (success) {
                localFile.delete()
                toRemove.add(path)
            }
        }
        if (toRemove.isNotEmpty()) {
            val prefs = SharedPreferencesHelper.getLegacySharedPreferences(context)
            val current = getOfflineStashedPaths()
            current.removeAll(toRemove.toSet())
            prefs.edit().putStringSet(PREF_KEY_OFFLINE_STASH, current).apply()

            cleanEmptyDirectories(directoriesManager.getSavesDirectory())
            cleanEmptyDirectories(directoriesManager.getStatesDirectory())
            cleanEmptyDirectories(directoriesManager.getStatesPreviewDirectory())
        }
        return getOfflineStashedPaths().isEmpty()
    }

    // --- Verified Storage Migration ---

    suspend fun migrateStorage(
        fromUriString: String?,
        toUriString: String?,
    ): MigrationResult =
        withContext(Dispatchers.IO) {
            if (fromUriString == toUriString) return@withContext MigrationResult.Success

            if (fromUriString != null && !isReachable(fromUriString)) {
                return@withContext MigrationResult.Failure("Source storage is not reachable")
            }

            if (toUriString != null && !isReachable(toUriString)) {
                return@withContext MigrationResult.Failure("Destination storage is not reachable or writable")
            }

            // 1. Collect all files from source location
            val filesToMove = listAllFiles(fromUriString)
            if (filesToMove.isEmpty()) {
                setCustomFolderPreference(toUriString)
                return@withContext MigrationResult.Success
            }

            // 2. Copy and verify each file
            val successfullyCopied = mutableListOf<String>()
            for (relativePath in filesToMove) {
                val data = readFromLocation(fromUriString, relativePath)
                if (data == null) {
                    cleanupPartialDestination(toUriString, successfullyCopied)
                    return@withContext MigrationResult.Failure("Failed to read $relativePath from source")
                }
                val writeOk = writeToLocation(toUriString, relativePath, data)
                if (!writeOk) {
                    cleanupPartialDestination(toUriString, successfullyCopied)
                    return@withContext MigrationResult.Failure("Failed to write $relativePath to destination")
                }
                val info = getInfoFromLocation(toUriString, relativePath)
                if (info == null || !info.exists) {
                    cleanupPartialDestination(toUriString, successfullyCopied)
                    return@withContext MigrationResult.Failure("Verification failed for $relativePath at destination")
                }
                successfullyCopied.add(relativePath)
            }

            // 3. Only delete from previous location if EVERY file was moved and verified
            for (relativePath in successfullyCopied) {
                deleteFromLocation(fromUriString, relativePath)
            }

            if (fromUriString == null) {
                cleanEmptyDirectories(directoriesManager.getSavesDirectory())
                cleanEmptyDirectories(directoriesManager.getStatesDirectory())
                cleanEmptyDirectories(directoriesManager.getStatesPreviewDirectory())
            }

            setCustomFolderPreference(toUriString)
            syncOfflineStash()
            MigrationResult.Success
        }

    private fun cleanupPartialDestination(
        toUriString: String?,
        copiedPaths: List<String>,
    ) {
        for (relativePath in copiedPaths) {
            runCatching {
                deleteFromLocation(toUriString, relativePath)
            }
        }
    }

    fun listAllFiles(locationUriString: String?): List<String> {
        if (locationUriString == null) {
            val result = mutableListOf<String>()
            val savesDir = directoriesManager.getSavesDirectory()
            val statesDir = directoriesManager.getStatesDirectory()
            val previewsDir = directoriesManager.getStatesPreviewDirectory()

            savesDir.walkTopDown().filter { it.isFile }.forEach {
                result.add("saves/" + it.relativeTo(savesDir).path.replace('\\', '/'))
            }
            statesDir.walkTopDown().filter { it.isFile }.forEach {
                result.add("states/" + it.relativeTo(statesDir).path.replace('\\', '/'))
            }
            previewsDir.walkTopDown().filter { it.isFile }.forEach {
                result.add("state-previews/" + it.relativeTo(previewsDir).path.replace('\\', '/'))
            }
            return result
        }

        val uri = Uri.parse(locationUriString)
        val files = mutableListOf<String>()
        if (isContentUri(uri)) {
            val rootDoc = DocumentFile.fromTreeUri(context, uri) ?: return emptyList()
            listAllDocumentTreeFiles(rootDoc, "", files)
        } else {
            val root = getFileDirectory(uri, locationUriString)
            if (root.exists()) {
                root.walkTopDown().filter { it.isFile }.forEach {
                    files.add(it.relativeTo(root).path.replace('\\', '/'))
                }
            }
        }
        val stashed = getOfflineStashedPaths()
        return (files + stashed).distinct()
    }

    private fun listAllDocumentTreeFiles(
        dir: DocumentFile,
        currentPath: String,
        outList: MutableList<String>,
    ) {
        val files = dir.listFiles()
        for (file in files) {
            val name = file.name ?: continue
            val relPath = if (currentPath.isEmpty()) name else "$currentPath/$name"
            if (file.isDirectory) {
                listAllDocumentTreeFiles(file, relPath, outList)
            } else {
                outList.add(relPath)
            }
        }
    }

    fun setCustomFolderPreference(toUriString: String?) {
        val prefs = SharedPreferencesHelper.getLegacySharedPreferences(context)
        val key = getPreferenceKey()
        if (toUriString.isNullOrBlank()) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, toUriString).apply()
        }
    }

    // --- Internal Storage Helpers ---

    fun getInternalFile(relativePath: String): File {
        val clean = relativePath.trimStart('/')
        return when {
            clean == "saves" || clean.startsWith("saves/") ->
                File(directoriesManager.getSavesDirectory(), clean.removePrefix("saves/").removePrefix("saves"))
            clean == "states" || clean.startsWith("states/") ->
                File(directoriesManager.getStatesDirectory(), clean.removePrefix("states/").removePrefix("states"))
            clean == "state-previews" || clean.startsWith("state-previews/") ->
                File(directoriesManager.getStatesPreviewDirectory(), clean.removePrefix("state-previews/").removePrefix("state-previews"))
            clean.endsWith(".srm") || clean.endsWith(".sav") ->
                File(directoriesManager.getSavesDirectory(), clean)
            clean.contains(".state") || clean.contains(".slot") ->
                File(directoriesManager.getStatesDirectory(), clean)
            else ->
                File(directoriesManager.getSavesDirectory(), clean)
        }
    }

    private fun writeInternal(
        relativePath: String,
        data: ByteArray,
    ): Boolean =
        runCatching {
            val file = getInternalFile(relativePath)
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                if (!parent.mkdirs()) {
                    return@runCatching false
                }
            }
            if (parent != null && !parent.isDirectory) {
                return@runCatching false
            }
            val tempFile = File(parent, "${file.name}.tmp")
            tempFile.writeBytes(data)
            if (!tempFile.renameTo(file)) {
                file.writeBytes(data)
                tempFile.delete()
            }
            true
        }.getOrDefault(false)

    private fun readInternal(relativePath: String): ByteArray? =
        runCatching {
            val file = getInternalFile(relativePath)
            if (file.exists() && file.length() > 0L) file.readBytes() else null
        }.getOrNull()

    private fun getInfoInternal(relativePath: String): SaveInfo? =
        runCatching {
            val file = getInternalFile(relativePath)
            val exists = file.exists() && file.length() > 0L
            SaveInfo(exists, if (exists) file.lastModified() else 0L)
        }.getOrNull()

    private fun deleteInternal(relativePath: String): Boolean =
        runCatching {
            val file = getInternalFile(relativePath)
            file.delete()
        }.getOrDefault(false)

    private fun renameInternal(
        oldPath: String,
        newPath: String,
    ): Boolean =
        runCatching {
            val oldFile = getInternalFile(oldPath)
            val newFile = getInternalFile(newPath)
            newFile.parentFile?.mkdirs()
            if (oldFile.exists() && !newFile.exists()) {
                oldFile.renameTo(newFile)
            } else {
                false
            }
        }.getOrDefault(false)

    private fun listInternal(subPath: String?): List<String> =
        runCatching {
            if (subPath.isNullOrBlank()) {
                val saves = directoriesManager.getSavesDirectory().listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()
                val states = directoriesManager.getStatesDirectory().listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()
                saves + states
            } else {
                val dir = getInternalFile(subPath)
                dir.listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()
            }
        }.getOrDefault(emptyList())

    // --- Custom Storage Helpers ---

    private fun readFromTarget(
        uri: Uri,
        folderStr: String,
        relativePath: String,
    ): ByteArray? =
        runCatching {
            if (isContentUri(uri)) {
                val rootDoc = DocumentFile.fromTreeUri(context, uri) ?: return@runCatching null
                val fileDoc = findDocumentFile(rootDoc, relativePath) ?: return@runCatching null
                if (!fileDoc.exists() || fileDoc.length() <= 0L) return@runCatching null
                context.contentResolver.openInputStream(fileDoc.uri)?.use { it.readBytes() }
            } else {
                val file = getFile(uri, folderStr, relativePath)
                if (file.exists() && file.length() > 0L) file.readBytes() else null
            }
        }.onFailure {
            Timber.w(it, "Error reading $relativePath from target storage")
        }.getOrNull()

    private fun writeToTarget(
        uri: Uri,
        folderStr: String,
        relativePath: String,
        data: ByteArray,
    ): Boolean =
        runCatching {
            if (isContentUri(uri)) {
                val rootDoc =
                    DocumentFile.fromTreeUri(context, uri)
                        ?: run {
                            Timber.w("Target rootDoc is null for $folderStr")
                            return@runCatching false
                        }
                if (!rootDoc.exists() || !rootDoc.canWrite()) {
                    Timber.w("Target rootDoc is not writable: $folderStr")
                    return@runCatching false
                }
                val targetDoc =
                    findOrCreateDocumentFile(rootDoc, relativePath)
                        ?: run {
                            Timber.w("Could not find or create $relativePath in target folder")
                            return@runCatching false
                        }
                context.contentResolver.openOutputStream(targetDoc.uri, "wt")?.use { stream ->
                    stream.write(data)
                    stream.flush()
                } ?: run {
                    Timber.w("Could not open output stream for ${targetDoc.uri}")
                    return@runCatching false
                }
                true
            } else {
                val file = getFile(uri, folderStr, relativePath)
                file.parentFile?.mkdirs()
                val tempFile = File(file.parentFile, "${file.name}.tmp")
                tempFile.writeBytes(data)
                if (tempFile.renameTo(file)) {
                    true
                } else {
                    file.writeBytes(data)
                    true
                }
            }
        }.onFailure {
            Timber.w(it, "Error writing $relativePath to target storage")
        }.getOrDefault(false)

    private fun getInfoFromTarget(
        uri: Uri,
        folderStr: String,
        relativePath: String,
    ): SaveInfo? =
        runCatching {
            if (isContentUri(uri)) {
                val rootDoc = DocumentFile.fromTreeUri(context, uri) ?: return@runCatching null
                val fileDoc = findDocumentFile(rootDoc, relativePath) ?: return@runCatching null
                val exists = fileDoc.exists() && fileDoc.length() > 0L
                SaveInfo(exists, if (exists) fileDoc.lastModified() else 0L)
            } else {
                val file = getFile(uri, folderStr, relativePath)
                val exists = file.exists() && file.length() > 0L
                SaveInfo(exists, if (exists) file.lastModified() else 0L)
            }
        }.getOrNull()

    private fun deleteFromTarget(
        uri: Uri,
        folderStr: String,
        relativePath: String,
    ): Boolean =
        runCatching {
            if (isContentUri(uri)) {
                val rootDoc = DocumentFile.fromTreeUri(context, uri) ?: return@runCatching false
                val fileDoc = findDocumentFile(rootDoc, relativePath) ?: return@runCatching false
                fileDoc.delete()
            } else {
                val file = getFile(uri, folderStr, relativePath)
                file.delete()
            }
        }.getOrDefault(false)

    private fun renameInTarget(
        uri: Uri,
        folderStr: String,
        oldRelativePath: String,
        newRelativePath: String,
    ): Boolean =
        runCatching {
            if (isContentUri(uri)) {
                val rootDoc = DocumentFile.fromTreeUri(context, uri) ?: return@runCatching false
                val fileDoc = findDocumentFile(rootDoc, oldRelativePath) ?: return@runCatching false
                val newName = newRelativePath.substringAfterLast('/')
                fileDoc.renameTo(newName)
            } else {
                val oldFile = getFile(uri, folderStr, oldRelativePath)
                val newFile = getFile(uri, folderStr, newRelativePath)
                newFile.parentFile?.mkdirs()
                if (oldFile.exists() && !newFile.exists()) {
                    oldFile.renameTo(newFile)
                } else {
                    false
                }
            }
        }.getOrDefault(false)

    private fun listFromTarget(
        uri: Uri,
        folderStr: String,
        subPath: String?,
    ): List<String> =
        runCatching {
            if (isContentUri(uri)) {
                val rootDoc = DocumentFile.fromTreeUri(context, uri) ?: return@runCatching emptyList()
                val targetDir = if (subPath.isNullOrBlank()) rootDoc else findDocumentFile(rootDoc, subPath)
                targetDir
                    ?.listFiles()
                    ?.filter { !it.isDirectory }
                    ?.mapNotNull { it.name } ?: emptyList()
            } else {
                val root = getFileDirectory(uri, folderStr)
                val targetDir = if (subPath.isNullOrBlank()) root else File(root, subPath)
                targetDir
                    .listFiles()
                    ?.filter { it.isFile }
                    ?.map { it.name } ?: emptyList()
            }
        }.getOrDefault(emptyList())

    // --- Location Abstraction Helpers ---

    private fun readFromLocation(
        uriString: String?,
        relativePath: String,
    ): ByteArray? =
        if (uriString == null) {
            readInternal(relativePath)
        } else {
            if (isStashedLocally(relativePath)) {
                readInternal(relativePath) ?: readFromTarget(Uri.parse(uriString), uriString, relativePath)
            } else {
                readFromTarget(Uri.parse(uriString), uriString, relativePath)
            }
        }

    private fun writeToLocation(
        uriString: String?,
        relativePath: String,
        data: ByteArray,
    ): Boolean =
        if (uriString == null) {
            writeInternal(relativePath, data)
        } else {
            writeToTarget(Uri.parse(uriString), uriString, relativePath, data)
        }

    private fun getInfoFromLocation(
        uriString: String?,
        relativePath: String,
    ): SaveInfo? =
        if (uriString == null) {
            getInfoInternal(relativePath)
        } else {
            getInfoFromTarget(Uri.parse(uriString), uriString, relativePath)
        }

    private fun deleteFromLocation(
        uriString: String?,
        relativePath: String,
    ): Boolean =
        if (uriString == null) {
            deleteInternal(relativePath)
        } else {
            if (isStashedLocally(relativePath)) {
                deleteInternal(relativePath)
                unmarkStashedLocally(relativePath)
            }
            deleteFromTarget(Uri.parse(uriString), uriString, relativePath)
        }

    private fun cleanEmptyDirectories(directory: File) {
        directory.listFiles()?.filter { it.isDirectory }?.forEach { subDir ->
            cleanEmptyDirectories(subDir)
            if (subDir.listFiles().isNullOrEmpty()) {
                subDir.delete()
            }
        }
    }

    private fun findDocumentFile(
        rootDoc: DocumentFile,
        relativePath: String,
    ): DocumentFile? {
        val segments = relativePath.split('/').filter { it.isNotEmpty() }
        var current: DocumentFile = rootDoc
        for (segment in segments) {
            current = current.findFile(segment) ?: return null
        }
        return current
    }

    private fun findOrCreateDocumentFile(
        rootDoc: DocumentFile,
        relativePath: String,
        mimeType: String = "application/octet-stream",
    ): DocumentFile? {
        val segments = relativePath.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        var current: DocumentFile = rootDoc
        for (i in 0 until segments.size - 1) {
            val dirName = segments[i]
            val nextDir =
                current.findFile(dirName)?.takeIf { it.isDirectory }
                    ?: current.createDirectory(dirName)
                    ?: return null
            current = nextDir
        }
        val fileName = segments.last()
        return current.findFile(fileName) ?: current.createFile(mimeType, fileName)
    }

    private fun isContentUri(uri: Uri): Boolean = uri.scheme == "content"

    private fun getFileDirectory(
        uri: Uri,
        folderStr: String,
    ): File =
        if (uri.scheme == "file") {
            File(uri.path ?: folderStr)
        } else {
            File(folderStr)
        }

    private fun getFile(
        uri: Uri,
        folderStr: String,
        relativePath: String,
    ): File {
        val root = getFileDirectory(uri, folderStr)
        return File(root, relativePath)
    }

    companion object {
        const val PREF_KEY_CUSTOM_SAVES_FOLDER = "custom_saves_folder"
        private const val PREF_KEY_OFFLINE_STASH = "custom_saves_offline_stash"
    }
}
