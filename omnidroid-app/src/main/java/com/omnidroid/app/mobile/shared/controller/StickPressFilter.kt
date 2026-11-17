package com.omnidroid.app.mobile.shared.controller

import kotlin.math.abs
import kotlin.math.sign

/**
 * Turns left-stick movement into single D-pad presses for UI navigation.
 *
 * - One tilt is one press: it presses at [pressThreshold] and only lets go below [releaseThreshold],
 *   so a stick hovering near the edge doesn't press again and again.
 * - Telling the spring back from a deliberate reverse: letting go is as fast as a flick, so speed
 *   alone can't. What differs is what follows. A spring overshoot is shallow and comes straight back;
 *   a thumb reversing keeps going and stays out. So an opposite tilt within [reboundMs] of letting go
 *   waits: it presses at once if it goes deep ([reverseDeepLean]), presses if it stays out for
 *   [reverseConfirmMs] (see [tick]), and is dropped as a rebound if it falls back first.
 * - Holding the stick repeats after [holdDelayMs], every [holdRepeatMs]. A stick held still sends
 *   no events, so the caller drives this with [tick] at [nextTickIn].
 *
 * Pure (time is passed in) so it can be unit tested.
 */
class StickPressFilter(
    private val pressThreshold: Float = 0.6f,
    private val releaseThreshold: Float = 0.3f,
    private val reboundMs: Long = 150L,
    private val reverseDeepLean: Float = 0.85f,
    val reverseConfirmMs: Long = 40L,
    private val holdDelayMs: Long = 400L,
    private val holdRepeatMs: Long = 110L,
) {
    /** A press in one direction: x or y is -1 or 1, the other 0. */
    data class Press(
        val x: Int,
        val y: Int,
    )

    private var heldX = 0
    private var heldY = 0
    private var pressedAt = 0L
    private var lastRepeatAt = 0L
    private var releasedX = 0
    private var releasedY = 0
    private var releasedAt = Long.MIN_VALUE / 2

    // An opposite tilt right after letting go, waiting to show whether it is a rebound.
    private var pendingX = 0
    private var pendingY = 0
    private var pendingSince = 0L

    /** The stick is held in a direction (past the press threshold, not yet released). */
    val engaged: Boolean get() = heldX != 0 || heldY != 0

    /** A possible reverse is waiting; call [tick] after [reverseConfirmMs] if no event comes. */
    val pending: Boolean get() = pendingX != 0 || pendingY != 0

    fun reset() {
        heldX = 0
        heldY = 0
        pendingX = 0
        pendingY = 0
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
            if (lean < releaseThreshold) {
                releasedX = heldX
                releasedY = heldY
                releasedAt = now
                heldX = 0
                heldY = 0
                return null
            }
            return holdRepeat(now)
        }
        if (pending) {
            val lean = if (pendingX != 0) x * pendingX else y * pendingY
            return when {
                lean < releaseThreshold -> {
                    // Came straight back: it was the spring overshooting.
                    pendingX = 0
                    pendingY = 0
                    null
                }
                lean >= reverseDeepLean || now - pendingSince >= reverseConfirmMs -> confirmPending(now)
                else -> null
            }
        }
        val ax = abs(x)
        val ay = abs(y)
        if (maxOf(ax, ay) < pressThreshold) return null
        // One direction only: the stronger axis, so a diagonal doesn't press twice.
        val dx = if (ax >= ay) sign(x).toInt() else 0
        val dy = if (ax >= ay) 0 else sign(y).toInt()
        val opposite = dx == -releasedX && dy == -releasedY
        if (opposite && now - releasedAt < reboundMs && maxOf(ax, ay) < reverseDeepLean) {
            pendingX = dx
            pendingY = dy
            pendingSince = now
            return null
        }
        return press(dx, dy, now)
    }

    /**
     * Time passing with no new stick event: confirms a reverse that stayed out long enough, or
     * repeats a held direction.
     */
    fun tick(now: Long): Press? =
        when {
            engaged -> holdRepeat(now)
            pending && now - pendingSince >= reverseConfirmMs -> confirmPending(now)
            else -> null
        }

    /** Milliseconds until [tick] has something to do, or null when nothing is waiting. */
    fun nextTickIn(now: Long): Long? =
        when {
            engaged -> (holdDue() - now).coerceAtLeast(1L)
            pending -> (pendingSince + reverseConfirmMs - now).coerceAtLeast(1L)
            else -> null
        }

    private fun holdDue(): Long =
        if (lastRepeatAt == pressedAt) {
            pressedAt + holdDelayMs
        } else {
            lastRepeatAt + holdRepeatMs
        }

    private fun holdRepeat(now: Long): Press? {
        if (now < holdDue()) return null
        lastRepeatAt = now
        return Press(heldX, heldY)
    }

    private fun confirmPending(now: Long): Press {
        val dx = pendingX
        val dy = pendingY
        pendingX = 0
        pendingY = 0
        return press(dx, dy, now)
    }

    private fun press(
        dx: Int,
        dy: Int,
        now: Long,
    ): Press {
        heldX = dx
        heldY = dy
        pressedAt = now
        lastRepeatAt = now
        return Press(dx, dy)
    }
}
