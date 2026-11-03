package com.omnidroid.lib.backend

/**
 * Declares feature capabilities for a backend under a given system/core configuration.
 * Checked by generic UI and menu layers to dynamically present options.
 */
data class BackendCapabilities(
    val supportsSaveStates: Boolean = false,
    val supportsDiscSwap: Boolean = false,
    val supportsFastForward: Boolean = false,
    val supportsRewind: Boolean = false,
    val supportsCustomTouchLayout: Boolean = false,
    val supportsShaders: Boolean = false,
    val supportsRumble: Boolean = false,
)

