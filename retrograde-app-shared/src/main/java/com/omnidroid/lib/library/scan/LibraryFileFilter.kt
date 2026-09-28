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
        if (extension in gameExtensions) return true
        if (BiosManager.isKnownBiosName(name)) return true
        return extension == "rom" && size in 1..BiosManager.MAX_BIOS_CANDIDATE_BYTES
    }
}
