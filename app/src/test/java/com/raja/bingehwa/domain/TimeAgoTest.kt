package com.raja.bingehwa.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime

class TimeAgoTest {
    private val now = OffsetDateTime.parse("2026-10-12T12:00:00+00:00")

    @Test fun formatsUnits() {
        assertEquals("now", TimeAgo.format("2026-10-12T12:00:00+00:00", now))
        assertEquals("30m", TimeAgo.format("2026-10-12T11:30:00+00:00", now))
        assertEquals("2h", TimeAgo.format("2026-10-12T10:00:00+00:00", now))
        assertEquals("3d", TimeAgo.format("2026-10-09T12:00:00+00:00", now))
    }

    @Test fun badInputIsBlank() {
        assertEquals("", TimeAgo.format("", now))
        assertEquals("", TimeAgo.format("not a date", now))
    }

    @Test fun millisVariant() {
        assertEquals("5m", TimeAgo.formatMillis(0L, 5 * 60_000L))
        assertEquals("now", TimeAgo.formatMillis(10_000L, 5_000L))
    }
}
