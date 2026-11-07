package com.omnidroid.lib.library

import com.omnidroid.lib.controller.ControllerConfig
import com.omnidroid.lib.core.CoreVariable
import java.io.Serializable

data class SystemCoreConfig(
    val coreID: CoreID,
    val controllerConfigs: HashMap<Int, ArrayList<ControllerConfig>>,
    val exposedSettings: List<ExposedSetting> = listOf(),
    val exposedAdvancedSettings: List<ExposedSetting> = listOf(),
    val defaultSettings: List<CoreVariable> = listOf(),
    val statesSupported: Boolean = true,
    val rumbleSupported: Boolean = false,
    val requiredBIOSFiles: List<String> = listOf(),
    val regionalBIOSFiles: Map<String, String> = mapOf(),
    val statesVersion: Int = 0,
    val supportsLibretroVFS: Boolean = false,
    val skipDuplicateFrames: Boolean = true,
    val allowFrameCatchUp: Boolean = true,
    val forceStandardAudioBuffer: Boolean = false,
    // When a core actually runs on Vulkan, prefer MAILBOX present so FIFO
    // vsync waits cannot stall the run/input thread under heavy GPU load.
    // No-op for OpenGL sessions.
    val nonBlockingVulkanPresent: Boolean = true,
    // Decouples swapchain acquire/submit/present to a dedicated worker thread,
    // allowing CPU emulation (retro_run) to overlap fully with GPU presentation.
    val asyncVulkanPresentation: Boolean = true,
    val supportedOnlyArchitectures: Set<String>? = null,
    val supportsMicrophone: Boolean = false,
) : Serializable
