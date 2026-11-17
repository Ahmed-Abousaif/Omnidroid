package com.omnidroid.app.mobile.shared.controller

import com.omnidroid.app.mobile.shared.controller.PhysicalStick.Spring
import com.omnidroid.app.mobile.shared.controller.StickPressFilter.Press
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * [StickPressFilter] against simulated physical sticks: every case runs on a new, a damped and a
 * worn stick, at 4, 8 and 16 ms reports, with several noise seeds.
 */
class StickPressFilterPhysicalTest {
    private val directions = listOf(Press(1, 0), Press(-1, 0), Press(0, 1), Press(0, -1))

    private fun everyStick(block: (name: String, make: () -> PhysicalStick) -> Unit) {
        for (spring in Spring.ALL) {
            for (sampleMs in PhysicalStick.SAMPLE_INTERVALS) {
                for (seed in 1L..6L) {
                    block("$spring, ${sampleMs}ms, seed $seed") {
                        PhysicalStick(spring = spring, sampleMs = sampleMs, seed = seed)
                    }
                }
            }
        }
    }

    private fun PhysicalStick.pushTo(
        press: Press,
        ms: Long,
    ) = push(press.x.toDouble(), press.y.toDouble(), ms)

    private fun opposite(press: Press) = Press(-press.x, -press.y)

    private fun List<PhysicalStick.TimedPress>.directions() = map { it.press }

    @Test
    fun `a flick and let go is one press, whatever the snapback`() {
        everyStick { name, stick ->
            for (dir in directions) {
                for (holdMs in listOf(0L, 30L, 150L)) {
                    val presses = stick().pushTo(dir, 25).hold(holdMs).letGo().drive()
                    assertEquals("$name, $dir held $holdMs ms", listOf(dir), presses.directions())
                }
            }
        }
    }

    @Test
    fun `a quick flick back through the centre is two presses`() {
        everyStick { name, stick ->
            for (dir in directions) {
                val presses =
                    stick()
                        .pushTo(dir, 25)
                        .hold(30)
                        .pushTo(opposite(dir), 35)
                        .hold(60)
                        .letGo()
                        .drive()
                assertEquals("$name, $dir", listOf(dir, opposite(dir)), presses.directions())
            }
        }
    }

    @Test
    fun `a flick back that skips the centre between reports still counts`() {
        everyStick { name, stick ->
            for (dir in directions) {
                // 8 ms from one edge to the other, then pinned there: the stick sends no more events.
                val presses =
                    stick()
                        .pushTo(dir, 20)
                        .hold(40)
                        .pushTo(opposite(dir), 8)
                        .hold(300)
                        .letGo()
                        .drive()
                assertEquals("$name, $dir", listOf(dir, opposite(dir)), presses.directions())
            }
        }
    }

    @Test
    fun `holding against the gate repeats at the hold pace`() {
        everyStick { name, stick ->
            for (dir in directions) {
                val presses = stick().pushTo(dir, 30).hold(1000).letGo().drive()
                assertTrue("$name, $dir: ${presses.directions()}", presses.all { it.press == dir })
                val gaps = presses.zipWithNext { a, b -> b.time - a.time }
                assertEquals("$name, $dir", 400L, gaps.first())
                assertTrue("$name, $dir: $gaps", gaps.drop(1).all { it == 110L })
                assertTrue("$name, $dir: ${presses.size} presses", presses.size in 6..8)
            }
        }
    }

    @Test
    fun `a resting stick with drift and noise never presses`() {
        for (seed in 1L..10L) {
            val stick = PhysicalStick(noise = 0.05f, restX = 0.12f, restY = -0.1f, seed = seed)
            assertEquals("seed $seed", emptyList<Press>(), stick.hold(3000).drive().directions())
        }
    }

    @Test
    fun `a thumb hovering at the threshold presses once`() {
        for (spring in Spring.ALL) {
            for (seed in 1L..10L) {
                val stick = PhysicalStick(spring = spring, noise = 0.05f, seed = seed)
                val presses = stick.push(0.62, 0.0, 60).hold(300).letGo().drive()
                assertEquals("$spring, seed $seed", listOf(Press(1, 0)), presses.directions())
            }
        }
    }

    @Test
    fun `rolling along the rim presses each direction it reaches`() {
        everyStick { name, stick ->
            val rolled = stick().push(1.0, 0.0, 25).hold(50)
            for (step in 1..12) {
                val angle = Math.toRadians(step * 7.5)
                rolled.push(cos(angle), sin(angle), 10)
            }
            val presses = rolled.hold(50).letGo().drive()
            assertEquals(name, listOf(Press(1, 0), Press(0, 1)), presses.directions())
        }
    }

    @Test
    fun `quick repeated flicks are one press each`() {
        everyStick { name, stick ->
            for (dir in directions) {
                val flicks = stick()
                repeat(4) { flicks.pushTo(dir, 20).hold(20).letGo(110) }
                assertEquals("$name, $dir", List(4) { dir }, flicks.drive().directions())
            }
        }
    }

    @Test
    fun `alternating flicks count every one`() {
        everyStick { name, stick ->
            val flicks = stick()
            val expected = List(4) { if (it % 2 == 0) Press(1, 0) else Press(-1, 0) }
            for (dir in expected) flicks.pushTo(dir, 30).hold(60).letGo(120)
            assertEquals(name, expected, flicks.drive().directions())
        }
    }
}
