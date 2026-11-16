package com.omnidroid.app.shared.game.view

import com.omnidroid.libretrodroid.Variable
import java.util.concurrent.ConcurrentLinkedQueue

/** UI producers enqueue snapshots; only the emulation thread drains complete batches. */
internal class CoreVariableUpdateQueue {
    private val batches = ConcurrentLinkedQueue<List<Variable>>()

    fun enqueue(variables: List<Variable>) {
        if (variables.isNotEmpty()) batches.add(variables.toList())
    }

    fun drain(apply: (List<Variable>) -> Unit) {
        while (true) apply(batches.poll() ?: return)
    }
}
