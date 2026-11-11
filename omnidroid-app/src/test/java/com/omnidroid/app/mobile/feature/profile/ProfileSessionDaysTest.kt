package com.omnidroid.app.mobile.feature.profile

import com.omnidroid.lib.library.db.entity.GameSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class ProfileSessionDaysTest {
    @Test
    fun `groups by local midnight and keeps sessions newest first`() {
        val sessions =
            listOf(
                session(1, "2026-11-09T21:59:00Z"),
                session(2, "2026-11-09T22:01:00Z"),
                session(3, "2026-11-10T10:00:00Z"),
            )
        val days =
            groupProfileSessions(
                sessions,
                now = timestamp("2026-11-10T12:00:00Z"),
                timeZone = TimeZone.getTimeZone("GMT+02:00"),
            )

        assertEquals(listOf(0, -1), days.map { it.relativeDay })
        assertEquals(listOf(3L, 2L), days[0].sessions.map { it.session.id })
        assertEquals(listOf(1L), days[1].sessions.map { it.session.id })
        assertEquals(timestamp("2026-11-09T22:00:00Z"), days[0].startedAt)
    }

    @Test
    fun `yesterday spans a 23 hour spring daylight saving day`() {
        val days =
            groupProfileSessions(
                listOf(session(1, "2026-03-08T07:30:00Z")),
                now = timestamp("2026-03-09T16:00:00Z"),
                timeZone = TimeZone.getTimeZone("America/New_York"),
            )

        assertEquals(-1, days.single().relativeDay)
        assertEquals(timestamp("2026-03-08T05:00:00Z"), days.single().startedAt)
    }

    @Test
    fun `repeated autumn clock hour stays in the same day`() {
        val days =
            groupProfileSessions(
                listOf(session(1, "2026-11-01T05:30:00Z"), session(2, "2026-11-01T06:30:00Z")),
                now = timestamp("2026-11-02T17:00:00Z"),
                timeZone = TimeZone.getTimeZone("America/New_York"),
            )

        assertEquals(-1, days.single().relativeDay)
        assertEquals(listOf(2L, 1L), days.single().sessions.map { it.session.id })
    }

    @Test
    fun `older dates have no relative label and empty history has no groups`() {
        val now = timestamp("2026-11-10T12:00:00Z")
        val zone = TimeZone.getTimeZone("UTC")
        val days = groupProfileSessions(listOf(session(1, "2026-11-08T12:00:00Z")), now, zone)

        assertNull(days.single().relativeDay)
        assertEquals(emptyList<ProfileSessionDay>(), groupProfileSessions(emptyList(), now, zone))
    }

    private fun session(
        id: Long,
        playedAt: String,
    ) = ProfileViewModel.RecentSessionItem(
        session =
            GameSession(
                id = id,
                gameId = 1,
                durationMs = 60_000,
                playedAt = timestamp(playedAt),
                xpEarned = 10,
                streakDay = 1,
            ),
        gameTitle = "Test game",
    )

    private fun timestamp(value: String) = Instant.parse(value).toEpochMilli()
}
