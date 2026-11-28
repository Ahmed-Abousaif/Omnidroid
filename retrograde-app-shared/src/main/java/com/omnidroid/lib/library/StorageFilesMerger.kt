package com.omnidroid.lib.library

import android.net.Uri
import com.omnidroid.common.files.readLines
import com.omnidroid.lib.storage.BaseStorageFile
import com.omnidroid.lib.storage.GroupedStorageFiles
import com.omnidroid.lib.storage.StorageProvider

object StorageFilesMerger {
    private val MSU1_ROM_EXTENSIONS = setOf("sfc", "smc", "fig", "swc", "bs")
    private val MSU1_COMPANION_EXTENSIONS = setOf("msu", "pcm", "msuv", "bml", "xml")

    /** Merge files which belong to the same game. This includes bin/cue files, m3u playlists, and MSU-1 game files.*/
    fun mergeDataFiles(
        storageProvider: StorageProvider,
        files: List<BaseStorageFile>,
    ): List<GroupedStorageFiles> {
        val allFiles =
            ps3GameFiles(storageProvider, files)
                .associateWith { listOf<BaseStorageFile>() }
                .toMutableMap()

        val ps3Icons = files.filter { it.name.equals("ICON0.PNG", true) }
        allFiles.keys.filter { it.name.equals("PARAM.SFO", true) }.forEach { descriptor ->
            val parent = (descriptor.path ?: descriptor.uri.path.orEmpty()).substringBeforeLast('/')
            allFiles[descriptor] =
                ps3Icons.filter { (it.path ?: it.uri.path.orEmpty()).substringBeforeLast('/') == parent }
        }
        mergeBinCueFiles(allFiles, storageProvider)
        removeInvalidBinCuePairs(allFiles, storageProvider)
        mergeDiscDescriptors(allFiles)
        mergeM3UPlaylists(allFiles, storageProvider)
        removeInvalidM3UPlaylists(allFiles, storageProvider)
        mergeMsu1Files(allFiles)

        return allFiles.map { GroupedStorageFiles(it.key, it.value) }
    }

    private fun ps3GameFiles(
        provider: StorageProvider,
        files: List<BaseStorageFile>,
    ): List<BaseStorageFile> {
        fun path(file: BaseStorageFile) = (file.path ?: file.uri.path.orEmpty()).replace('\\', '/')
        val descriptors =
            files.filter { file ->
                file.name.equals("PARAM.SFO", true) &&
                    runCatching {
                        provider.openRandomAccess(file.uri)?.use { source ->
                            com.omnidroid.lib.library.scan.Ps3Content.parse(
                                source.read(0, minOf(file.size, 1024 * 1024L).toInt()),
                            )
                        } != null
                    }.getOrDefault(false)
            }
        val roots =
            descriptors.map { file ->
                val parent = path(file).substringBeforeLast('/')
                if (parent.endsWith("/PS3_GAME", true)) parent.substringBeforeLast('/') else parent
            }
        return files.filter { file ->
            !file.name.equals("ICON0.PNG", true) &&
                (file in descriptors || roots.none { path(file).startsWith("$it/", ignoreCase = true) })
        }
    }

    private fun mergeDiscDescriptors(allFiles: MutableMap<BaseStorageFile, List<BaseStorageFile>>) {
        val removed = mutableSetOf<BaseStorageFile>()

        fun parent(file: BaseStorageFile) =
            (file.path ?: file.uri.toString()).replace('\\', '/').substringBeforeLast('/')
        allFiles.keys.filter { it.extension.lowercase() in setOf("ccd", "mds") }.toList().forEach { descriptor ->
            val extensions = if (descriptor.extension.equals("ccd", true)) setOf("img", "sub") else setOf("mdf")
            val companions =
                allFiles.keys.filter {
                    it != descriptor &&
                        it.extension.lowercase() in extensions &&
                        it.extensionlessName.equals(descriptor.extensionlessName, true) &&
                        parent(it) == parent(descriptor)
                }
            allFiles[descriptor] = allFiles[descriptor].orEmpty() + companions
            removed.addAll(companions)
        }
        removed.forEach { allFiles.remove(it) }
    }

    private fun removeInvalidM3UPlaylists(
        allFiles: MutableMap<BaseStorageFile, List<BaseStorageFile>>,
        storageProvider: StorageProvider,
    ) {
        val toBeRemoved = mutableListOf<BaseStorageFile>()

        allFiles.keys
            .asSequence()
            .filter { it.extension == "m3u" }
            .forEach { m3uFile ->
                val m3uFiles: List<String> =
                    runCatching {
                        storageProvider
                            .getInputStream(m3uFile.uri)
                            ?.readLines()
                            ?.map { it.trim() }
                            ?.filter { it.isNotEmpty() && !it.startsWith("#") }
                            ?.map { it.substringAfterLast('/').substringAfterLast('\\') }
                    }.getOrNull() ?: listOf()

                val filesNames = allFiles[m3uFile]?.map { it.name } ?: listOf()

                if (!filesNames.containsAll(m3uFiles)) {
                    toBeRemoved.add(m3uFile)
                }
            }

        toBeRemoved.forEach { allFiles.remove(it) }
    }

    private fun mergeM3UPlaylists(
        allFiles: MutableMap<BaseStorageFile, List<BaseStorageFile>>,
        storageProvider: StorageProvider,
    ) {
        val toBeRemoved = mutableListOf<BaseStorageFile>()

        allFiles.keys
            .asSequence()
            .filter { it.extension == "m3u" }
            .forEach { m3uFile ->
                val m3uFiles =
                    runCatching {
                        storageProvider
                            .getInputStream(m3uFile.uri)
                            ?.readLines()
                            ?.map { it.trim() }
                            ?.filter { it.isNotEmpty() && !it.startsWith("#") }
                            ?.map { it.substringAfterLast('/').substringAfterLast('\\') }
                    }.getOrNull() ?: listOf()

                val dataFiles = allFiles.filter { it.key.name in m3uFiles }

                allFiles[m3uFile] = allFiles[m3uFile]!! +
                    dataFiles.flatMap {
                        listOf(it.key) + it.value
                    }
                toBeRemoved.addAll(dataFiles.keys)
            }

        toBeRemoved.forEach { allFiles.remove(it) }
    }

    private fun removeInvalidBinCuePairs(
        allFiles: MutableMap<BaseStorageFile, List<BaseStorageFile>>,
        storageProvider: StorageProvider,
    ) {
        val toBeRemoved = mutableListOf<BaseStorageFile>()

        allFiles.keys
            .asSequence()
            .filter { it.extension == "cue" }
            .forEach {
                val requestedFileNames = extractBinFiles(storageProvider, it.uri).toSet()
                val givenFileNames = allFiles[it]?.map { it.name }?.toSet() ?: setOf()

                if (requestedFileNames != givenFileNames) toBeRemoved.add(it)
            }

        toBeRemoved.forEach { allFiles.remove(it) }
    }

    private fun mergeBinCueFiles(
        allFiles: MutableMap<BaseStorageFile, List<BaseStorageFile>>,
        storageProvider: StorageProvider,
    ) {
        val toBeRemoved = mutableListOf<BaseStorageFile>()

        allFiles.keys
            .asSequence()
            .filter { it.extension == "cue" }
            .forEach { cueFile ->
                val requestedBinFiles = extractBinFiles(storageProvider, cueFile.uri)

                val binFiles =
                    allFiles
                        .filter { it.key.name in requestedBinFiles }

                allFiles[cueFile] = (allFiles[cueFile] ?: listOf()) +
                    binFiles.flatMap {
                        listOf(it.key) + it.value
                    }
                toBeRemoved.addAll(binFiles.keys)
            }

        toBeRemoved.forEach { allFiles.remove(it) }
    }

    private fun mergeMsu1Files(allFiles: MutableMap<BaseStorageFile, List<BaseStorageFile>>) {
        val toBeRemoved = mutableListOf<BaseStorageFile>()

        // Process longer ROM names first so more specific names claim their companion files first
        val romFiles =
            allFiles.keys
                .filter { it.extension.lowercase() in MSU1_ROM_EXTENSIONS }
                .sortedByDescending { it.extensionlessName.length }

        romFiles.forEach { romFile ->
            val baseName = romFile.extensionlessName

            val companionFiles =
                allFiles.keys.filter { candidate ->
                    candidate != romFile &&
                        candidate !in toBeRemoved &&
                        candidate.extension.lowercase() in MSU1_COMPANION_EXTENSIONS &&
                        (
                            candidate.extensionlessName.equals(baseName, ignoreCase = true) ||
                                candidate.extensionlessName.startsWith("$baseName-", ignoreCase = true) ||
                                candidate.extensionlessName.startsWith("${baseName}_", ignoreCase = true)
                        )
                }

            if (companionFiles.isNotEmpty()) {
                allFiles[romFile] = (allFiles[romFile] ?: listOf()) +
                    companionFiles.flatMap { candidate ->
                        listOf(candidate) + (allFiles[candidate] ?: listOf())
                    }
                toBeRemoved.addAll(companionFiles)
            }
        }

        toBeRemoved.forEach { allFiles.remove(it) }
    }

    private fun extractBinFiles(
        storageProvider: StorageProvider,
        uri: Uri,
    ): List<String> =
        runCatching {
            storageProvider
                .getInputStream(uri)
                ?.readLines()
                ?.mapNotNull {
                    Regex(
                        "FILE \"(.*)\"",
                        RegexOption.IGNORE_CASE,
                    ).find(it)?.groupValues?.get(1)?.substringAfterLast('/')?.substringAfterLast('\\')
                }
                ?: listOf()
        }.getOrDefault(listOf())
}
