package com.omnidroid.lib.core.assetsmanager

import android.net.Uri
import com.omnidroid.lib.core.GithubCoreDownloader
import java.io.File

class DolphinAssetsManager : ArchiveAssetsManager() {
    override val directoryName = "$DOLPHIN_FOLDER_NAME/Sys"
    override val versionKey = DOLPHIN_ASSETS_VERSION_KEY
    override val version = DOLPHIN_ASSETS_VERSION
    override val downloadUrl get() = DOLPHIN_ASSETS_URL.toString()

    override fun mapEntry(name: String): String? {
        val normalized = name.removePrefix("dolphin-emu/")
        if (normalized.startsWith("User/", ignoreCase = true) || normalized == "User") return null
        return normalized.removePrefix("Sys/")
    }

    override fun validate(directory: File): Boolean =
        listOf("GC/dsp_rom.bin", "GC/dsp_coef.bin", "wiitdb-en.txt").all { File(directory, it).length() > 0 }

    companion object {
        const val DOLPHIN_ASSETS_VERSION = "omnidroid-3"
        const val DOLPHIN_ASSETS_VERSION_KEY = "dolphin_assets_version_key"
        const val DOLPHIN_FOLDER_NAME = "dolphin-emu"
        val DOLPHIN_ASSETS_URL: Uri = Uri.parse(
            "https://raw.githubusercontent.com/Ahmed-Abousaif/OmnidroidCores/${GithubCoreDownloader.CORES_VERSION}/assets/dolphin.zip",
        )
    }
}
