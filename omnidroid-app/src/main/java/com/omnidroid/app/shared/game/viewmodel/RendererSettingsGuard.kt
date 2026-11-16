package com.omnidroid.app.shared.game.viewmodel

import com.omnidroid.lib.core.CoreVariable

/** A running view/core keeps its launch backend. Preferences take effect on the next launch. */
internal class RendererSettingsGuard(launchOptions: List<CoreVariable>) {
    private val runningOptions = launchOptions.filter { it.key in RENDERER_KEYS }.associate { it.key to it.value }
    private val deferredOptions = mutableMapOf<String, String>()
    private var lastNotifiedOptions = emptyMap<String, String>()

    data class Update(val applicable: List<CoreVariable>, val notifyDeferredChange: Boolean)

    fun filter(options: List<CoreVariable>): Update {
        var hasRendererOptions = false
        val applicable = options.mapNotNull { option ->
            if (option.key !in RENDERER_KEYS) return@mapNotNull option
            hasRendererOptions = true
            val running = runningOptions[option.key]
            if (option.value == running) {
                deferredOptions.remove(option.key)
                option
            } else {
                deferredOptions[option.key] = option.value
                // An absent launch option used the core's default. Do not introduce a new
                // renderer option into that running core, even if both use a GL view.
                running?.let { CoreVariable(option.key, it) }
            }
        }
        val notify = hasRendererOptions && deferredOptions.isNotEmpty() && deferredOptions != lastNotifiedOptions
        if (hasRendererOptions) lastNotifiedOptions = deferredOptions.toMap()
        return Update(applicable, notify)
    }

    companion object {
        private val RENDERER_KEYS = setOf(
            "citra_graphics_api",
            "dolphin_graphics_api",
            "dolphin_renderer",
            "ppsspp_rendering_backend",
            "ppsspp_gpu_backend",
            "armsx2_renderer",
            "cemu_graphics_api",
        )

        fun requestsVulkan(options: List<CoreVariable>): Boolean =
            options.any { it.key in RENDERER_KEYS && it.value.equals("vulkan", ignoreCase = true) }
    }
}
