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
            files
                .associateWith { listOf<BaseStorageFile>() }
                .toMutableMap()

        mergeBinCueFiles(allFiles, storageProvider)
        removeInvalidBinCuePairs(allFiles, storageProvider)
        mergeM3UPlaylists(allFiles, storageProvider)
        removeInvalidM3UPlaylists(allFiles, storageProvider)
        mergeMsu1Files(allFiles)

        return allFiles.map { GroupedStorageFiles(it.key, it.value) }
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
                        storageProvider.getInputStream(m3uFile.uri)?.readLines()
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
                        storageProvider.getInputStream(m3uFile.uri)?.readLines()
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

    private fun mergeMsu1Files(
        allFiles: MutableMap<BaseStorageFile, List<BaseStorageFile>>,
    ) {
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
    ): List<String> {
        return runCatching {
            storageProvider.getInputStream(uri)?.readLines()
                ?.mapNotNull { Regex("FILE \"(.*)\"").find(it)?.groupValues?.get(1) }
                ?: listOf()
        }.getOrDefault(listOf())
    }
}
