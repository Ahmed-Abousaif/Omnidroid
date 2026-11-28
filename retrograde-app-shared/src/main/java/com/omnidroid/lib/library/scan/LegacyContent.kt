package com.omnidroid.lib.library.scan

import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.SystemID

/** Resolve shared home-computer image suffixes only when the console is known. */
internal object LegacyContent {
    val systems =
        setOf(
            SystemID.ATARI5200,
            SystemID.ATARI800,
            SystemID.SATURN,
            SystemID.THREE_DO,
            SystemID.COLECOVISION,
            SystemID.C64,
            SystemID.MSX,
            SystemID.MSX2,
            SystemID.APPLE2,
            SystemID.JAGUAR,
        )
    private val catalog by lazy { GameSystem.all().filter { it.id in systems } }
    val extensions by lazy { catalog.flatMap { it.supportedExtensions }.toSet() }
    val uniqueExtensions by lazy { catalog.flatMap { it.uniqueExtensions }.toSet() }

    fun hinted(
        path: String?,
        extension: String,
    ): SystemID? =
        FolderHints.system(path)?.takeIf { id -> catalog.any { it.id == id && extension in it.supportedExtensions } }

    fun resolve(
        path: String?,
        extension: String,
    ): SystemID? =
        catalog.singleOrNull { extension in it.uniqueExtensions }?.id ?: hinted(path, extension)
            ?: catalog.singleOrNull { extension in it.supportedExtensions }?.id
}
