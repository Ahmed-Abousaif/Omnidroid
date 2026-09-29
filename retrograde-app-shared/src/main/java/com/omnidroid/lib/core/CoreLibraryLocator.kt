package com.omnidroid.lib.core

import android.content.Context
import com.omnidroid.lib.library.CoreID
import java.io.File

object CoreLibraryLocator {
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
            .firstOrNull { it.name == coreID.libretroFileName }
    }

    /** Cores previously downloaded into app files storage. */
    fun findDownloaded(
        context: Context,
        coreID: CoreID,
    ): File? {
        return context.filesDir
            .walkBottomUp()
            .firstOrNull { it.name == coreID.libretroFileName }
    }
}
