package com.omnidroid.lib.core

import android.content.Context
import com.omnidroid.lib.library.CoreID
import java.io.File

object CoreLibraryLocator {
    /** Installed binaries from older core releases still need an update. */
    fun findCurrent(
        context: Context,
        coreID: CoreID,
    ): File? =
        findBundled(context, coreID)
            ?: GithubCoreDownloader.findCached(File(context.filesDir, "cores"), coreID)

    fun find(
        context: Context,
        coreID: CoreID,
    ): File? {
        return findBundled(context, coreID) ?: findDownloaded(context, coreID)
    }

    /** Cores packaged inside the APK / extracted native lib dir. */
    fun findBundled(
        context: Context,
        coreID: CoreID,
    ): File? {
        return File(context.applicationInfo.nativeLibraryDir)
            .walkBottomUp()
            .firstOrNull { it.name == coreID.libretroFileName && it.isFile && it.length() > 0L }
    }

    /** Cores previously downloaded into app files storage. */
    fun findDownloaded(
        context: Context,
        coreID: CoreID,
    ): File? {
        GithubCoreDownloader.findCached(File(context.filesDir, "cores"), coreID)?.let { return it }
        return context.filesDir
            .walkTopDown()
            .onEnter { it != File(context.filesDir, "ps3") }
            .firstOrNull { it.name == coreID.libretroFileName && it.isFile && it.length() > 0L }
    }
}
