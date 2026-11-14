package com.omnidroid.lib.core.assetsmanager

import android.content.SharedPreferences
import android.net.Uri
import com.omnidroid.lib.bios.BiosManager
import com.omnidroid.lib.bios.Ps2Bios
import com.omnidroid.lib.core.CoreUpdater
import com.omnidroid.lib.core.GithubCoreDownloader
import com.omnidroid.lib.storage.DirectoriesManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PCEE2AssetsManager(private val region: ServedRegion = ServedRegion.USA) : ArchiveAssetsManager() {
    override val directoryName = ".ps2-assets-${region.name}"
    override val versionKey = "${PCEE2_ASSETS_VERSION_KEY}_${region.name}"
    override val version = PCEE2_ASSETS_VERSION
    override val downloadUrl get() = PCEE2_ASSETS_URL.toString()

    override fun mapEntry(name: String): String? =
        name.substringAfterLast('/').takeIf { it.endsWith(".bin", true) && region.matches(it) }

    override fun validate(directory: File): Boolean =
        directory.listFiles().orEmpty().filter { it.extension.equals("bin", true) }
            .any { BiosManager.adoptPs2File(directory, it) }

    override suspend fun isReady(directoriesManager: DirectoriesManager): Boolean =
        withContext(Dispatchers.IO) { BiosManager.refreshPs2Library(directoriesManager.getSystemDirectory()).isNotEmpty() }

    override suspend fun retrieveAssetsIfNeeded(
        coreUpdaterApi: CoreUpdater.CoreManagerApi,
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
        force: Boolean,
    ) {
        if (!force && isReady(directoriesManager)) return
        super.retrieveAssetsIfNeeded(coreUpdaterApi, directoriesManager, sharedPreferences, force)
    }

    override fun publish(directory: File, directories: DirectoriesManager, preferences: SharedPreferences) {
        val owned = preferences.getStringSet(MANAGED_BIOS_KEY, emptySet()).orEmpty().toMutableSet()
        val valid = BiosManager.refreshPs2Library(directories.getSystemDirectory()).map { it.libretroFileName }.toSet()
        BiosManager.ps2Library(directory).listFiles().orEmpty().filter { it.isFile }.forEach { source ->
            val destination = File(BiosManager.ps2Library(directories.getSystemDirectory()), source.name)
            // Existing imported BIOS files remain owned by the user.
            if (!destination.exists() || source.name in owned || source.name !in valid) {
                AssetInstallation.copyAtomically(source, destination)
                owned += source.name
            }
        }
        check(preferences.edit().putStringSet(MANAGED_BIOS_KEY, owned).commit()) { "Could not save BIOS installation" }
    }

    override suspend fun clearAssets(directoriesManager: DirectoriesManager, sharedPreferences: SharedPreferences) {
        withContext(Dispatchers.IO) {
            val system = directoriesManager.getSystemDirectory()
            val owned = sharedPreferences.getStringSet(MANAGED_BIOS_KEY, emptySet()).orEmpty()
            ServedRegion.entries.forEach { region ->
                val cached = File(system, ".ps2-assets-${region.name}")
                BiosManager.ps2Library(cached).listFiles().orEmpty().filter { it.name in owned }.forEach { source ->
                    listOf(File(BiosManager.ps2Library(system), source.name), File(system, "pcsx2/bios/${source.name}"))
                        .filter { it.isFile && it.readBytes().contentEquals(source.readBytes()) }
                        .forEach(AssetInstallation::remove)
                }
                AssetInstallation.remove(cached)
            }
            sharedPreferences.edit().remove(MANAGED_BIOS_KEY).remove(PCEE2_ASSETS_VERSION_KEY).apply {
                ServedRegion.entries.forEach { remove("${PCEE2_ASSETS_VERSION_KEY}_${it.name}") }
            }.commit()
        }
    }

    suspend fun installServedRegion(
        coreUpdaterApi: CoreUpdater.CoreManagerApi,
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
        region: ServedRegion,
    ) {
        com.omnidroid.lib.core.CoreAssets.withLock(directoriesManager, com.omnidroid.lib.library.CoreID.ARMSX2) {
            com.omnidroid.lib.core.retryAssetInstall {
                PCEE2AssetsManager(region).retrieveAssetsIfNeeded(coreUpdaterApi, directoriesManager, sharedPreferences, true)
            }
        }
    }

    companion object {
        const val PCEE2_ASSETS_VERSION = "omnidroid-1"
        const val PCEE2_ASSETS_VERSION_KEY = "pcee2_assets_version_key"
        private const val MANAGED_BIOS_KEY = "ps2_managed_bios"
        val PCEE2_ASSETS_URL: Uri = Uri.parse(
            "https://raw.githubusercontent.com/Ahmed-Abousaif/OmnidroidCores/${GithubCoreDownloader.CORES_VERSION}/assets/pcee2.zip",
        )
    }

    enum class ServedRegion {
        USA, EUROPE, JAPAN;

        fun matches(entryName: String): Boolean {
            val name = entryName.uppercase()
            return when (this) {
                USA -> "39001" in name
                EUROPE -> "39004" in name
                JAPAN -> "30000" in name
            }
        }

        fun fileName(): String = when (this) {
            USA -> Ps2Bios.USA
            EUROPE -> Ps2Bios.EUROPE
            JAPAN -> Ps2Bios.JAPAN_SERVED
        }
    }
}
