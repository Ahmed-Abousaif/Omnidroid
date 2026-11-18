package com.omnidroid.app.mobile.shared.controller

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * A simulated analog stick, for testing [StickPressFilter] against movement like a real one's.
 *
 * - The thumb drives it ([push], [hold]); let go, a damped spring pulls it back to the centre
 *   ([letGo]) and overshoots past it ("snapback"), more on a worn stick.
 * - The controller reports it every [sampleMs] with a little [noise] and resting offset, quantized
 *   to 16 bits, and only when the value changes: a stick held against the gate sends nothing.
 * - Android batches joystick events per frame, so with a 16 ms sample the filter can see the stick
 *   jump straight past the centre.
 */
internal class PhysicalStick(
    private val spring: Spring = Spring.SNAPPY,
    private val sampleMs: Long = 8L,
    private val noise: Float = 0.01f,
    private val restX: Float = 0.03f,
    private val restY: Float = -0.02f,
    seed: Long = 1L,
) {
    /** Return spring: natural frequency and damping ratio. */
    data class Spring(
        val hz: Double,
        val damping: Double,
    ) {
        companion object {
            /** A new stick: about 30% overshoot. */
            val SNAPPY = Spring(hz = 35.0, damping = 0.35)

            /** Hardly any overshoot. */
            val DAMPED = Spring(hz = 30.0, damping = 0.7)

            /** A worn stick: about 70% overshoot, past the press threshold. */
            val WORN = Spring(hz = 25.0, damping = 0.12)

            val ALL = listOf(SNAPPY, DAMPED, WORN)
        }
    }

    data class Sample(
        val x: Float,
        val y: Float,
        val time: Long,
    )

    private val random = Random(seed)
    private val phase = random.nextLong(sampleMs)

    // True position every STEP_MS, as (x, y).
    private val path = mutableListOf(0.0 to 0.0)
    private var x = 0.0
    private var y = 0.0

    val elapsedMs: Long get() = ((path.size - 1) * STEP_MS).toLong()

    /** The thumb moves the stick to ([toX], [toY]) over [ms], easing in and out. */
    fun push(
        toX: Double,
        toY: Double,
        ms: Long,
    ): PhysicalStick {
        val fromX = x
        val fromY = y
        val steps = max(1, (ms / STEP_MS).roundToInt())
        for (i in 1..steps) {
            val t = i.toDouble() / steps
            val eased = t * t * (3 - 2 * t)
            x = fromX + (toX - fromX) * eased
            y = fromY + (toY - fromY) * eased
            path += x to y
        }
        return this
    }

    fun hold(ms: Long): PhysicalStick {
        repeat((ms / STEP_MS).roundToInt()) { path += x to y }
        return this
    }

    /** The thumb lets go: the spring takes the stick back to the centre. */
    fun letGo(ms: Long = 150L): PhysicalStick {
        val omega = 2 * Math.PI * spring.hz / 1000.0
        var vx = 0.0
        var vy = 0.0
        val dt = STEP_MS / SUBSTEPS
        repeat((ms / STEP_MS).roundToInt()) {
            repeat(SUBSTEPS) {
                vx += (-omega * omega * x - 2 * spring.damping * omega * vx) * dt
                vy += (-omega * omega * y - 2 * spring.damping * omega * vy) * dt
                x += vx * dt
                y += vy * dt
            }
            path += x to y
        }
        return this
    }

    /** What the app receives: one sample per report, only when the reading changed. */
    fun samples(): List<Sample> {
        val result = mutableListOf<Sample>()
        var last: Pair<Float, Float>? = null
        var time = phase
        while (time <= elapsedMs) {
            val (px, py) = path[(time / STEP_MS).roundToInt()]
            // Pressed against the gate, the whole stick is held still: neither axis jitters.
            val pinned = px * px + py * py >= 0.998
            val reading = read(px, restX, pinned) to read(py, restY, pinned)
            if (reading != last) result += Sample(reading.first, reading.second, time)
            last = reading
            time += sampleMs
        }
        return result
    }

    private fun read(
        position: Double,
        rest: Float,
        pinned: Boolean,
    ): Float {
        // At the gate the reading is full scale.
        if (abs(position) >= 0.999) return if (position > 0) 1f else -1f
        val jitter = if (pinned) 0f else (random.nextFloat() * 2 - 1) * noise
        val value = (position + rest + jitter).coerceIn(-1.0, 1.0)
        return (value * 32767).roundToInt() / 32767f
    }

    data class TimedPress(
        val press: StickPressFilter.Press,
        val time: Long,
    )

    /** Runs [filter] the way ControllerInputBridge does: one update per stick report, nothing between. */
    fun drive(filter: StickPressFilter = StickPressFilter()): List<TimedPress> =
        samples().mapNotNull { sample ->
            filter.update(sample.x, sample.y, sample.time)?.let { TimedPress(it, sample.time) }
        }

    companion object {
        const val STEP_MS = 0.5
        private const val SUBSTEPS = 20

        /** Report intervals: wired / Bluetooth / batched per 60 Hz frame. */
        val SAMPLE_INTERVALS = listOf(4L, 8L, 16L)
    }
}
