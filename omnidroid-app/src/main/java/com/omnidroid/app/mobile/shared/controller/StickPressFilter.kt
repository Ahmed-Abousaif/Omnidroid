package com.omnidroid.app.mobile.shared.controller

import kotlin.math.abs
import kotlin.math.sign

/**
 * Turns left-stick movement into single D-pad presses for UI navigation. Purely event-driven: it
 * only runs when a stick report arrives, with no timers, and allocates nothing unless it presses.
 *
 * - One tilt is one press: it presses at [pressThreshold] and lets go only below
 *   [releaseThreshold], so a stick hovering near the edge doesn't press again. Holding does not
 *   repeat; the D-pad's own key repeat is there for long lists.
 * - Spring-back: letting go, the spring overshoots past the centre, on a worn stick by about 70%,
 *   past [pressThreshold]. So an opposite tilt within [reboundMs] of letting go counts only if it
 *   goes deep ([reverseDeepLean]); a deliberate reverse does, or is still out on the first report
 *   after [reboundMs], while the overshoot has died out by then.
 *
 * Pure (time is passed in) so it can be unit tested.
 */
class StickPressFilter(
    private val pressThreshold: Float = 0.65f,
    private val releaseThreshold: Float = 0.3f,
    private val reboundMs: Long = 150L,
    private val reverseDeepLean: Float = 0.85f,
) {
    /** A press in one direction: x or y is -1 or 1, the other 0. */
    data class Press(
        val x: Int,
        val y: Int,
    )

    private var heldX = 0
    private var heldY = 0
    private var releasedX = 0
    private var releasedY = 0
    private var releasedAt = Long.MIN_VALUE / 2

    /** The stick is held in a direction (past the press threshold, not yet released). */
    val engaged: Boolean get() = heldX != 0 || heldY != 0

    fun reset() {
        heldX = 0
        heldY = 0
    }

    /** Feeds the stick position; returns a press to dispatch, or null. */
    fun update(
        x: Float,
        y: Float,
        now: Long,
    ): Press? {
        if (engaged) {
            // How far the stick still leans the way it was pressed.
            val lean = if (heldX != 0) x * heldX else y * heldY
            if (lean >= releaseThreshold) return null
            releasedX = heldX
            releasedY = heldY
            releasedAt = now
            heldX = 0
            heldY = 0
            // Fall through: one report can already be past the centre (a fast flick back, or events
            // batched per frame), and a stick then held still sends nothing more.
        }
        val ax = abs(x)
        val ay = abs(y)
        val depth = maxOf(ax, ay)
        if (depth < pressThreshold) return null
        // One direction only: the stronger axis, so a diagonal doesn't press twice.
        val dx = if (ax >= ay) sign(x).toInt() else 0
        val dy = if (ax >= ay) 0 else sign(y).toInt()
        val reverse = dx == -releasedX && dy == -releasedY
        if (reverse && now - releasedAt < reboundMs && depth < reverseDeepLean) return null
        heldX = dx
        heldY = dy
        return Press(dx, dy)
    }
}
