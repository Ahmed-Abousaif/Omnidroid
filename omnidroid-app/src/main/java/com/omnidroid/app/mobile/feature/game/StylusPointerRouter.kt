package com.omnidroid.app.mobile.feature.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import gg.padkit.UnhandledPointer

/** Select one new host pointer inside the visible touchscreen, independently of pad fingers. */
internal class StylusPointerRouter(private val bounds: Rect) {
    enum class Action { DOWN, MOVE, UP }

    data class Event(val action: Action, val position: Offset)

    private var previousIds = emptySet<Long>()
    private var stylusId: Long? = null
    private var lastPosition = Offset.Zero

    fun update(pointers: List<UnhandledPointer>): List<Event> {
        val events = mutableListOf<Event>()
        if (stylusId != null) {
            val pointer = pointers.firstOrNull { it.id == stylusId }
            if (pointer == null) {
                cancel()?.let(events::add)
            } else if (pointer.position != lastPosition) {
                lastPosition = pointer.position
                events += Event(Action.MOVE, lastPosition)
            }
        }
        if (stylusId == null) {
            pointers.firstOrNull { it.isNew && it.id !in previousIds && bounds.contains(it.position) }?.let { pointer ->
                stylusId = pointer.id
                lastPosition = pointer.position
                events += Event(Action.DOWN, lastPosition)
            }
        }
        previousIds = pointers.map { it.id }.toSet()
        return events
    }

    fun cancel(): Event? {
        if (stylusId == null) return null
        stylusId = null
        return Event(Action.UP, lastPosition)
    }
}
