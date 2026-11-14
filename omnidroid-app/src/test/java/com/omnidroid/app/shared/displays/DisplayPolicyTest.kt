package com.omnidroid.app.shared.displays

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayPolicyTest {
    @Test fun singleScreenDoesNotNeedACoordinatorRegardlessOfItsId() {
        assertFalse(DisplayPolicy.needsCoordinator(setOf(0), setOf(0)))
        assertFalse(DisplayPolicy.needsCoordinator(setOf(7), setOf(7)))
        assertFalse(DisplayPolicy.needsCoordinator(emptySet(), setOf(0)))
    }

    @Test fun anotherAvailableDisplayActivatesTheCoordinator() {
        assertTrue(DisplayPolicy.needsCoordinator(setOf(0, 7), setOf(0)))
        assertTrue(DisplayPolicy.needsCoordinator(setOf(0, 7), setOf(7)))
    }

    @Test fun backgroundOrFullyOccupiedDisplaysDoNotNeedACoordinator() {
        assertFalse(DisplayPolicy.needsCoordinator(setOf(0, 7), emptySet()))
        assertFalse(DisplayPolicy.needsCoordinator(setOf(0, 7), setOf(0, 7)))
    }

    private fun display(
        id: Int = 1,
        presentation: Boolean = true,
        activity: Boolean = false,
    ) = DisplayFacts(
        id, "Screen $id", 1080, 1240, 320, 0, 0, true, presentation, false, activity,
    )

    private fun evaluate(
        facts: DisplayFacts = display(),
        hosts: Set<Int> = setOf(0),
        foreground: Boolean = true,
        automatic: Boolean = true,
        hidden: Boolean = false,
        showing: Boolean = false,
        failed: Boolean = false,
    ) = DisplayPolicy.evaluate(facts, hosts, foreground, automatic, hidden, showing, failed)

    @Test fun currentAppScreenIsExcludedEvenWhenItIsNotTheDefaultDisplay() {
        assertNull(evaluate(display(7), hosts = setOf(7)))
        assertNull(evaluate(display(0), hosts = setOf(0)))
        assertNull(evaluate(display(7), hosts = setOf(0, 7)))
        assertEquals(DisplayOutput.ACTIVITY, evaluate(display(0, false, true), hosts = setOf(7))?.output)
    }

    @Test fun presentationAndActivityDisplaysCanCoexist() {
        val displays = listOf(display(0), display(1), display(2, false, true))
        val entries = displays.mapNotNull { evaluate(it) }
        assertEquals(listOf(1, 2), entries.map { it.facts.id })
        assertEquals(listOf(DisplayOutput.PRESENTATION, DisplayOutput.ACTIVITY), entries.map { it.output })
    }

    @Test fun presentationSupportDoesNotImplyActivityOrTouchSupport() {
        val entry = checkNotNull(evaluate())
        assertEquals(DisplayOutput.PRESENTATION, entry.output)
        assertFalse(entry.facts.activityAllowed)
        assertEquals(DisplayStatus.READY, entry.status)
        assertFalse(DisplaysState(true, true, listOf(entry)).hasOutput)
        assertTrue(DisplaysState(true, true, listOf(entry.copy(status = DisplayStatus.SHOWING))).hasOutput)
    }

    @Test fun privateFlagDoesNotReplaceThePlatformsOwnershipAndAccessChecks() {
        assertEquals(DisplayOutput.PRESENTATION, evaluate(display().copy(privateDisplay = true))?.output)
        assertEquals(DisplayStatus.UNAVAILABLE, evaluate(display().copy(privateDisplay = true), failed = true)?.status)
        assertNull(evaluate(display(presentation = false).copy(privateDisplay = true))?.output)
        assertEquals(DisplayStatus.UNAVAILABLE, evaluate(display(presentation = false))?.status)
    }

    @Test fun sleepingDisabledHiddenAndBackgroundStatesSuppressOutput() {
        assertEquals(DisplayStatus.SLEEPING, evaluate(display().copy(awake = false), showing = true)?.status)
        assertEquals(DisplayStatus.HIDDEN, evaluate(automatic = false, showing = true)?.status)
        assertEquals(DisplayStatus.HIDDEN, evaluate(hidden = true, showing = true)?.status)
        assertEquals(DisplayStatus.PAUSED, evaluate(foreground = false, showing = true)?.status)
    }

    @Test fun rejectedOutputIsNotReportedAsActiveOrAutomaticallyRetried() {
        assertEquals(DisplayStatus.UNAVAILABLE, evaluate(showing = true, failed = true)?.status)
        assertFalse(DisplaysState(true, true, listOf(checkNotNull(evaluate(failed = true)))).hasOutput)
        assertEquals(DisplayStatus.READY, evaluate(failed = false)?.status)
    }

    @Test fun wakingAndMovingTheAppRecomputeEligibilityFromCurrentFacts() {
        val sleeping = display().copy(awake = false)
        assertEquals(DisplayStatus.SLEEPING, evaluate(sleeping)?.status)
        assertEquals(DisplayStatus.READY, evaluate(sleeping.copy(awake = true))?.status)
        assertNull(evaluate(sleeping.copy(awake = true), hosts = setOf(1)))
        assertEquals(DisplayStatus.READY, evaluate(display(0), hosts = setOf(1))?.status)
    }
}
