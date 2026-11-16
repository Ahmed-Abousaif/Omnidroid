package com.omnidroid.app.shared.game.viewmodel

import com.omnidroid.app.shared.game.viewmodel.TouchScreenLayoutController.Companion.CITRA_DUAL_LANDSCAPE
import com.omnidroid.app.shared.game.viewmodel.TouchScreenLayoutController.Companion.CITRA_DUAL_PORTRAIT
import com.omnidroid.app.shared.game.viewmodel.TouchScreenLayoutController.Companion.CITRA_SINGLE
import com.omnidroid.app.shared.game.viewmodel.TouchScreenLayoutController.Companion.CITRA_SWAP_BOTTOM
import com.omnidroid.app.shared.game.viewmodel.TouchScreenLayoutController.Companion.CITRA_SWAP_TOP
import com.omnidroid.app.shared.game.viewmodel.TouchScreenLayoutController.Companion.MELONDSDS_DUAL_LANDSCAPE
import com.omnidroid.app.shared.game.viewmodel.TouchScreenLayoutController.Companion.MELONDSDS_DUAL_LANDSCAPE_SWAPPED
import com.omnidroid.app.shared.game.viewmodel.TouchScreenLayoutController.Companion.MELONDSDS_DUAL_PORTRAIT
import com.omnidroid.app.shared.game.viewmodel.TouchScreenLayoutController.Companion.MELONDSDS_DUAL_PORTRAIT_SWAPPED
import com.omnidroid.app.shared.game.viewmodel.TouchScreenLayoutController.Companion.MELONDSDS_SINGLE_BOTTOM
import com.omnidroid.app.shared.game.viewmodel.TouchScreenLayoutController.Companion.MELONDSDS_SINGLE_TOP

/**
 * Pure next-state logic for the DS/3DS screen-layout cycle:
 * dual -> dual swapped (bottom screen first) -> top only -> bottom only -> dual.
 * Any other value (e.g. a hybrid layout picked in core options) counts as dual.
 */
internal object ScreenLayoutCycle {
    fun isMelonDsDsSwappedDual(layout: String?): Boolean =
        layout == MELONDSDS_DUAL_PORTRAIT_SWAPPED || layout == MELONDSDS_DUAL_LANDSCAPE_SWAPPED

    fun melonDsDsDual(isLandscape: Boolean, swapped: Boolean): String =
        when {
            isLandscape && swapped -> MELONDSDS_DUAL_LANDSCAPE_SWAPPED
            isLandscape -> MELONDSDS_DUAL_LANDSCAPE
            swapped -> MELONDSDS_DUAL_PORTRAIT_SWAPPED
            else -> MELONDSDS_DUAL_PORTRAIT
        }

    fun nextMelonDsDs(current: String?, isLandscape: Boolean): String =
        when {
            isMelonDsDsSwappedDual(current) -> MELONDSDS_SINGLE_TOP
            current == MELONDSDS_SINGLE_TOP -> MELONDSDS_SINGLE_BOTTOM
            current == MELONDSDS_SINGLE_BOTTOM -> melonDsDsDual(isLandscape, swapped = false)
            else -> melonDsDsDual(isLandscape, swapped = true)
        }

    /** Returns the next (layout, swap) pair; swap picks which screen is primary. */
    fun nextCitra(layout: String?, swap: String?, isLandscape: Boolean): Pair<String, String> {
        val dual = if (isLandscape) CITRA_DUAL_LANDSCAPE else CITRA_DUAL_PORTRAIT
        val swapped = swap == CITRA_SWAP_BOTTOM
        return when {
            layout == CITRA_SINGLE && swapped -> dual to CITRA_SWAP_TOP
            layout == CITRA_SINGLE -> CITRA_SINGLE to CITRA_SWAP_BOTTOM
            swapped -> CITRA_SINGLE to CITRA_SWAP_TOP
            else -> dual to CITRA_SWAP_BOTTOM
        }
    }
}
