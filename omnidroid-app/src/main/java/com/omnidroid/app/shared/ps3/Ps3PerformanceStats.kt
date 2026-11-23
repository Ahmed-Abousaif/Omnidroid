package com.omnidroid.app.shared.ps3

/** Cumulative valid RSX flip timings, not a claim about game simulation speed. */
class Ps3PerformanceStats(private val values: LongArray) {
    init { require(values.size == 14) }
    val frames get() = values[0]
    val periodNs get() = values[1]
    val workNs get() = values[2]
    val lastPeriodNs get() = values[3]
    val lastWorkNs get() = values[4]
    val submitted get() = values[5]
    val errors get() = values[6]
    val sessionsCreated get() = values[7]
    val sessionsClosed get() = values[8]
    val rsxTid get() = values[9]
    val hintTid get() = values[10]
    val options get() = values[11]
    val displayHz get() = values[13] / 1000.0
    val active get() = values[12] != 0L
}
