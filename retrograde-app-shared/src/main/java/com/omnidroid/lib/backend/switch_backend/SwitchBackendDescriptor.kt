package com.omnidroid.lib.backend.switch_backend

import android.content.Context
import com.omnidroid.lib.backend.BackendCapabilities
import com.omnidroid.lib.backend.BackendID
import com.omnidroid.lib.backend.ConsoleBackendDescriptor
import com.omnidroid.lib.backend.RequirementAction
import com.omnidroid.lib.backend.RequirementDescriptor
import com.omnidroid.lib.backend.RequirementItem
import com.omnidroid.lib.backend.RequirementSeverity
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.SystemCoreConfig
import com.omnidroid.lib.library.SystemID
import java.io.File

/**
 * Descriptor for the embedded Eden Nintendo Switch backend.
 * Base-APK safe: references only file paths and configuration identifiers.
 */
class SwitchBackendDescriptor : ConsoleBackendDescriptor {
    override val id: BackendID = BackendID.SWITCH
    override val displayName: String = "Eden"

    override val ownedCoreIDs: Set<CoreID> = setOf(CoreID.SWITCH)
    override val ownedSystemIDs: Set<SystemID> = setOf(SystemID.SWITCH)

    /**
     * Enabled in Stage (d) now that unified input routing is wired.
     */
    var enableTouchLayout: Boolean = true

    override fun capabilities(config: SystemCoreConfig): BackendCapabilities {
        return BackendCapabilities(
            supportsSaveStates = false,
            supportsDiscSwap = false,
            supportsFastForward = false,
            supportsRewind = false,
            supportsCustomTouchLayout = enableTouchLayout,
            supportsShaders = false,
            supportsRumble = false,
        )
    }

    override fun requirementsFor(config: SystemCoreConfig): RequirementDescriptor {
        return RequirementDescriptor(
            title = "Nintendo Switch System Files",
            items = listOf(
                RequirementItem(
                    id = "prod_keys",
                    title = "Decryption Keys (prod.keys)",
                    description = "Required to decrypt Switch games.",
                    severity = RequirementSeverity.REQUIRED,
                    isFulfilled = { context ->
                        val keysFile = File(context.filesDir, "switch/keys/prod.keys")
                        keysFile.exists() && keysFile.length() > 0
                    },
                    statusSummary = { context ->
                        val keysFile = File(context.filesDir, "switch/keys/prod.keys")
                        if (keysFile.exists() && keysFile.length() > 0) {
                            "Installed"
                        } else {
                            "Missing - Tap to select file"
                        }
                    },
                    action = RequirementAction.FileImport(
                        destinationDir = "switch/keys",
                        destinationFileName = "prod.keys",
                        acceptedExtensions = listOf("keys"),
                        fileTypeLabel = "Switch Keys (*.keys)",
                    ),
                ),
                RequirementItem(
                    id = "switch_firmware",
                    title = "System Firmware",
                    description = "Optional. Some Switch games require system firmware to run or render correctly.",
                    severity = RequirementSeverity.RECOMMENDED,
                    isFulfilled = { context ->
                        val fwDir = File(context.filesDir, "switch/nand/system/Contents/registered")
                        fwDir.exists() && (fwDir.listFiles { _, name -> name.endsWith(".nca", ignoreCase = true) }?.isNotEmpty() == true)
                    },
                    statusSummary = { context ->
                        val fwDir = File(context.filesDir, "switch/nand/system/Contents/registered")
                        val ncas = if (fwDir.exists()) fwDir.listFiles { _, name -> name.endsWith(".nca", ignoreCase = true) } else null
                        if (!ncas.isNullOrEmpty()) {
                            "Installed (${ncas.size} files)"
                        } else {
                            "Not installed (Optional) - Tap to select firmware (.zip)"
                        }
                    },
                    action = RequirementAction.ArchiveImport(
                        destinationDir = "switch/nand/system/Contents/registered",
                        acceptedExtensions = listOf("zip", "nca"),
                        fileTypeLabel = "Firmware Archive (*.zip, *.nca)",
                        fileFilterPattern = ".*\\.nca$",
                    ),
                ),
            ),
        )
    }

    override fun isAvailable(context: Context): Boolean = true
}
