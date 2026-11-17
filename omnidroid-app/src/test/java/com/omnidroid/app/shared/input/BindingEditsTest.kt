package com.omnidroid.app.shared.input

import com.omnidroid.app.shared.input.BindingEdits.ConflictResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BindingEditsTest {
    // Physical keys (arbitrary codes) and RetroPad buttons.
    private val k1 = InputKey(1)
    private val k2 = InputKey(2)
    private val k3 = InputKey(3)
    private val a = RetroKey(100)
    private val b = RetroKey(101)

    private val bindings = mapOf(k1 to a, k2 to b)

    @Test
    fun noConflictWhenKeyIsFreeOrAlreadyTheTarget() {
        assertNull(BindingEdits.conflictingButton(bindings, a, k3))
        assertNull(BindingEdits.conflictingButton(bindings, a, k1))
    }

    @Test
    fun conflictReportsTheOtherButton() {
        assertEquals(b, BindingEdits.conflictingButton(bindings, a, k2))
    }

    @Test
    fun assignFreeKeyMovesTheTarget() {
        assertEquals(mapOf(k3 to a, k2 to b), BindingEdits.assign(bindings, a, k3))
    }

    @Test
    fun replaceLeavesTheOtherButtonUnbound() {
        assertEquals(mapOf(k2 to a), BindingEdits.assign(bindings, a, k2, ConflictResolution.REPLACE))
    }

    @Test
    fun swapGivesTheOtherButtonTheTargetsOldKey() {
        assertEquals(mapOf(k2 to a, k1 to b), BindingEdits.assign(bindings, a, k2, ConflictResolution.SWAP))
    }

    @Test
    fun swapWithoutAnOldKeyActsLikeReplace() {
        val unboundTarget = mapOf(k2 to b)
        assertEquals(mapOf(k2 to a), BindingEdits.assign(unboundTarget, a, k2, ConflictResolution.SWAP))
    }

    @Test
    fun clearRemovesOnlyTheTarget() {
        assertEquals(mapOf(k2 to b), BindingEdits.clear(bindings, a))
    }
}
