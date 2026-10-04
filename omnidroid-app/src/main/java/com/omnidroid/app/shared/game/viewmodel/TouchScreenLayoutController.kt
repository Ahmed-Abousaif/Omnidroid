package com.omnidroid.app.shared.game.viewmodel

import android.content.SharedPreferences
import androidx.core.content.edit
import com.omnidroid.lib.core.CoreVariable
import com.omnidroid.lib.core.CoreVariablesManager
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.SystemID
import com.omnidroid.touchinput.radial.settings.TouchControllerSettingsManager

/**
 * Toggles DS/3DS screen layout using the same SharedPreferences keys as the in-game Settings list.
 */
class TouchScreenLayoutController(
    private val sharedPreferences: SharedPreferences,
    private val systemId: SystemID,
    private val coreId: CoreID,
) {
    fun toggle(
        orientation: TouchControllerSettingsManager.Orientation,
    ): List<CoreVariable> {
        return when (coreId) {
            CoreID.MELONDS_DS -> toggleMelonDsDs(orientation)
            CoreID.CITRA, CoreID.AZAHAR -> toggleCitra(orientation)
            else -> emptyList()
        }
    }

    fun supportsToggle(): Boolean =
        coreId == CoreID.MELONDS_DS || coreId == CoreID.CITRA || coreId == CoreID.AZAHAR

    fun getAspectRatio(orientation: TouchControllerSettingsManager.Orientation): Float {
        val isLandscape = orientation == TouchControllerSettingsManager.Orientation.LANDSCAPE
        return when (coreId) {
            CoreID.MELONDS_DS -> {
                val key = CoreVariablesManager.computeSharedPreferenceKey(MELONDSDS_LAYOUT_KEY, systemId.dbname)
                val current = sharedPreferences.getString(key, null)
                when {
                    current == MELONDSDS_SINGLE_TOP -> 4f / 3f
                    isLandscape -> 8f / 3f
                    else -> 4f / 6f
                }
            }
            CoreID.CITRA, CoreID.AZAHAR -> {
                val layoutKey = CoreVariablesManager.computeSharedPreferenceKey(CITRA_LAYOUT_KEY, systemId.dbname)
                val current = sharedPreferences.getString(layoutKey, null)
                when {
                    current == CITRA_SINGLE -> 5f / 3f
                    isLandscape -> 2.5f
                    else -> 5f / 6f
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
                val key = CoreVariablesManager.computeSharedPreferenceKey("cemu_screen_view", systemId.dbname)
                val view = sharedPreferences.getString(key, null)
                if (view == "Side by Side") 32f / 9f else 16f / 9f
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
                SystemID.PSP, SystemID.WII_U -> 16f / 9f
                SystemID.NGP -> 20f / 19f
                SystemID.LYNX -> 160f / 102f
                else -> 4f / 3f
            }
        }
    }

    private fun toggleMelonDsDs(orientation: TouchControllerSettingsManager.Orientation): List<CoreVariable> {
        val key = CoreVariablesManager.computeSharedPreferenceKey(MELONDSDS_LAYOUT_KEY, systemId.dbname)
        val current = sharedPreferences.getString(key, null)
        val next =
            if (current == MELONDSDS_SINGLE_TOP) {
                melonDsDsDual(orientation)
            } else {
                MELONDSDS_SINGLE_TOP
            }
        sharedPreferences.edit { putString(key, next) }
        return listOf(CoreVariable(MELONDSDS_LAYOUT_KEY, next))
    }

    private fun toggleCitra(orientation: TouchControllerSettingsManager.Orientation): List<CoreVariable> {
        val layoutKey = CoreVariablesManager.computeSharedPreferenceKey(CITRA_LAYOUT_KEY, systemId.dbname)
        val swapKey = CoreVariablesManager.computeSharedPreferenceKey(CITRA_SWAP_KEY, systemId.dbname)
        val current = sharedPreferences.getString(layoutKey, null)
        return if (current == CITRA_SINGLE) {
            val dual = citraDual(orientation)
            sharedPreferences.edit { putString(layoutKey, dual) }
            listOf(CoreVariable(CITRA_LAYOUT_KEY, dual))
        } else {
            sharedPreferences.edit {
                putString(layoutKey, CITRA_SINGLE)
                putString(swapKey, CITRA_SWAP_TOP)
            }
            listOf(
                CoreVariable(CITRA_LAYOUT_KEY, CITRA_SINGLE),
                CoreVariable(CITRA_SWAP_KEY, CITRA_SWAP_TOP),
            )
        }
    }

    fun onOrientationChanged(
        orientation: TouchControllerSettingsManager.Orientation,
    ): List<CoreVariable> {
        return when (coreId) {
            CoreID.MELONDS_DS -> onOrientationChangedMelonDsDs(orientation)
            else -> emptyList()
        }
    }

    private fun onOrientationChangedMelonDsDs(orientation: TouchControllerSettingsManager.Orientation): List<CoreVariable> {
        val key = CoreVariablesManager.computeSharedPreferenceKey(MELONDSDS_LAYOUT_KEY, systemId.dbname)
        val current = sharedPreferences.getString(key, null)
        if (current == MELONDSDS_SINGLE_TOP) {
            return emptyList()
        }
        val target = melonDsDsDual(orientation)
        if (current == target) {
            return emptyList()
        }
        sharedPreferences.edit { putString(key, target) }
        return listOf(CoreVariable(MELONDSDS_LAYOUT_KEY, target))
    }

    private fun melonDsDsDual(orientation: TouchControllerSettingsManager.Orientation): String {
        return if (orientation == TouchControllerSettingsManager.Orientation.LANDSCAPE) {
            MELONDSDS_DUAL_LANDSCAPE
        } else {
            MELONDSDS_DUAL_PORTRAIT
        }
    }

    private fun citraDual(orientation: TouchControllerSettingsManager.Orientation): String {
        return if (orientation == TouchControllerSettingsManager.Orientation.LANDSCAPE) {
            CITRA_DUAL_LANDSCAPE
        } else {
            CITRA_DUAL_PORTRAIT
        }
    }

    companion object {
        const val MELONDSDS_LAYOUT_KEY = "melonds_screen_layout1"
        const val MELONDSDS_DUAL_PORTRAIT = "top-bottom"
        const val MELONDSDS_DUAL_LANDSCAPE = "left-right"
        const val MELONDSDS_SINGLE_TOP = "top"

        const val CITRA_LAYOUT_KEY = "citra_layout_option"
        const val CITRA_SWAP_KEY = "citra_swap_screen"
        const val CITRA_DUAL_PORTRAIT = "Default Top-Bottom Screen"
        const val CITRA_DUAL_LANDSCAPE = "Side by Side"
        const val CITRA_SINGLE = "Single Screen Only"
        const val CITRA_SWAP_TOP = "Top"

        const val PCSX_REARMED_ASPECT_RATIO_KEY = "pcsx_rearmed_aspect_ratio"
        const val SNES9X_ASPECT_RATIO_KEY = "snes9x_aspect_ratio"
    }
}
