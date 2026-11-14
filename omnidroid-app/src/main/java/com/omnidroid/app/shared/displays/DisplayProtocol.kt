package com.omnidroid.app.shared.displays

import android.os.Bundle

/** Internal Messenger protocol; the service and helper activity are never exported. */
internal object DisplayProtocol {
    const val UPDATE_HOST = 1
    const val SET_AUTOMATIC = 2
    const val SET_HIDDEN = 3
    const val REFRESH = 4
    const val REMOVE_HOST = 5
    const val STATE = 6
    const val LAUNCH_ACTIVITY = 7
    const val OUTPUT_READY = 8
    const val OUTPUT_GONE = 9
    const val OUTPUT_FAILED = 10
    const val CLOSE_OUTPUT = 11
    const val REQUEST_HOST = 12
    const val HOSTS = "hosts"
    const val FOREGROUND = "foreground"
    const val ENABLED = "enabled"
    const val HIDDEN = "hidden"
    const val ID = "id"
    const val TOKEN = "token"

    fun encode(state: DisplaysState) =
        Bundle().apply {
            putBoolean(ENABLED, state.automatic)
            putParcelableArrayList(
                "entries",
                ArrayList(
                    state.entries.map { entry ->
                        Bundle().apply {
                            val facts = entry.facts
                            putInt(ID, facts.id)
                            putString("name", facts.name)
                            putInt("width", facts.width)
                            putInt("height", facts.height)
                            putInt("density", facts.densityDpi)
                            putInt("rotation", facts.rotation)
                            putInt("flags", facts.flags)
                            putBoolean("awake", facts.awake)
                            putBoolean("presentation", facts.presentation)
                            putBoolean("private", facts.privateDisplay)
                            putBoolean("activity", facts.activityAllowed)
                            putString("status", entry.status.name)
                            putString("output", entry.output?.name)
                        }
                    },
                ),
            )
        }

    @Suppress("DEPRECATION")
    fun decode(data: Bundle) =
        DisplaysState(
            connected = true,
            automatic = data.getBoolean(ENABLED, true),
            entries =
                data.getParcelableArrayList<Bundle>("entries").orEmpty().map { item ->
                    DisplayEntry(
                        DisplayFacts(
                            item.getInt(ID), item.getString("name").orEmpty(),
                            item.getInt("width"), item.getInt("height"), item.getInt("density"),
                            item.getInt("rotation"), item.getInt("flags"), item.getBoolean("awake"),
                            item.getBoolean("presentation"), item.getBoolean("private"), item.getBoolean("activity"),
                        ),
                        DisplayStatus.valueOf(checkNotNull(item.getString("status"))),
                        item.getString("output")?.let(DisplayOutput::valueOf),
                    )
                },
        )
}
