package com.omnidroid.app.shared.savesync

import android.content.Context
import android.os.Environment
import com.omnidroid.lib.savesync.CloudSaveFolder
import com.omnidroid.lib.savesync.ConflictStore
import com.omnidroid.lib.savesync.SaveConflict
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class LemuroidMigrationManager(
    private val context: Context,
    private val directoriesManager: DirectoriesManager,
) {
    data class LemuroidSaveStats(
        val romSavesCount: Int,
        val statesCount: Int,
        val previewsCount: Int,
    ) {
        val totalCount: Int get() = romSavesCount + statesCount + previewsCount
    }

    data class MigrationResult(
        val success: Boolean,
        val romSavesImported: Int = 0,
        val statesImported: Int = 0,
        val previewsImported: Int = 0,
        val conflictsCount: Int = 0,
        val errorMessage: String? = null,
    )

    fun getLemuroidFilesDirectory(): File? {
        val candidates = listOfNotNull(
            Environment.getExternalStorageDirectory()?.let { File(it, "Android/data/com.swordfish.lemuroid/files") },
            File("/storage/emulated/0/Android/data/com.swordfish.lemuroid/files"),
            File("/sdcard/Android/data/com.swordfish.lemuroid/files"),
        ).distinct()

        return candidates.firstOrNull { it.exists() && it.canRead() }
    }

    suspend fun detectLemuroidSaves(): LemuroidSaveStats? = withContext(Dispatchers.IO) {
        val dir = getLemuroidFilesDirectory() ?: return@withContext null
        try {
            val savesDir = File(dir, "saves")
            val romSaves = if (savesDir.exists() && savesDir.canRead()) {
                savesDir.listFiles()?.count { it.isFile && it.length() > 0 } ?: 0
            } else 0

            val statesDir = File(dir, "states")
            val states = if (statesDir.exists() && statesDir.canRead()) {
                statesDir.walkTopDown().count { it.isFile && it.length() > 0 && !it.name.endsWith(".metadata") }
            } else 0

            val previewsDir = File(dir, "state-previews")
            val previews = if (previewsDir.exists() && previewsDir.canRead()) {
                previewsDir.walkTopDown().count { it.isFile && it.length() > 0 }
            } else 0

            if (romSaves == 0 && states == 0 && previews == 0) null
            else LemuroidSaveStats(romSaves, states, previews)
        } catch (e: Exception) {
            Timber.w(e, "Could not inspect Lemuroid files directory")
            null
        }
    }

    suspend fun migrateFromLemuroid(): MigrationResult = withContext(Dispatchers.IO) {
        val dir = getLemuroidFilesDirectory()
            ?: return@withContext MigrationResult(false, errorMessage = "Lemuroid directory not accessible")

        val conflicts = ConflictStore(context)
        var savesCount = 0
        var statesCount = 0
        var previewsCount = 0
        var conflictsCount = 0

        try {
            // 1. Migrate saves/ (ROM battery SRAM saves)
            val lemuroidSaves = File(dir, "saves")
            if (lemuroidSaves.exists() && lemuroidSaves.canRead()) {
                val destDir = directoriesManager.getSavesDirectory()
                lemuroidSaves.listFiles()?.filter { it.isFile && it.length() > 0 }?.forEach { srcFile ->
                    val destFile = File(destDir, srcFile.name)
                    if (copyOrConflict(srcFile, destFile, CloudSaveFolder.SAVES.remoteName, srcFile.name, conflicts)) {
                        conflictsCount++
                    }
                    savesCount++
                }
            }

            // 2. Migrate states/ (Core-specific save states and metadata)
            val lemuroidStates = File(dir, "states")
            if (lemuroidStates.exists() && lemuroidStates.canRead()) {
                val destStates = directoriesManager.getStatesDirectory()
                lemuroidStates.walkTopDown().filter { it.isFile && it.length() > 0 }.forEach { srcFile ->
                    val relativePath = srcFile.toRelativeString(lemuroidStates)
                    val destFile = File(destStates, relativePath)
                    destFile.parentFile?.mkdirs()
                    if (copyOrConflict(srcFile, destFile, CloudSaveFolder.STATES.remoteName, relativePath, conflicts)) {
                        conflictsCount++
                    }
                    if (!srcFile.name.endsWith(".metadata")) {
                        statesCount++
                    }
                }
            }

            // 3. Migrate state-previews/ (State preview screenshot thumbnails)
            val lemuroidPreviews = File(dir, "state-previews")
            if (lemuroidPreviews.exists() && lemuroidPreviews.canRead()) {
                val destPreviews = directoriesManager.getStatesPreviewDirectory()
                lemuroidPreviews.walkTopDown().filter { it.isFile && it.length() > 0 }.forEach { srcFile ->
                    val relativePath = srcFile.toRelativeString(lemuroidPreviews)
                    val destFile = File(destPreviews, relativePath)
                    destFile.parentFile?.mkdirs()
                    srcFile.copyTo(destFile, overwrite = true)
                    previewsCount++
                }
            }

            MigrationResult(
                success = true,
                romSavesImported = savesCount,
                statesImported = statesCount,
                previewsImported = previewsCount,
                conflictsCount = conflictsCount,
            )
        } catch (e: Exception) {
            Timber.e(e, "Error during direct Lemuroid migration")
            MigrationResult(false, errorMessage = e.message ?: e.javaClass.simpleName)
        }
    }

    private fun copyOrConflict(
        srcFile: File,
        destFile: File,
        folderName: String,
        relativePath: String,
        conflicts: ConflictStore,
    ): Boolean {
        if (destFile.exists() && destFile.length() > 0) {
            // Check if contents are identical
            if (destFile.length() == srcFile.length() && destFile.readBytes().contentEquals(srcFile.readBytes())) {
                return false
            }
            // Conflict exists: save backup of incoming lemuroid file
            val conflictCopy = File(destFile.parentFile, conflictCopyPath(relativePath))
            conflictCopy.parentFile?.mkdirs()
            srcFile.copyTo(conflictCopy, overwrite = true)
            conflicts.add(
                SaveConflict(
                    id = UUID.randomUUID().toString(),
                    providerId = "lemuroid-migration",
                    folder = folderName,
                    relativePath = relativePath,
                    localPath = destFile.absolutePath,
                    remoteCopyPath = conflictCopy.absolutePath,
                ),
            )
            return true
        } else {
            destFile.parentFile?.mkdirs()
            srcFile.copyTo(destFile, overwrite = true)
            return false
        }
    }

    private fun conflictCopyPath(relativePath: String): String {
        val slash = relativePath.lastIndexOf('/')
        val dir = if (slash >= 0) relativePath.substring(0, slash + 1) else ""
        val name = if (slash >= 0) relativePath.substring(slash + 1) else relativePath
        val dot = name.lastIndexOf('.')
        val base = if (dot >= 0) name.substring(0, dot) else name
        val ext = if (dot >= 0) name.substring(dot) else ""
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "$dir$base (Lemuroid, $date)$ext"
    }
}

