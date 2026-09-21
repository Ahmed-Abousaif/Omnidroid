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
            CoreID.MELONDS -> toggleMelonDs(orientation)
            CoreID.MELONDS_DS -> toggleMelonDsDs(orientation)
            CoreID.CITRA, CoreID.AZAHAR -> toggleCitra(orientation)
            else -> emptyList()
        }
    }

    fun supportsToggle(): Boolean =
        coreId == CoreID.MELONDS || coreId == CoreID.MELONDS_DS || coreId == CoreID.CITRA || coreId == CoreID.AZAHAR

    private fun toggleMelonDs(orientation: TouchControllerSettingsManager.Orientation): List<CoreVariable> {
        val key = CoreVariablesManager.computeSharedPreferenceKey(MELONDS_LAYOUT_KEY, systemId.dbname)
        val current = sharedPreferences.getString(key, null)
        val next =
            if (current == MELONDS_SINGLE_TOP) {
                melonDsDual(orientation)
            } else {
                MELONDS_SINGLE_TOP
            }
        sharedPreferences.edit { putString(key, next) }
        return listOf(CoreVariable(MELONDS_LAYOUT_KEY, next))
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
            CoreID.MELONDS -> onOrientationChangedMelonDs(orientation)
            CoreID.MELONDS_DS -> onOrientationChangedMelonDsDs(orientation)
            CoreID.CITRA, CoreID.AZAHAR -> onOrientationChangedCitra(orientation)
            else -> emptyList()
        }
    }

    private fun onOrientationChangedCitra(orientation: TouchControllerSettingsManager.Orientation): List<CoreVariable> {
        val layoutKey = CoreVariablesManager.computeSharedPreferenceKey(CITRA_LAYOUT_KEY, systemId.dbname)
        val current = sharedPreferences.getString(layoutKey, null)
        if (current == CITRA_SINGLE) {
            return emptyList()
        }
        val target = citraDual(orientation)
        if (current == target) {
            return emptyList()
        }
        sharedPreferences.edit { putString(layoutKey, target) }
        return listOf(CoreVariable(CITRA_LAYOUT_KEY, target))
    }

    private fun onOrientationChangedMelonDs(orientation: TouchControllerSettingsManager.Orientation): List<CoreVariable> {
        val key = CoreVariablesManager.computeSharedPreferenceKey(MELONDS_LAYOUT_KEY, systemId.dbname)
        val current = sharedPreferences.getString(key, null)
        if (current == MELONDS_SINGLE_TOP) {
            return emptyList()
        }
        val target = melonDsDual(orientation)
        if (current == target) {
            return emptyList()
        }
        sharedPreferences.edit { putString(key, target) }
        return listOf(CoreVariable(MELONDS_LAYOUT_KEY, target))
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

    private fun melonDsDual(orientation: TouchControllerSettingsManager.Orientation): String {
        return if (orientation == TouchControllerSettingsManager.Orientation.LANDSCAPE) {
            MELONDS_DUAL_LANDSCAPE
        } else {
            MELONDS_DUAL_PORTRAIT
        }
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
        const val MELONDS_LAYOUT_KEY = "melonds_screen_layout"
        const val MELONDS_DUAL_PORTRAIT = "Top/Bottom"
        const val MELONDS_DUAL_LANDSCAPE = "Left/Right"
        const val MELONDS_SINGLE_TOP = "Top Only"

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
    }
}
