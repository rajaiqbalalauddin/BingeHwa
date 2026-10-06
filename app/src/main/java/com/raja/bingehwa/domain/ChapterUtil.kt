package com.raja.bingehwa.domain

import com.raja.bingehwa.data.model.Chapter

/**
 * Chapter list helpers. They live apart from the repository so they can be unit tested
 * without Android, because chapter ordering is where reading apps usually go wrong.
 */
object ChapterUtil {

    /** "12.5" -> 12.5, "Oneshot" -> null. */
    fun numberValue(number: String): Double? = number.trim().toDoubleOrNull()

    /**
     * Several scanlation groups can upload the same chapter. Keep one per number, preferring
     * a readable one over an external link, then the newest.
     */
    fun dedupe(chapters: List<Chapter>): List<Chapter> =
        chapters
            .groupBy { it.number }
            .map { (_, group) ->
                group.sortedWith(
                    compareByDescending<Chapter> { it.readableInApp }.thenByDescending { it.publishAt },
                ).first()
            }

    /** Newest first. Chapters without a numeric value go last, ordered by date. */
    fun sortDescending(chapters: List<Chapter>): List<Chapter> =
        chapters.sortedWith(
            compareByDescending<Chapter> { numberValue(it.number) ?: Double.NEGATIVE_INFINITY }
                .thenByDescending { it.publishAt },
        )

    fun prepare(chapters: List<Chapter>): List<Chapter> = sortDescending(dedupe(chapters))

    /** The chapter that comes right after [currentId] in reading order, or null at the end. */
    fun next(descending: List<Chapter>, currentId: String): Chapter? {
        val i = descending.indexOfFirst { it.id == currentId }
        return if (i > 0) descending[i - 1] else null
    }

    fun previous(descending: List<Chapter>, currentId: String): Chapter? {
        val i = descending.indexOfFirst { it.id == currentId }
        return if (i >= 0 && i < descending.lastIndex) descending[i + 1] else null
    }

    /** Chapters newer than the last one the reader finished. */
    fun unreadCount(descending: List<Chapter>, lastReadNumber: String?): Int {
        val read = lastReadNumber?.let { numberValue(it) } ?: return descending.count { it.readableInApp || it.externalUrl != null }
        return descending.count { c -> (numberValue(c.number) ?: Double.NEGATIVE_INFINITY) > read }
    }

    /** Where to start: the first chapter for new readers, otherwise the one after the last read. */
    fun continueTarget(descending: List<Chapter>, lastReadChapterId: String?): Chapter? {
        if (descending.isEmpty()) return null
        if (lastReadChapterId == null) return descending.last()
        return next(descending, lastReadChapterId) ?: descending.firstOrNull { it.id == lastReadChapterId }
    }
}
