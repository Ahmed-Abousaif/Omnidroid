package com.omnidroid.app.shared.game.view

import com.omnidroid.libretrodroid.Variable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreVariableUpdateQueueTest {
    @Test
    fun updatesBeforeSurfaceCreationWaitForEmulationThreadAndPreserveBatches() {
        val queue = CoreVariableUpdateQueue()
        val layout = listOf(Variable("layout", "single"), Variable("swap", "bottom"))
        val received = mutableListOf<List<Variable>>()
        queue.enqueue(layout)
        assertTrue(received.isEmpty())
        queue.drain {
            received += it
            if (received.size == 1) queue.enqueue(listOf(Variable("resolution", "2")))
        }
        assertEquals(listOf(layout, listOf(Variable("resolution", "2"))), received)
        queue.drain { error("Already drained") }
    }

    @Test
    fun producerMutationCannotChangeQueuedOptions() {
        val queue = CoreVariableUpdateQueue()
        val variables = mutableListOf(Variable("layout", "single"))
        queue.enqueue(variables)
        variables.clear()
        queue.drain { assertEquals(listOf(Variable("layout", "single")), it) }
    }
}
