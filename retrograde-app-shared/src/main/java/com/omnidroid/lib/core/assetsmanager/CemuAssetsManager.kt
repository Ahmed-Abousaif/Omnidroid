package com.omnidroid.lib.core.assetsmanager

import android.content.SharedPreferences
import android.net.Uri
import com.omnidroid.lib.core.GithubCoreDownloader
import com.omnidroid.lib.core.CoreUpdater
import com.omnidroid.lib.storage.DirectoriesManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CemuAssetsManager : ArchiveAssetsManager() {
    override val directoryName = ".cemu-assets"
    override val versionKey = CEMU_ASSETS_VERSION_KEY
    override val version = CEMU_ASSETS_VERSION
    override val downloadUrl get() = CEMU_ASSETS_URL.toString()

    override fun mapEntry(name: String): String? =
        "keys.txt".takeIf { name.substringAfterLast('/').equals(it, ignoreCase = true) }

    private fun key(line: String): String? = line.substringBefore('#').trim().lowercase()
        .takeIf { it.matches(Regex("[0-9a-f]{32}")) }

    private fun keys(file: File): Set<String> =
        if (file.isFile) file.readLines().mapNotNull(::key).toSet() else emptySet()

    override fun validate(directory: File): Boolean = keys(File(directory, "keys.txt")).isNotEmpty()

    private fun copies(directories: DirectoriesManager) =
        listOf(File(directories.getSystemDirectory(), "Cemu/keys.txt"), File(directories.getSystemDirectory(), "keys.txt"))

    override fun publish(directory: File, directories: DirectoriesManager, preferences: SharedPreferences) {
        val source = File(directory, "keys.txt")
        val managed = keys(source)
        val destinations = copies(directories)
        val additional = destinations.filter { it.isFile }.flatMap { it.readLines() }
            .filter { key(it)?.let { value -> value !in managed } == true }.distinctBy(::key)
        val merged = if (additional.isEmpty()) source else File.createTempFile("cemu-user-keys", ".txt", directory.parentFile).apply {
            writeText(source.readText().trimEnd() + "\n" + additional.joinToString("\n", postfix = "\n"))
        }
        try {
            destinations.forEach { destination ->
                if (!destination.isFile || !destination.readBytes().contentEquals(merged.readBytes())) {
                    AssetInstallation.copyAtomically(merged, destination)
                }
            }
        } finally {
            if (merged != source) merged.delete()
        }
    }

    override suspend fun isReady(directoriesManager: DirectoriesManager): Boolean =
        withContext(Dispatchers.IO) {
            val managed = keys(File(directory(directoriesManager), "keys.txt"))
            if (!directory(directoriesManager).exists()) copies(directoriesManager).all { keys(it).isNotEmpty() }
            else super.isReady(directoriesManager) && managed.isNotEmpty() &&
                copies(directoriesManager).all { keys(it).containsAll(managed) }
        }

    override suspend fun retrieveAssetsIfNeeded(
        coreUpdaterApi: CoreUpdater.CoreManagerApi,
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
        force: Boolean,
    ) {
        if (!force && !directory(directoriesManager).exists() &&
            sharedPreferences.getString(versionKey, null) == version && isReady(directoriesManager)
        ) return
        super.retrieveAssetsIfNeeded(coreUpdaterApi, directoriesManager, sharedPreferences, force)
    }

    override suspend fun clearAssets(directoriesManager: DirectoriesManager, sharedPreferences: SharedPreferences) {
        withContext(Dispatchers.IO) {
            val managed = keys(File(directory(directoriesManager), "keys.txt"))
            if (managed.isNotEmpty()) copies(directoriesManager).filter { it.isFile }.forEach { destination ->
                val remaining = destination.readLines().filter { key(it) !in managed }
                if (remaining.none { key(it) != null }) AssetInstallation.remove(destination)
                else {
                    val temp = File.createTempFile("cemu-user-keys", ".txt", directory(directoriesManager).parentFile)
                    try {
                        temp.writeText(remaining.joinToString("\n", postfix = "\n"))
                        AssetInstallation.copyAtomically(temp, destination)
                    } finally { temp.delete() }
                }
            }
            super.clearAssets(directoriesManager, sharedPreferences)
        }
    }

    companion object {
        const val CEMU_ASSETS_VERSION = "omnidroid-2"
        const val CEMU_ASSETS_VERSION_KEY = "cemu_assets_version_key"
        val CEMU_ASSETS_URL: Uri = Uri.parse(
            "https://raw.githubusercontent.com/Ahmed-Abousaif/OmnidroidCores/${GithubCoreDownloader.CORES_VERSION}/assets/cemu.zip",
        )
    }
}
