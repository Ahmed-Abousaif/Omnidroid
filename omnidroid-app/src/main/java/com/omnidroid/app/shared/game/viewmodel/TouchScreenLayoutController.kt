package com.omnidroid.app.shared.game.viewmodel

import android.content.SharedPreferences
import androidx.core.content.edit
import com.omnidroid.lib.core.CoreVariable
import com.omnidroid.lib.core.CoreVariablesManager
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.SystemID
import com.omnidroid.touchinput.radial.settings.TouchControllerSettingsManager

/**
 * Toggles handheld/Wii U screen layouts using the in-game Settings preference keys.
 */
class TouchScreenLayoutController(
    private val sharedPreferences: SharedPreferences,
    private val systemId: SystemID,
    private val coreId: CoreID,
) {
    /**
     * Next screen layout, used by both the on-screen layout button and the gamepad shortcut.
     * DS/3DS cycle four states: dual -> dual swapped -> top only -> bottom only -> dual.
     * Cemu keeps its TV <-> dual toggle.
     */
    fun cycle(
        orientation: TouchControllerSettingsManager.Orientation,
    ): List<CoreVariable> {
        return when (coreId) {
            CoreID.MELONDS_DS -> cycleMelonDsDs(orientation)
            CoreID.CITRA, CoreID.AZAHAR -> cycleCitra(orientation)
            CoreID.CEMU -> toggleCemu(orientation)
            else -> emptyList()
        }
    }

    private fun cycleMelonDsDs(orientation: TouchControllerSettingsManager.Orientation): List<CoreVariable> {
        val key = CoreVariablesManager.computeSharedPreferenceKey(MELONDSDS_LAYOUT_KEY, systemId.dbname)
        val next = ScreenLayoutCycle.nextMelonDsDs(sharedPreferences.getString(key, null), orientation.isLandscape())
        sharedPreferences.edit { putString(key, next) }
        return listOf(CoreVariable(MELONDSDS_LAYOUT_KEY, next))
    }

    private fun cycleCitra(orientation: TouchControllerSettingsManager.Orientation): List<CoreVariable> {
        val layoutKey = CoreVariablesManager.computeSharedPreferenceKey(CITRA_LAYOUT_KEY, systemId.dbname)
        val swapKey = CoreVariablesManager.computeSharedPreferenceKey(CITRA_SWAP_KEY, systemId.dbname)
        val (layout, swap) =
            ScreenLayoutCycle.nextCitra(
                layout = sharedPreferences.getString(layoutKey, null),
                swap = sharedPreferences.getString(swapKey, null),
                isLandscape = orientation.isLandscape(),
            )
        sharedPreferences.edit {
            putString(layoutKey, layout)
            putString(swapKey, swap)
        }
        return listOf(CoreVariable(CITRA_LAYOUT_KEY, layout), CoreVariable(CITRA_SWAP_KEY, swap))
    }

    private fun TouchControllerSettingsManager.Orientation.isLandscape() =
        this == TouchControllerSettingsManager.Orientation.LANDSCAPE

    fun supportsToggle(): Boolean =
        coreId == CoreID.MELONDS_DS || coreId == CoreID.CITRA || coreId == CoreID.AZAHAR || coreId == CoreID.CEMU

    fun getAspectRatio(orientation: TouchControllerSettingsManager.Orientation): Float {
        val isLandscape = orientation == TouchControllerSettingsManager.Orientation.LANDSCAPE
        return when (coreId) {
            CoreID.MELONDS_DS -> {
                val key = CoreVariablesManager.computeSharedPreferenceKey(MELONDSDS_LAYOUT_KEY, systemId.dbname)
                val current = sharedPreferences.getString(key, null)
                when (current) {
                    MELONDSDS_SINGLE_TOP, MELONDSDS_SINGLE_BOTTOM -> 4f / 3f
                    MELONDSDS_DUAL_LANDSCAPE, MELONDSDS_DUAL_LANDSCAPE_SWAPPED -> 8f / 3f
                    MELONDSDS_DUAL_PORTRAIT, MELONDSDS_DUAL_PORTRAIT_SWAPPED -> 4f / 6f
                    else -> if (isLandscape) 8f / 3f else 4f / 6f
                }
            }
            CoreID.CITRA, CoreID.AZAHAR -> {
                val layoutKey = CoreVariablesManager.computeSharedPreferenceKey(CITRA_LAYOUT_KEY, systemId.dbname)
                val current = sharedPreferences.getString(layoutKey, null)
                val swapKey = CoreVariablesManager.computeSharedPreferenceKey(CITRA_SWAP_KEY, systemId.dbname)
                val swap = sharedPreferences.getString(swapKey, null)
                when (current) {
                    CITRA_SINGLE -> if (swap == CITRA_SWAP_BOTTOM) 4f / 3f else 5f / 3f
                    CITRA_DUAL_LANDSCAPE -> 3f
                    CITRA_DUAL_PORTRAIT -> 5f / 6f
                    else -> if (isLandscape) 3f else 5f / 6f
                }
            }
            CoreID.PCSX_REARMED -> {
                val aspectKey = CoreVariablesManager.computeSharedPreferenceKey(PCSX_REARMED_ASPECT_RATIO_KEY, systemId.dbname)
                val current = sharedPreferences.getString(aspectKey, null)
                when (current) {
                    "16:9" -> 16f / 9f
                    else -> 4f / 3f
                }
            }
            CoreID.SNES9X -> {
                val aspectKey = CoreVariablesManager.computeSharedPreferenceKey(SNES9X_ASPECT_RATIO_KEY, systemId.dbname)
                val current = sharedPreferences.getString(aspectKey, null)
                when (current) {
                    "16:9" -> 16f / 9f
                    "8:7" -> 8f / 7f
                    else -> 4f / 3f
                }
            }
            CoreID.MUPEN64_PLUS_NEXT -> {
                val aspectKey = CoreVariablesManager.computeSharedPreferenceKey("mupen64plus-aspect", systemId.dbname)
                val current = sharedPreferences.getString(aspectKey, null)
                when (current) {
                    "16:9", "16:9 adjusted" -> 16f / 9f
                    else -> 4f / 3f
                }
            }
            CoreID.DOLPHIN -> {
                val key = CoreVariablesManager.computeSharedPreferenceKey("dolphin_widescreen", systemId.dbname)
                val isWidescreen = sharedPreferences.getBoolean(key, false) || sharedPreferences.getString(key, null) == "enabled"
                if (isWidescreen) 16f / 9f else 4f / 3f
            }
            CoreID.FLYCAST -> {
                val key = CoreVariablesManager.computeSharedPreferenceKey("flycast_widescreen_hack", systemId.dbname)
                val isWidescreen = sharedPreferences.getBoolean(key, false) || sharedPreferences.getString(key, null) == "enabled"
                if (isWidescreen) 16f / 9f else 4f / 3f
            }
            CoreID.CEMU -> {
                val key = CoreVariablesManager.computeSharedPreferenceKey(CEMU_VIEW_KEY, systemId.dbname)
                val view = sharedPreferences.getString(key, null)
                CemuScreenLayout.aspectRatio(view, isLandscape)
            }
            CoreID.MEDNAFEN_WSWAN -> {
                val key = CoreVariablesManager.computeSharedPreferenceKey("wswan_rotate_display", systemId.dbname)
                val rotation = sharedPreferences.getString(key, null)
                if (rotation == "Portrait") 9f / 14f else 14f / 9f
            }
            else -> when (systemId) {
                SystemID.PSX -> {
                    val aspectKey = CoreVariablesManager.computeSharedPreferenceKey(PCSX_REARMED_ASPECT_RATIO_KEY, systemId.dbname)
                    val current = sharedPreferences.getString(aspectKey, null)
                    when (current) {
                        "16:9" -> 16f / 9f
                        else -> 4f / 3f
                    }
                }
                SystemID.SNES -> {
                    val aspectKey = CoreVariablesManager.computeSharedPreferenceKey(SNES9X_ASPECT_RATIO_KEY, systemId.dbname)
                    val current = sharedPreferences.getString(aspectKey, null)
                    when (current) {
                        "16:9" -> 16f / 9f
                        "8:7" -> 8f / 7f
                        else -> 4f / 3f
                    }
                }
                SystemID.N64 -> {
                    val aspectKey = CoreVariablesManager.computeSharedPreferenceKey("mupen64plus-aspect", systemId.dbname)
                    val current = sharedPreferences.getString(aspectKey, null)
                    when (current) {
                        "16:9", "16:9 adjusted" -> 16f / 9f
                        else -> 4f / 3f
                    }
                }
                SystemID.GAMECUBE, SystemID.WII -> {
                    val key = CoreVariablesManager.computeSharedPreferenceKey("dolphin_widescreen", systemId.dbname)
                    val isWidescreen = sharedPreferences.getBoolean(key, false) || sharedPreferences.getString(key, null) == "enabled"
                    if (isWidescreen) 16f / 9f else 4f / 3f
                }
                SystemID.DREAMCAST -> {
                    val key = CoreVariablesManager.computeSharedPreferenceKey("flycast_widescreen_hack", systemId.dbname)
                    val isWidescreen = sharedPreferences.getBoolean(key, false) || sharedPreferences.getString(key, null) == "enabled"
                    if (isWidescreen) 16f / 9f else 4f / 3f
                }
                SystemID.WS, SystemID.WSC -> {
                    val key = CoreVariablesManager.computeSharedPreferenceKey("wswan_rotate_display", systemId.dbname)
                    val rotation = sharedPreferences.getString(key, null)
                    if (rotation == "Portrait") 9f / 14f else 14f / 9f
                }
                SystemID.GB, SystemID.GBC, SystemID.GG -> 10f / 9f
                SystemID.GBA -> 3f / 2f
                SystemID.PSP, SystemID.WII_U, SystemID.SWITCH -> 16f / 9f
                SystemID.NGP -> 20f / 19f
                SystemID.LYNX -> 160f / 102f
                else -> 4f / 3f
            }
        }
    }

    fun onOrientationChanged(
        orientation: TouchControllerSettingsManager.Orientation,
    ): List<CoreVariable> {
        return when (coreId) {
            CoreID.MELONDS_DS -> onOrientationChangedMelonDsDs(orientation)
            CoreID.CITRA, CoreID.AZAHAR -> onOrientationChangedCitra(orientation)
            CoreID.CEMU -> updateCemuOrientation(orientation)
            else -> emptyList()
        }
    }

    private fun onOrientationChangedCitra(orientation: TouchControllerSettingsManager.Orientation): List<CoreVariable> {
        val key = CoreVariablesManager.computeSharedPreferenceKey(CITRA_LAYOUT_KEY, systemId.dbname)
        val current = sharedPreferences.getString(key, null)
        if (current == CITRA_SINGLE) {
            return emptyList()
        }
        val target = citraDual(orientation)
        if (current == target) {
            return emptyList()
        }
        sharedPreferences.edit { putString(key, target) }
        return listOf(CoreVariable(CITRA_LAYOUT_KEY, target))
    }

    private fun onOrientationChangedMelonDsDs(orientation: TouchControllerSettingsManager.Orientation): List<CoreVariable> {
        val key = CoreVariablesManager.computeSharedPreferenceKey(MELONDSDS_LAYOUT_KEY, systemId.dbname)
        val current = sharedPreferences.getString(key, null)
        if (current == MELONDSDS_SINGLE_TOP || current == MELONDSDS_SINGLE_BOTTOM) {
            return emptyList()
        }
        // Keep a swapped dual layout swapped when rotating (bottom-top <-> right-left).
        val target =
            ScreenLayoutCycle.melonDsDsDual(
                isLandscape = orientation.isLandscape(),
                swapped = ScreenLayoutCycle.isMelonDsDsSwappedDual(current),
            )
        if (current == target) {
            return emptyList()
        }
        sharedPreferences.edit { putString(key, target) }
        return listOf(CoreVariable(MELONDSDS_LAYOUT_KEY, target))
    }

    private fun citraDual(orientation: TouchControllerSettingsManager.Orientation): String {
        return if (orientation == TouchControllerSettingsManager.Orientation.LANDSCAPE) {
            CITRA_DUAL_LANDSCAPE
        } else {
            CITRA_DUAL_PORTRAIT
        }
    }

    /** True while a DS/3DS core shows only one screen (top or bottom). */
    fun isSingleScreen(): Boolean {
        return when (coreId) {
            CoreID.MELONDS_DS -> {
                val key = CoreVariablesManager.computeSharedPreferenceKey(MELONDSDS_LAYOUT_KEY, systemId.dbname)
                val current = sharedPreferences.getString(key, null)
                current == MELONDSDS_SINGLE_TOP || current == MELONDSDS_SINGLE_BOTTOM
            }
            CoreID.CITRA, CoreID.AZAHAR -> {
                val key = CoreVariablesManager.computeSharedPreferenceKey(CITRA_LAYOUT_KEY, systemId.dbname)
                sharedPreferences.getString(key, null) == CITRA_SINGLE
            }
            else -> false
        }
    }

    fun getTouchScreenRegion(orientation: TouchControllerSettingsManager.Orientation): TouchScreenRegion? {
        if (coreId != CoreID.CEMU) return TouchScreenRegion.FULL
        val key = CoreVariablesManager.computeSharedPreferenceKey(CEMU_VIEW_KEY, systemId.dbname)
        return CemuScreenLayout.touchRegion(
            sharedPreferences.getString(key, "tv"),
            orientation == TouchControllerSettingsManager.Orientation.LANDSCAPE,
        )
    }

    private fun updateCemuOrientation(orientation: TouchControllerSettingsManager.Orientation): List<CoreVariable> {
        val layout = CemuScreenLayout.nativeLayout(orientation == TouchControllerSettingsManager.Orientation.LANDSCAPE)
        val key = CoreVariablesManager.computeSharedPreferenceKey(CEMU_LAYOUT_KEY, systemId.dbname)
        sharedPreferences.edit { putString(key, layout) }
        return listOf(CoreVariable(CEMU_LAYOUT_KEY, layout))
    }

    private fun toggleCemu(orientation: TouchControllerSettingsManager.Orientation): List<CoreVariable> {
        val key = CoreVariablesManager.computeSharedPreferenceKey(CEMU_VIEW_KEY, systemId.dbname)
        val current = sharedPreferences.getString(key, "tv")
        val next = CemuScreenLayout.nextView(current)
        sharedPreferences.edit { putString(key, next) }
        return updateCemuOrientation(orientation) + CoreVariable(CEMU_VIEW_KEY, next)
    }

    companion object {
        const val CEMU_VIEW_KEY = "cemu_screen_view"
        const val CEMU_LAYOUT_KEY = "cemu_dual_screen_layout"
        const val MELONDSDS_LAYOUT_KEY = "melonds_screen_layout1"
        const val MELONDSDS_DUAL_PORTRAIT = "top-bottom"
        const val MELONDSDS_DUAL_LANDSCAPE = "left-right"
        const val MELONDSDS_DUAL_PORTRAIT_SWAPPED = "bottom-top"
        const val MELONDSDS_DUAL_LANDSCAPE_SWAPPED = "right-left"
        const val MELONDSDS_SINGLE_TOP = "top"
        const val MELONDSDS_SINGLE_BOTTOM = "bottom"

        const val CITRA_LAYOUT_KEY = "citra_layout_option"
        const val CITRA_SWAP_KEY = "citra_swap_screen"
        const val CITRA_DUAL_PORTRAIT = "Default Top-Bottom Screen"
        const val CITRA_DUAL_LANDSCAPE = "Side by Side"
        const val CITRA_SINGLE = "Single Screen Only"
        const val CITRA_SWAP_TOP = "Top"
        const val CITRA_SWAP_BOTTOM = "Bottom"

        const val PCSX_REARMED_ASPECT_RATIO_KEY = "pcsx_rearmed_aspect_ratio"
        const val SNES9X_ASPECT_RATIO_KEY = "snes9x_aspect_ratio"
    }
}
