package com.raja.bingehwa.domain

import java.time.Instant
import java.time.ZoneId

/** Numbers behind the Profile charts. Pure functions so the day-boundary logic is testable. */
object ReadingStats {

    /** Chapters finished per day for the last 7 days, oldest first, today last. */
    fun weeklyCounts(timestampsMs: List<Long>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<Int> {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val counts = IntArray(7)
        timestampsMs.forEach { ts ->
            val day = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
            val ago = java.time.temporal.ChronoUnit.DAYS.between(day, today).toInt()
            if (ago in 0..6) counts[6 - ago]++
        }
        return counts.toList()
    }

    /** Top tags as fractions of the strongest one (1.0 = the strongest), for the taste bars. */
    fun topTags(profile: Map<String, Double>, n: Int = 5): List<Pair<String, Float>> {
        val top = profile.entries.sortedByDescending { it.value }.take(n)
        val max = top.firstOrNull()?.value ?: return emptyList()
        return top.map { it.key to (it.value / max).toFloat() }
    }
}
