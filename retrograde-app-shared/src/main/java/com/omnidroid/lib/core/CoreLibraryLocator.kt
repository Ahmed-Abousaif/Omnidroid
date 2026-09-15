package com.omnidroid.lib.core

import android.content.Context
import com.omnidroid.lib.library.CoreID
import java.io.File

object CoreLibraryLocator {
    fun find(
        context: Context,
        coreID: CoreID,
    ): File? {
        val roots =
            sequenceOf(
                File(context.applicationInfo.nativeLibraryDir),
                context.filesDir,
            )

        return roots
            .flatMap { it.walkBottomUp() }
            .firstOrNull { it.name == coreID.libretroFileName }
    }
}
