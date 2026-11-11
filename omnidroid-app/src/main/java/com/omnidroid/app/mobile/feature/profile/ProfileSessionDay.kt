package com.omnidroid.app.mobile.feature.profile

import java.util.Calendar
import java.util.TimeZone

internal data class ProfileSessionDay(
    val startedAt: Long,
    val relativeDay: Int?,
    val sessions: List<ProfileViewModel.RecentSessionItem>,
)

/** Calendar boundaries handle time zones and 23/25-hour days without assuming a fixed day length. */
internal fun groupProfileSessions(
    sessions: List<ProfileViewModel.RecentSessionItem>,
    now: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
): List<ProfileSessionDay> {
    val calendar = Calendar.getInstance(timeZone)

    fun startOfDay(timestamp: Long): Long {
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    val today = startOfDay(now)
    calendar.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = calendar.timeInMillis
    return sessions.sortedByDescending { it.session.playedAt }
        .groupBy { startOfDay(it.session.playedAt) }
        .map { (day, items) ->
            ProfileSessionDay(
                startedAt = day,
                relativeDay =
                    when (day) {
                        today -> 0
                        yesterday -> -1
                        else -> null
                    },
                sessions = items,
            )
        }
}
