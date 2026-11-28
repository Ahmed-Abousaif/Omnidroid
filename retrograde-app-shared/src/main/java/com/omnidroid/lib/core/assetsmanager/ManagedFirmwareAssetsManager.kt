package com.omnidroid.lib.core.assetsmanager

import android.content.SharedPreferences
import com.omnidroid.lib.core.CoreUpdater
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

internal data class FirmwareFile(
    val size: Long,
    val md5: String,
)

enum class FirmwarePackage(
    val archiveName: String,
    val version: String,
    internal val files: Map<String, FirmwareFile>,
) {
    SATURN(
        "saturn",
        "5c8141e8f3ec6691",
        mapOf(
            "saturn_bios.bin" to FirmwareFile(524288L, "af5828fdff51384f99b3c4926be27762"),
        ),
    ),
    COLECOVISION(
        "colecovision",
        "e653acfff64b1ce5",
        mapOf(
            "colecovision.rom" to FirmwareFile(8192L, "2c66f5911e5b42b8ebe113403548eee7"),
        ),
    ),
    MSX(
        "msx",
        "c7fbef1f0af58ef2",
        mapOf(
            "MSX.ROM" to FirmwareFile(32768L, "364a1a579fe5cb8dba54519bcfcdac0d"),
            "MSX2.ROM" to FirmwareFile(32768L, "ec3a01c91f24fbddcbcab0ad301bc9ef"),
            "MSX2EXT.ROM" to FirmwareFile(16384L, "2183c2aff17cf4297bdb496de78c2e8a"),
            "DISK.ROM" to FirmwareFile(16384L, "80dcd1ad1a4cf65d64b7ba10504e8190"),
            "FMPAC.ROM" to FirmwareFile(65536L, "6f69cc8b5ed761b03afd78000dfb0e19"),
            "MSXDOS2.ROM" to FirmwareFile(65536L, "6418d091cd6907bbcf940324339e43bb"),
            "PAINTER.ROM" to FirmwareFile(65536L, "403cdea1cbd2bb24fae506941f8f655e"),
            "KANJI.ROM" to FirmwareFile(131072L, "febe8782b466d7c3b16de6d104826b34"),
        ),
    ),
}

/** Verified firmware is staged, published and owned separately from user imports. */
open class ManagedFirmwareAssetsManager(
    private val profile: FirmwarePackage,
) : ArchiveAssetsManager() {
    override val directoryName = ".${profile.archiveName}-firmware-assets"
    override val versionKey = "${profile.archiveName}_firmware_version"
    override val version = profile.version
    override val downloadUrl =
        "https://raw.githubusercontent.com/Ahmed-Abousaif/OmnidroidCores/" +
            "master/assets/${profile.archiveName}.zip"
    private val ownedKey = "${profile.archiveName}_managed_firmware"

    override fun openBundledArchive(directories: DirectoriesManager): InputStream? =
        runCatching { directories.context.assets.open("firmware/${profile.archiveName}.zip") }.getOrNull()

    override fun mapEntry(name: String): String? = name.takeIf { it in profile.files }

    override fun validate(directory: File): Boolean = profile.files.keys.all { valid(profile, File(directory, it)) }

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
        val owned = preferences.getStringSet(ownedKey, emptySet()).orEmpty().toMutableSet()
        profile.files.keys.forEach { name ->
            val destination = File(directories.getSystemDirectory(), name)
            if (!valid(profile, destination)) {
                AssetInstallation.copyAtomically(File(directory, name), destination)
                owned += name
            }
        }
        check(preferences.edit().putStringSet(ownedKey, owned).commit()) { "Could not save firmware ownership" }
    }

    override suspend fun clearAssets(
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
    ) {
        withContext(Dispatchers.IO) {
            sharedPreferences
                .getStringSet(ownedKey, emptySet())
                .orEmpty()
                .filter { it in profile.files }
                .map { File(directoriesManager.getSystemDirectory(), it) }
                .filter { valid(profile, it) }
                .forEach(AssetInstallation::remove)
            super.clearAssets(directoriesManager, sharedPreferences)
            check(sharedPreferences.edit().remove(ownedKey).commit()) { "Could not clear firmware ownership" }
        }
    }

    companion object {
        fun isPublishedBios(
            systemDirectory: File,
            file: File,
        ): Boolean =
            FirmwarePackage.entries.any { profile ->
                valid(profile, file) &&
                    valid(profile, File(File(systemDirectory, ".${profile.archiveName}-firmware-assets"), file.name))
            }

        private fun valid(
            profile: FirmwarePackage,
            file: File,
        ): Boolean {
            val spec = profile.files[file.name] ?: return false
            if (!file.isFile || file.length() != spec.size) return false
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) } == spec.md5
        }
    }
}
