package com.raja.bingehwa.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReadingStatsTest {
    private val zone = ZoneId.of("Asia/Kuala_Lumpur")
    private fun ms(day: Int, hour: Int) = ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun countsPerDayOldestFirst() {
        val now = ms(12, 15)
        val out = ReadingStats.weeklyCounts(listOf(ms(12, 1), ms(12, 14), ms(11, 23), ms(6, 9), ms(5, 9)), now, zone)
        assertEquals(listOf(1, 0, 0, 0, 0, 1, 2), out)
    }

    @Test fun topTagsAreRelativeToStrongest() {
        val out = ReadingStats.topTags(mapOf("A" to 4.0, "B" to 2.0, "C" to 1.0), 2)
        assertEquals(listOf("A" to 1f, "B" to 0.5f), out)
    }

    @Test fun emptyProfileGivesNothing() {
        assertEquals(emptyList<Pair<String, Float>>(), ReadingStats.topTags(emptyMap()))
    }
}
