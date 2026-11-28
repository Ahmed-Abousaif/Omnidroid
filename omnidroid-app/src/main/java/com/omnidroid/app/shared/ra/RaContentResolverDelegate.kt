package com.omnidroid.app.shared.ra

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.omnidroid.lib.game.GameLoader
import com.omnidroid.lib.library.db.entity.DataFile
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.ra.RaFileOpenDelegate
import com.omnidroid.lib.storage.RomFiles
import java.io.File

data class PreparedRaContent(
    val primaryPath: String,
    val openDelegate: RaFileOpenDelegate,
    val dataFiles: List<DataFile>,
)

/**
 * Resolves content paths and ParcelFileDescriptors across standard local files, extracted
 * 7z/zip caches, SAF `content://` documents, `.chd` disc images, `.cue`/`.bin` track sets,
 * and `.m3u` multi-disc playlists without consuming LibretroDroid's active virtual FDs.
 */
object RaContentResolverDelegate {
    fun prepare(
        context: Context,
        game: Game,
        gameData: GameLoader.GameData,
        dataFiles: List<DataFile>,
    ): PreparedRaContent? {
        val exactUris = HashMap<String, Uri>()
        val nameToUri = HashMap<String, Uri>()
        val nameLowerToUri = HashMap<String, Uri>()
        val virtualFallbackFds = HashMap<String, ParcelFileDescriptor>()

        fun registerUri(
            pathOrName: String,
            uriString: String?,
        ) {
            if (uriString.isNullOrBlank()) return
            val parsed = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
            if (!parsed.scheme.equals("content", ignoreCase = true) &&
                !parsed.scheme.equals("file", ignoreCase = true)
            ) {
                return
            }
            exactUris[pathOrName] = parsed
            val baseName = File(pathOrName).name
            if (baseName.isNotBlank()) {
                nameToUri.putIfAbsent(baseName, parsed)
                nameLowerToUri.putIfAbsent(baseName.lowercase(), parsed)
            }
        }

        registerUri(game.fileName, game.fileUri)
        registerUri("/virtual/${game.fileName}", game.fileUri)
        dataFiles.forEach { df ->
            registerUri(df.fileName, df.fileUri)
            registerUri("/virtual/${df.fileName}", df.fileUri)
            df.path?.takeIf { it.isNotBlank() }?.let { registerUri(it, df.fileUri) }
        }

        val primaryPath =
            when (val romFiles = gameData.gameFiles) {
                is RomFiles.Standard -> {
                    romFiles.files.forEach { file ->
                        val uri = Uri.fromFile(file).toString()
                        registerUri(file.absolutePath, uri)
                        registerUri(file.name, uri)
                    }
                    romFiles.files.firstOrNull()?.absolutePath
                }

                is RomFiles.Virtual -> {
                    romFiles.files.forEachIndexed { idx, entry ->
                        val fallbackUri = if (idx == 0) game.fileUri else null
                        if (fallbackUri != null) {
                            registerUri(entry.filePath, fallbackUri)
                        }
                        virtualFallbackFds[entry.filePath] = entry.fd
                        virtualFallbackFds[File(entry.filePath).name] = entry.fd
                    }
                    romFiles.files.firstOrNull()?.filePath
                }
            } ?: return null

        val openDelegate =
            RaFileOpenDelegate { requestedPath ->
                val localFile = File(requestedPath)
                if (localFile.isFile) {
                    return@RaFileOpenDelegate runCatching {
                        ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    }.getOrNull()
                }
                val fileName = File(requestedPath).name
                val matchedUri =
                    exactUris[requestedPath]
                        ?: nameToUri[fileName]
                        ?: nameLowerToUri[fileName.lowercase()]
                if (matchedUri != null) {
                    val opened =
                        if (matchedUri.scheme.equals("file", ignoreCase = true)) {
                            matchedUri.path?.let { path ->
                                File(path).takeIf { it.isFile }?.let { file ->
                                    runCatching {
                                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                                    }.getOrNull()
                                }
                            }
                        } else {
                            runCatching {
                                context.contentResolver.openFileDescriptor(matchedUri, "r")
                            }.getOrNull()
                        }
                    if (opened != null) return@RaFileOpenDelegate opened
                }

                val fallbackFd =
                    virtualFallbackFds[requestedPath]
                        ?: virtualFallbackFds[fileName]
                fallbackFd?.let { fd ->
                    runCatching { ParcelFileDescriptor.dup(fd.fileDescriptor) }.getOrNull()
                }
            }

        return PreparedRaContent(
            primaryPath = primaryPath,
            openDelegate = openDelegate,
            dataFiles = dataFiles,
        )
    }

    fun resolveDiscPath(
        context: Context,
        discIndex: Int,
        game: Game,
        prepared: PreparedRaContent,
    ): String? {
        if (discIndex < 0) return null
        val m3uEntries = readM3uEntries(context, prepared.primaryPath, game)
        val entryName = m3uEntries.getOrNull(discIndex)
        if (!entryName.isNullOrBlank()) {
            val direct = File(entryName)
            if (direct.isAbsolute && direct.isFile) return direct.absolutePath
            val primaryParent = File(prepared.primaryPath).parentFile
            if (primaryParent != null) {
                val sibling = File(primaryParent, entryName)
                if (sibling.isFile) return sibling.absolutePath
            }
            val matchedData =
                prepared.dataFiles.firstOrNull {
                    it.fileName.equals(entryName, ignoreCase = true) ||
                        it.fileName.equals(File(entryName).name, ignoreCase = true)
                }
            if (matchedData != null) {
                return matchedData.path?.takeIf { File(it).isFile } ?: matchedData.fileName
            }
            return entryName
        }

        val candidateData = prepared.dataFiles.getOrNull(discIndex) ?: return null
        return candidateData.path?.takeIf { File(it).isFile } ?: candidateData.fileName
    }

    private fun readM3uEntries(
        context: Context,
        primaryPath: String,
        game: Game,
    ): List<String> {
        val looksLikeM3u =
            primaryPath.endsWith(".m3u", ignoreCase = true) ||
                primaryPath.endsWith(".m3u8", ignoreCase = true) ||
                game.fileName.endsWith(".m3u", ignoreCase = true)
        if (!looksLikeM3u) return emptyList()

        val rawText =
            File(primaryPath).takeIf { it.isFile }?.let { file ->
                runCatching { file.readText() }.getOrNull()
            } ?: runCatching {
                val uri = Uri.parse(game.fileUri)
                context.contentResolver
                    .openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
            }.getOrNull()
                ?: return emptyList()

        return rawText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
    }
}
