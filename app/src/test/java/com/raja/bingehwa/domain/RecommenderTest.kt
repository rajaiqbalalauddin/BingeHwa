package com.raja.bingehwa.domain

import com.raja.bingehwa.data.model.Series
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommenderTest {
    private fun s(id: String, vararg tags: String) =
        Series(id, "T$id", "", null, tags.toList(), "ongoing", null, null)

    @Test fun cosineIsZeroWithNoOverlap() {
        assertEquals(0.0, Recommender.cosine(mapOf("Romance" to 2.0), listOf("Horror")), 1e-9)
    }

    @Test fun moreOverlapScoresHigher() {
        val prof = mapOf("Romance" to 2.0, "Drama" to 1.0)
        val a = Recommender.cosine(prof, listOf("Romance", "Drama"))
        val b = Recommender.cosine(prof, listOf("Romance", "Horror"))
        assertTrue(a > b)
    }

    @Test fun rankSkipsOwnedAndSortsByMatch() {
        val lib = listOf(Recommender.Owned("Owned One", listOf("Romance", "Drama")))
        val out = Recommender.rank(
            candidates = listOf(s("1", "Romance", "Drama"), s("2", "Horror"), s("3", "Romance")),
            library = lib,
            ownedIds = setOf("3"),
            tastePicks = emptySet(),
        )
        assertEquals(listOf("1"), out.map { it.series.id })
        assertEquals("Because you read Owned One", out[0].reason)
        assertTrue(out[0].percent in 1..99)
    }

    @Test fun tastePicksAloneStillRecommend() {
        val out = Recommender.rank(listOf(s("1", "Fantasy")), emptyList(), emptySet(), setOf("Fantasy"))
        assertEquals(1, out.size)
        assertEquals("Matches your taste for Fantasy", out[0].reason)
    }
}
