package com.swordfish.lemuroid.lib.core

import android.content.Context
import com.swordfish.lemuroid.lib.library.CoreID
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
