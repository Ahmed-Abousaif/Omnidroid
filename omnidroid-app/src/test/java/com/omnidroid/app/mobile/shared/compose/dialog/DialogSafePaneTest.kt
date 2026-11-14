package com.omnidroid.app.mobile.shared.compose.dialog

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Test

class DialogSafePaneTest {
    @Test fun ordinaryAndOffscreenWindowsKeepAllAvailableSpace() {
        assertEquals(DialogPaneInsets(), dialogPaneInsets(400, 800, null, true))
        assertEquals(DialogPaneInsets(), dialogPaneInsets(400, 800, IntRect(600, 0, 620, 800), true))
        assertEquals(DialogPaneInsets(), dialogPaneInsets(400, 800, IntRect(200, 800, 220, 900), true))
    }

    @Test fun verticalHingeChoosesLargerPaneWithoutCrossingHinge() {
        assertEquals(DialogPaneInsets(left = 420), dialogPaneInsets(1000, 800, IntRect(400, 0, 420, 800), true))
        assertEquals(DialogPaneInsets(right = 400), dialogPaneInsets(1000, 800, IntRect(600, 0, 620, 800), true))
    }

    @Test fun horizontalHingeChoosesLargerPaneWithoutCrossingHinge() {
        assertEquals(DialogPaneInsets(top = 320), dialogPaneInsets(800, 1000, IntRect(0, 300, 800, 320), false))
        assertEquals(DialogPaneInsets(bottom = 300), dialogPaneInsets(800, 1000, IntRect(0, 700, 800, 720), false))
    }

    @Test fun separatingFoldWithZeroThicknessStillKeepsPanelOnOneSide() {
        assertEquals(DialogPaneInsets(right = 500), dialogPaneInsets(1000, 800, IntRect(500, 0, 500, 800), true))
        assertEquals(DialogPaneInsets(bottom = 400), dialogPaneInsets(1000, 800, IntRect(0, 400, 1000, 400), false))
    }
}
