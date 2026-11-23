package com.omnidroid.lib.backend.ps3

import com.omnidroid.lib.R
import com.omnidroid.lib.core.CoreVariable
import com.omnidroid.lib.library.ExposedSetting

/** Curated graphics controls. Engine internals and debugging controls stay private. */
object Ps3GraphicsOptions {
    enum class Kind { NUMBER, ENUM, BOOL }
    data class Option(val key: String, val path: String, val title: Int, val default: String,
        val kind: Kind, val choices: List<ExposedSetting.Value>, val advanced: Boolean, val restartRequired: Boolean) {
        fun exposed() = ExposedSetting(key, title, ArrayList(choices))
        fun accepts(value: String) = choices.any { it.key == value }
        fun storedValue(raw: Any?): String {
            val value = when (raw) {
                is Boolean -> if (raw) "enabled" else "disabled"
                is String -> raw
                else -> default
            }
            return value.takeIf(::accepts) ?: default
        }
    }

    val all = listOf(
        Option("ps3_resolution_scale", "Video@@Resolution Scale", R.string.setting_ps3_resolution_scale, "100", Kind.NUMBER, listOf(
            ExposedSetting.Value("100", R.string.value_ps3_resolution_scale_0),
            ExposedSetting.Value("75", R.string.value_ps3_resolution_scale_1),
            ExposedSetting.Value("50", R.string.value_ps3_resolution_scale_2),
            ExposedSetting.Value("150", R.string.value_ps3_resolution_scale_3),
            ExposedSetting.Value("200", R.string.value_ps3_resolution_scale_4)
        ), advanced = false, restartRequired = true),
        Option("ps3_frame_limit", "Video@@Frame limit", R.string.setting_ps3_frame_limit, "Auto", Kind.ENUM, listOf(
            ExposedSetting.Value("Auto", R.string.value_ps3_frame_limit_0),
            ExposedSetting.Value("PS3 Native", R.string.value_ps3_frame_limit_1),
            ExposedSetting.Value("30", R.string.value_ps3_frame_limit_2),
            ExposedSetting.Value("60", R.string.value_ps3_frame_limit_3),
            ExposedSetting.Value("120", R.string.value_ps3_frame_limit_4),
            ExposedSetting.Value("Off", R.string.value_ps3_frame_limit_5)
        ), advanced = false, restartRequired = false),
        Option("ps3_vsync", "Video@@VSync Mode", R.string.setting_ps3_vsync, "Disabled", Kind.ENUM, listOf(
            ExposedSetting.Value("Disabled", R.string.value_ps3_vsync_0),
            ExposedSetting.Value("Adaptive", R.string.value_ps3_vsync_1),
            ExposedSetting.Value("Full", R.string.value_ps3_vsync_2)
        ), advanced = false, restartRequired = false),
        Option("ps3_anisotropic", "Video@@Anisotropic Filter Override", R.string.setting_ps3_anisotropic, "0", Kind.NUMBER, listOf(
            ExposedSetting.Value("0", R.string.value_ps3_anisotropic_0),
            ExposedSetting.Value("2", R.string.value_ps3_anisotropic_1),
            ExposedSetting.Value("4", R.string.value_ps3_anisotropic_2),
            ExposedSetting.Value("8", R.string.value_ps3_anisotropic_3),
            ExposedSetting.Value("16", R.string.value_ps3_anisotropic_4)
        ), advanced = false, restartRequired = false),
        Option("ps3_output_scaling", "Video@@Output Scaling Mode", R.string.setting_ps3_output_scaling, "Bilinear", Kind.ENUM, listOf(
            ExposedSetting.Value("Bilinear", R.string.value_ps3_output_scaling_0),
            ExposedSetting.Value("Nearest", R.string.value_ps3_output_scaling_1),
            ExposedSetting.Value("FidelityFX Super Resolution", R.string.value_ps3_output_scaling_2),
            ExposedSetting.Value("Snapdragon Game Super Resolution", R.string.value_ps3_output_scaling_3)
        ), advanced = false, restartRequired = false),
        Option("ps3_sharpening", "Video@@FidelityFX CAS Sharpening Intensity", R.string.setting_ps3_sharpening, "50", Kind.NUMBER, listOf(
            ExposedSetting.Value("0", R.string.value_ps3_sharpening_0),
            ExposedSetting.Value("25", R.string.value_ps3_sharpening_1),
            ExposedSetting.Value("50", R.string.value_ps3_sharpening_2),
            ExposedSetting.Value("75", R.string.value_ps3_sharpening_3),
            ExposedSetting.Value("100", R.string.value_ps3_sharpening_4)
        ), advanced = false, restartRequired = false),
        Option("ps3_shader_mode", "Video@@Shader Mode", R.string.setting_ps3_shader_mode, "Async Recompiler (multi-threaded)", Kind.ENUM, listOf(
            ExposedSetting.Value("Async Recompiler (multi-threaded)", R.string.value_ps3_shader_mode_0),
            ExposedSetting.Value("Legacy Recompiler (single-threaded)", R.string.value_ps3_shader_mode_1),
            ExposedSetting.Value("Async Recompiler with Shader Interpreter", R.string.value_ps3_shader_mode_2)
        ), advanced = true, restartRequired = true),
        Option("ps3_shader_precision", "Video@@Shader Precision", R.string.setting_ps3_shader_precision, "Low", Kind.ENUM, listOf(
            ExposedSetting.Value("Low", R.string.value_ps3_shader_precision_0),
            ExposedSetting.Value("High", R.string.value_ps3_shader_precision_1),
            ExposedSetting.Value("Ultra", R.string.value_ps3_shader_precision_2)
        ), advanced = true, restartRequired = true),
        Option("ps3_msaa", "Video@@MSAA", R.string.setting_ps3_msaa, "Auto", Kind.ENUM, listOf(
            ExposedSetting.Value("Auto", R.string.value_ps3_msaa_0),
            ExposedSetting.Value("Disabled", R.string.value_ps3_msaa_1)
        ), advanced = true, restartRequired = true),
        Option("ps3_write_color_buffers", "Video@@Write Color Buffers", R.string.setting_ps3_write_color_buffers, "disabled", Kind.BOOL, listOf(
            ExposedSetting.Value("disabled", R.string.value_ps3_write_color_buffers_0),
            ExposedSetting.Value("enabled", R.string.value_ps3_write_color_buffers_1)
        ), advanced = true, restartRequired = true),
        Option("ps3_write_depth_buffer", "Video@@Write Depth Buffer", R.string.setting_ps3_write_depth_buffer, "disabled", Kind.BOOL, listOf(
            ExposedSetting.Value("disabled", R.string.value_ps3_write_depth_buffer_0),
            ExposedSetting.Value("enabled", R.string.value_ps3_write_depth_buffer_1)
        ), advanced = true, restartRequired = true),
        Option("ps3_strict_rendering", "Video@@Strict Rendering Mode", R.string.setting_ps3_strict_rendering, "disabled", Kind.BOOL, listOf(
            ExposedSetting.Value("disabled", R.string.value_ps3_strict_rendering_0),
            ExposedSetting.Value("enabled", R.string.value_ps3_strict_rendering_1)
        ), advanced = true, restartRequired = true),
        Option("ps3_high_precision_depth", "Video@@Force High Precision Z buffer", R.string.setting_ps3_high_precision_depth, "disabled", Kind.BOOL, listOf(
            ExposedSetting.Value("disabled", R.string.value_ps3_high_precision_depth_0),
            ExposedSetting.Value("enabled", R.string.value_ps3_high_precision_depth_1)
        ), advanced = true, restartRequired = true),
        Option("ps3_strict_texture_flushing", "Video@@Strict Texture Flushing", R.string.setting_ps3_strict_texture_flushing, "disabled", Kind.BOOL, listOf(
            ExposedSetting.Value("disabled", R.string.value_ps3_strict_texture_flushing_0),
            ExposedSetting.Value("enabled", R.string.value_ps3_strict_texture_flushing_1)
        ), advanced = true, restartRequired = true),
        Option("ps3_disable_zcull", "Video@@Disable ZCull Occlusion Queries", R.string.setting_ps3_disable_zcull, "disabled", Kind.BOOL, listOf(
            ExposedSetting.Value("disabled", R.string.value_ps3_disable_zcull_0),
            ExposedSetting.Value("enabled", R.string.value_ps3_disable_zcull_1)
        ), advanced = true, restartRequired = false),
    )

    val defaults get() = all.map { CoreVariable(it.key, it.default) }
    val settings get() = all.filterNot { it.advanced }.map { it.exposed() }
    val advancedSettings get() = all.filter { it.advanced }.map { it.exposed() }
}
