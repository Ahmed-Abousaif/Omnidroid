package com.omnidroid.app.mobile.feature.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import gg.padkit.UnhandledPointer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StylusPointerRouterTest {
    private val router = StylusPointerRouter(Rect(100f, 0f, 900f, 600f))
    private fun pointer(id: Long, x: Float, y: Float) = UnhandledPointer(id, Offset(x, y), isNew = true)

    @Test
    fun sideOfScreenAbovePadsRemainsTouchable() {
        val position = Offset(110f, 10f)
        assertEquals(listOf(StylusPointerRouter.Event(StylusPointerRouter.Action.DOWN, position)), router.update(listOf(pointer(1, 110f, 10f))))
        assertEquals(StylusPointerRouter.Action.UP, router.update(emptyList()).single().action)
    }

    @Test
    fun fingerStartingInBlackMarginDoesNotTurnIntoStylus() {
        assertTrue(router.update(listOf(pointer(1, 10f, 20f))).isEmpty())
        assertTrue(router.update(listOf(pointer(1, 200f, 20f))).isEmpty())
    }

    @Test
    fun secondFingerDoesNotDisplaceStylusOrBecomePenWhenFirstIsReleased() {
        router.update(listOf(pointer(1, 200f, 20f)))
        assertTrue(router.update(listOf(pointer(1, 200f, 20f), pointer(2, 300f, 20f))).isEmpty())
        assertEquals(StylusPointerRouter.Action.UP, router.update(listOf(pointer(2, 300f, 20f))).single().action)
        assertTrue(router.update(listOf(pointer(2, 310f, 20f))).isEmpty())
        router.update(emptyList())
        assertEquals(StylusPointerRouter.Action.DOWN, router.update(listOf(pointer(2, 310f, 20f))).single().action)
    }

    @Test
    fun capturedStylusContinuesOutsideImageAndCancellationReleasesOnce() {
        router.update(listOf(pointer(1, 200f, 20f)))
        assertEquals(StylusPointerRouter.Action.MOVE, router.update(listOf(pointer(1, 0f, 20f))).single().action)
        assertEquals(StylusPointerRouter.Event(StylusPointerRouter.Action.UP, Offset(0f, 20f)), router.cancel())
        assertNull(router.cancel())
    }

    @Test
    fun layoutReplacementDoesNotTreatAHeldFingerAsANewPenDown() {
        assertTrue(router.update(listOf(UnhandledPointer(1, Offset(200f, 20f), isNew = false))).isEmpty())
    }
}
