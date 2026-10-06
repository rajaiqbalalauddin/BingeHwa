package com.raja.bingehwa.domain

import java.time.Duration
import java.time.OffsetDateTime

/** Short relative times for the feed ("2h", "3d"). Pure so it is easy to test. */
object TimeAgo {
    fun formatMillis(ms: Long, nowMs: Long = System.currentTimeMillis()): String {
        val mins = ((nowMs - ms) / 60_000).coerceAtLeast(0)
        return when {
            mins < 1 -> "now"
            mins < 60 -> "${mins}m"
            mins < 60 * 24 -> "${mins / 60}h"
            mins < 60 * 24 * 30 -> "${mins / (60 * 24)}d"
            mins < 60 * 24 * 365 -> "${mins / (60 * 24 * 30)}mo"
            else -> "${mins / (60 * 24 * 365)}y"
        }
    }

    fun format(iso: String, now: OffsetDateTime = OffsetDateTime.now()): String {
        if (iso.isBlank()) return ""
        val then = try {
            OffsetDateTime.parse(iso)
        } catch (e: Exception) {
            return ""
        }
        val mins = Duration.between(then, now).toMinutes().coerceAtLeast(0)
        return when {
            mins < 1 -> "now"
            mins < 60 -> "${mins}m"
            mins < 60 * 24 -> "${mins / 60}h"
            mins < 60 * 24 * 30 -> "${mins / (60 * 24)}d"
            mins < 60 * 24 * 365 -> "${mins / (60 * 24 * 30)}mo"
            else -> "${mins / (60 * 24 * 365)}y"
        }
    }
}
