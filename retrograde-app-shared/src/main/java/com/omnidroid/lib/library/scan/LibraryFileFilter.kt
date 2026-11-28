package com.omnidroid.lib.library.scan

import com.omnidroid.lib.bios.BiosManager
import com.omnidroid.lib.library.GameSystem

object LibraryFileFilter {
    private val gameExtensions: Set<String> by lazy {
        GameSystem.getSupportedExtensions().map { it.lowercase() }.toSet()
    }

    fun accept(
        name: String,
        size: Long,
    ): Boolean {
        val extension = ScanKeys.extension(name)
        if (name.equals("ICON0.PNG", ignoreCase = true)) return true
        if (extension in gameExtensions || extension in setOf("7z", "img", "sub")) return true
        if (BiosManager.isKnownBiosName(name)) return true
        return extension == "rom" && size in 1..BiosManager.MAX_BIOS_CANDIDATE_BYTES
    }
}
