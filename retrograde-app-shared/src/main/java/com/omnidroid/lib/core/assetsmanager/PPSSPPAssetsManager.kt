package com.omnidroid.lib.core.assetsmanager

import android.net.Uri
import com.omnidroid.lib.core.GithubCoreDownloader
import java.io.File

class PPSSPPAssetsManager : ArchiveAssetsManager() {
    override val directoryName = PPSSPP_ASSETS_FOLDER_NAME
    override val versionKey = PPSSPP_ASSETS_VERSION_KEY
    override val version = PPSSPP_ASSETS_VERSION
    override val downloadUrl get() = PPSSPP_ASSETS_URL.toString()

    override fun validate(directory: File): Boolean =
        File(directory, "flash0/font/jpn0.pgf").length() > 0 && File(directory, "lang/en_US.ini").length() > 0

    companion object {
        const val PPSSPP_ASSETS_VERSION = "omnidroid-1"
        const val PPSSPP_ASSETS_VERSION_KEY = "ppsspp_assets_version_key"
        const val PPSSPP_ASSETS_FOLDER_NAME = "PPSSPP"
        val PPSSPP_ASSETS_URL: Uri = Uri.parse(
            "https://raw.githubusercontent.com/Ahmed-Abousaif/OmnidroidCores/${GithubCoreDownloader.CORES_VERSION}/assets/ppsspp.zip",
        )
    }
}
