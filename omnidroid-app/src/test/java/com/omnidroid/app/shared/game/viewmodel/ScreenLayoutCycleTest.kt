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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenLayoutCycleTest {
    private fun melonCycle(start: String?, isLandscape: Boolean, steps: Int = 5): List<String> {
        var layout = start
        return List(steps) {
            ScreenLayoutCycle.nextMelonDsDs(layout, isLandscape).also { layout = it }
        }
    }

    private fun citraCycle(start: Pair<String?, String?>, isLandscape: Boolean, steps: Int = 5): List<Pair<String, String>> {
        var state = start
        return List(steps) {
            ScreenLayoutCycle.nextCitra(state.first, state.second, isLandscape).also { state = it }
        }
    }

    @Test
    fun melonDsCyclesFourStatesInLandscape() {
        assertEquals(
            listOf(
                MELONDSDS_DUAL_LANDSCAPE_SWAPPED,
                MELONDSDS_SINGLE_TOP,
                MELONDSDS_SINGLE_BOTTOM,
                MELONDSDS_DUAL_LANDSCAPE,
                MELONDSDS_DUAL_LANDSCAPE_SWAPPED,
            ),
            melonCycle(MELONDSDS_DUAL_LANDSCAPE, isLandscape = true),
        )
    }

    @Test
    fun melonDsCyclesFourStatesInPortrait() {
        assertEquals(
            listOf(
                MELONDSDS_DUAL_PORTRAIT_SWAPPED,
                MELONDSDS_SINGLE_TOP,
                MELONDSDS_SINGLE_BOTTOM,
                MELONDSDS_DUAL_PORTRAIT,
                MELONDSDS_DUAL_PORTRAIT_SWAPPED,
            ),
            melonCycle(MELONDSDS_DUAL_PORTRAIT, isLandscape = false),
        )
    }

    @Test
    fun melonDsTreatsUnsetAndOtherLayoutsAsDual() {
        assertEquals(MELONDSDS_DUAL_LANDSCAPE_SWAPPED, ScreenLayoutCycle.nextMelonDsDs(null, isLandscape = true))
        assertEquals(MELONDSDS_DUAL_PORTRAIT_SWAPPED, ScreenLayoutCycle.nextMelonDsDs("hybrid-top", isLandscape = false))
    }

    @Test
    fun melonDsSwappedDualFollowsOrientation() {
        assertTrue(ScreenLayoutCycle.isMelonDsDsSwappedDual(MELONDSDS_DUAL_PORTRAIT_SWAPPED))
        assertTrue(ScreenLayoutCycle.isMelonDsDsSwappedDual(MELONDSDS_DUAL_LANDSCAPE_SWAPPED))
        assertFalse(ScreenLayoutCycle.isMelonDsDsSwappedDual(MELONDSDS_DUAL_LANDSCAPE))
        assertEquals(MELONDSDS_DUAL_LANDSCAPE_SWAPPED, ScreenLayoutCycle.melonDsDsDual(isLandscape = true, swapped = true))
        assertEquals(MELONDSDS_DUAL_PORTRAIT_SWAPPED, ScreenLayoutCycle.melonDsDsDual(isLandscape = false, swapped = true))
    }

    @Test
    fun citraCyclesFourStatesInLandscape() {
        assertEquals(
            listOf(
                CITRA_DUAL_LANDSCAPE to CITRA_SWAP_BOTTOM,
                CITRA_SINGLE to CITRA_SWAP_TOP,
                CITRA_SINGLE to CITRA_SWAP_BOTTOM,
                CITRA_DUAL_LANDSCAPE to CITRA_SWAP_TOP,
                CITRA_DUAL_LANDSCAPE to CITRA_SWAP_BOTTOM,
            ),
            citraCycle(CITRA_DUAL_LANDSCAPE to CITRA_SWAP_TOP, isLandscape = true),
        )
    }

    @Test
    fun citraCyclesFourStatesInPortraitFromUnsetPrefs() {
        assertEquals(
            listOf(
                CITRA_DUAL_PORTRAIT to CITRA_SWAP_BOTTOM,
                CITRA_SINGLE to CITRA_SWAP_TOP,
                CITRA_SINGLE to CITRA_SWAP_BOTTOM,
                CITRA_DUAL_PORTRAIT to CITRA_SWAP_TOP,
                CITRA_DUAL_PORTRAIT to CITRA_SWAP_BOTTOM,
            ),
            citraCycle(null to null, isLandscape = false),
        )
    }
}
