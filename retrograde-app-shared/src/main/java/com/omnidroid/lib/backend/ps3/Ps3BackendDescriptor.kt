package com.omnidroid.lib.backend.ps3

import android.content.Context
import android.os.Build
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

class Ps3BackendDescriptor : ConsoleBackendDescriptor {
    override val id = BackendID.PS3
    override val displayName = "ARMSX3"
    override val ownedCoreIDs = setOf(CoreID.ARMSX3)
    override val ownedSystemIDs = setOf(SystemID.PS3)

    override fun capabilities(config: SystemCoreConfig) =
        BackendCapabilities(
            supportsSaveStates = true,
            supportsDiscSwap = false,
            supportsFastForward = false,
            supportsRewind = false,
            supportsCustomTouchLayout = true,
            supportsShaders = false,
            supportsRumble = false,
        )

    override fun isAvailable(context: Context) =
        Build.VERSION.SDK_INT >= 33 &&
            "arm64-v8a" in Build.SUPPORTED_ABIS

    override fun requirementsFor(config: SystemCoreConfig) =
        RequirementDescriptor(
            title = "PlayStation 3 System Files",
            items =
                listOf(
                    RequirementItem(
                        id = "ps3_firmware",
                        title = "PS3 System Firmware",
                        description = "Install your PS3 firmware update (.PUP).",
                        isFulfilled = { firmwareInstalled(it) },
                        statusSummary = {
                            if (firmwareInstalled(it)) {
                                val version =
                                    it
                                        .getSharedPreferences(
                                            "ps3",
                                            Context.MODE_PRIVATE,
                                        ).getString("firmware_version", null)
                                if (version.isNullOrBlank()) "Installed" else "Installed ($version)"
                            } else {
                                "Missing - select a firmware .PUP"
                            }
                        },
                        action = RequirementAction.BackendImport(BackendID.PS3, "firmware", listOf("pup")),
                    ),
                    RequirementItem(
                        id = "ps3_package",
                        title = "Install Game, Update or DLC",
                        description = "Install a PS3 package (.PKG).",
                        severity = RequirementSeverity.OPTIONAL,
                        isFulfilled = { true },
                        statusSummary = { "Select a .PKG file" },
                        action = RequirementAction.BackendImport(BackendID.PS3, "package", listOf("pkg")),
                    ),
                    RequirementItem(
                        id = "ps3_license",
                        title = "Import Game License or Disc Key",
                        description = "Import your .RAP, .KEY or .DKEY file when required by a game.",
                        severity = RequirementSeverity.OPTIONAL,
                        isFulfilled = { true },
                        statusSummary = { "Select a license or key" },
                        action = RequirementAction.BackendImport(BackendID.PS3, "key", listOf("rap", "key", "dkey")),
                    ),
                ),
        )

    companion object {
        fun firmwareInstalled(context: Context) =
            File(context.filesDir, "ps3/config/dev_flash/vsh/module/vsh.self").isFile
    }
}
