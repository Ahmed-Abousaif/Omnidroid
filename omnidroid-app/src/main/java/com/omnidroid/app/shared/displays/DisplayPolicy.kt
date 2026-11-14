package com.omnidroid.app.shared.displays

/** Facts are deliberately independent of device model names and touchscreen assumptions. */
data class DisplayFacts(
    val id: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val densityDpi: Int,
    val rotation: Int,
    val flags: Int,
    val awake: Boolean,
    val presentation: Boolean,
    val privateDisplay: Boolean,
    val activityAllowed: Boolean,
)

enum class DisplayOutput { PRESENTATION, ACTIVITY }

enum class DisplayStatus { READY, SHOWING, SLEEPING, HIDDEN, PAUSED, UNAVAILABLE }

data class DisplayEntry(
    val facts: DisplayFacts,
    val status: DisplayStatus,
    val output: DisplayOutput?,
)

data class DisplaysState(
    /** A ready inventory can come from the single-screen watcher without a service connection. */
    val connected: Boolean = false,
    val automatic: Boolean = true,
    val entries: List<DisplayEntry> = emptyList(),
) {
    val hasOutput: Boolean get() = entries.any { it.status == DisplayStatus.SHOWING }
}

/** Pure policy: an advertised capability is only a candidate, never a successful output. */
object DisplayPolicy {
    fun needsCoordinator(
        displayIds: Set<Int>,
        hostDisplays: Set<Int>,
    ): Boolean = hostDisplays.isNotEmpty() && displayIds.any { it !in hostDisplays }

    fun evaluate(
        facts: DisplayFacts,
        hostDisplays: Set<Int>,
        foreground: Boolean,
        automatic: Boolean,
        hidden: Boolean,
        showing: Boolean,
        failed: Boolean,
    ): DisplayEntry? {
        if (facts.id in hostDisplays) return null
        val output =
            when {
                facts.presentation -> DisplayOutput.PRESENTATION
                facts.activityAllowed -> DisplayOutput.ACTIVITY
                else -> null
            }
        val status =
            when {
                !automatic || hidden -> DisplayStatus.HIDDEN
                !facts.awake -> DisplayStatus.SLEEPING
                !foreground -> DisplayStatus.PAUSED
                failed || output == null -> DisplayStatus.UNAVAILABLE
                showing -> DisplayStatus.SHOWING
                else -> DisplayStatus.READY
            }
        return DisplayEntry(facts, status, output)
    }
}
