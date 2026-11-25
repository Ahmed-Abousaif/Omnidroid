package com.omnidroid.lib.core.assetsmanager

import android.content.SharedPreferences
import com.omnidroid.lib.core.CoreUpdater
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class SegaCdAssetsManager : ArchiveAssetsManager() {
    override val directoryName = ASSETS_DIRECTORY
    override val versionKey = "sega_cd_assets_version"
    override val version = "omnidroid-1"
    override val downloadUrl =
        "https://raw.githubusercontent.com/Ahmed-Abousaif/OmnidroidCores/" +
            "d153f1c09f50359be0e6f3454c7eb285884fae61/assets/sega-cd.zip"

    override fun mapEntry(name: String): String? = name.takeIf { it in BIOS_MD5 }

    override fun validate(directory: File): Boolean = BIOS_MD5.keys.all { valid(File(directory, it)) }

    override suspend fun isReady(directoriesManager: DirectoriesManager): Boolean =
        withContext(Dispatchers.IO) { validate(directoriesManager.getSystemDirectory()) }

    override suspend fun retrieveAssetsIfNeeded(
        coreUpdaterApi: CoreUpdater.CoreManagerApi,
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
        force: Boolean,
    ) {
        if (!force && isReady(directoriesManager)) return
        super.retrieveAssetsIfNeeded(coreUpdaterApi, directoriesManager, sharedPreferences, force)
    }

    override fun publish(
        directory: File,
        directories: DirectoriesManager,
        preferences: SharedPreferences,
    ) {
        val owned = preferences.getStringSet(MANAGED_BIOS_KEY, emptySet()).orEmpty().toMutableSet()
        BIOS_MD5.keys.forEach { name ->
            val destination = File(directories.getSystemDirectory(), name)
            if (!valid(destination)) {
                AssetInstallation.copyAtomically(File(directory, name), destination)
                owned += name
            }
        }
        check(preferences.edit().putStringSet(MANAGED_BIOS_KEY, owned).commit()) { "Could not save BIOS installation" }
    }

    override suspend fun clearAssets(
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
    ) {
        withContext(Dispatchers.IO) {
            sharedPreferences
                .getStringSet(MANAGED_BIOS_KEY, emptySet())
                .orEmpty()
                .filter { it in BIOS_MD5 }
                .map { File(directoriesManager.getSystemDirectory(), it) }
                .filter(::valid)
                .forEach(AssetInstallation::remove)
            super.clearAssets(directoriesManager, sharedPreferences)
            check(sharedPreferences.edit().remove(MANAGED_BIOS_KEY).commit()) { "Could not clear BIOS installation" }
        }
    }

    companion object {
        private const val MANAGED_BIOS_KEY = "sega_cd_managed_bios"
        private const val ASSETS_DIRECTORY = ".sega-cd-assets"
        private val BIOS_MD5 =
            mapOf(
                "bios_CD_E.bin" to "e66fa1dc5820d254611fdcdba0662372",
                "bios_CD_J.bin" to "278a9397d192149e84e820ac621a8edd",
                "bios_CD_U.bin" to "2efd74e3232ff260e371b99f84024f7f",
            )

        fun isPublishedBios(
            systemDirectory: File,
            file: File,
        ): Boolean = valid(file) && valid(File(File(systemDirectory, ASSETS_DIRECTORY), file.name))

        private fun valid(file: File): Boolean {
            val expected = BIOS_MD5[file.name] ?: return false
            if (!file.isFile || file.length() != 128 * 1024L) return false
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) } == expected
        }
    }
}
