package com.omnidroid.lib.backend.libretro

import android.content.Context
import com.omnidroid.lib.backend.BackendCapabilities
import com.omnidroid.lib.backend.BackendID
import com.omnidroid.lib.backend.ConsoleBackendDescriptor
import com.omnidroid.lib.backend.RequirementDescriptor
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.SystemCoreConfig
import com.omnidroid.lib.library.SystemID

/**
 * Descriptor for the built-in Libretro emulation backend.
 * Owns all standard libretro cores and consoles.
 */
class LibretroBackendDescriptor : ConsoleBackendDescriptor {
    override val id: BackendID = BackendID.LIBRETRO
    override val displayName: String = "Libretro"

    override val ownedCoreIDs: Set<CoreID> = CoreID.values()
        .filterNot { it == CoreID.SWITCH }
        .toSet()

    override val ownedSystemIDs: Set<SystemID> = SystemID.values()
        .filterNot { it == SystemID.SWITCH }
        .toSet()

    override fun capabilities(config: SystemCoreConfig): BackendCapabilities {
        return BackendCapabilities(
            supportsSaveStates = true,
            supportsDiscSwap = true,
            supportsFastForward = true,
            supportsRewind = true,
            supportsCustomTouchLayout = true,
            supportsShaders = true,
            supportsRumble = true,
        )
    }

    override fun requirementsFor(config: SystemCoreConfig): RequirementDescriptor? {
        return null
    }

    override fun isAvailable(context: Context): Boolean = true
}
