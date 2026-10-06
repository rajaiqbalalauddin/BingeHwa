package com.raja.bingehwa.domain

import com.raja.bingehwa.data.model.Series
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Content-based recommendations from tag overlap. Explained for a beginner: every tag is a
 * dimension; the reader's taste is an arrow in that space; a candidate points in some
 * direction too. The smaller the angle (cosine), the better the match. No server needed.
 */
object Recommender {

    data class Owned(val title: String, val tags: List<String>)
    data class Scored(val series: Series, val percent: Int, val reason: String)

    /** Library tags count once each, explicit taste picks count double as a stronger signal. */
    fun profile(library: List<Owned>, tastePicks: Set<String>): Map<String, Double> {
        val out = HashMap<String, Double>()
        library.forEach { o -> o.tags.forEach { out[it] = (out[it] ?: 0.0) + 1.0 } }
        tastePicks.forEach { out[it] = (out[it] ?: 0.0) + 2.0 }
        return out
    }

    /** Cosine between the weighted taste profile and a candidate's tag set (0.0 to 1.0). */
    fun cosine(profile: Map<String, Double>, tags: Collection<String>): Double {
        if (profile.isEmpty() || tags.isEmpty()) return 0.0
        val unique = tags.toSet()
        val dot = unique.sumOf { profile[it] ?: 0.0 }
        val normP = sqrt(profile.values.sumOf { it * it })
        val normC = sqrt(unique.size.toDouble())
        return if (normP == 0.0 || normC == 0.0) 0.0 else dot / (normP * normC)
    }

    /** The owned series that shares the most tags with [candidate], for the "because you read" line. */
    fun bestReason(library: List<Owned>, candidate: Series, tastePicks: Set<String>): String {
        val best = library
            .map { it to it.tags.toSet().intersect(candidate.tags.toSet()).size }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
        if (best != null) return "Because you read ${best.first.title}"
        val shared = candidate.tags.firstOrNull { it in tastePicks }
        return if (shared != null) "Matches your taste for $shared" else "Popular with readers like you"
    }

    fun rank(
        candidates: List<Series>,
        library: List<Owned>,
        ownedIds: Set<String>,
        tastePicks: Set<String>,
        limit: Int = 20,
    ): List<Scored> {
        val prof = profile(library, tastePicks)
        return candidates
            .asSequence()
            .filter { it.id !in ownedIds }
            .distinctBy { it.id }
            .map { it to cosine(prof, it.tags) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { (s, score) ->
                // Cosine rarely reaches 1.0 in practice, so stretch it a little for readable percentages.
                val percent = (score * 120).roundToInt().coerceIn(1, 99)
                Scored(s, percent, bestReason(library, s, tastePicks))
            }
            .toList()
    }
}
