package com.raja.bingehwa.domain

import com.raja.bingehwa.data.model.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterUtilTest {
    private fun ch(id: String, n: String, pages: Int = 10, ext: String? = null, at: String = "2026-01-01") =
        Chapter(id, "s", n, null, pages, at, ext)

    @Test fun sortsNumericallyNotAlphabetically() {
        val out = ChapterUtil.prepare(listOf(ch("a", "2"), ch("b", "10"), ch("c", "1"), ch("d", "9.5")))
        assertEquals(listOf("10", "9.5", "2", "1"), out.map { it.number })
    }

    @Test fun dedupePrefersReadableThenNewest() {
        val out = ChapterUtil.prepare(
            listOf(
                ch("ext", "5", pages = 0, ext = "https://x"),
                ch("old", "5", at = "2026-01-01"),
                ch("new", "5", at = "2026-02-01"),
            ),
        )
        assertEquals(1, out.size)
        assertEquals("new", out[0].id)
    }

    @Test fun nextAndPrevious() {
        val list = ChapterUtil.prepare(listOf(ch("a", "1"), ch("b", "2"), ch("c", "3")))
        assertEquals("c", ChapterUtil.next(list, "b")?.id)
        assertNull(ChapterUtil.next(list, "c"))
        assertEquals("a", ChapterUtil.previous(list, "b")?.id)
        assertNull(ChapterUtil.previous(list, "a"))
    }

    @Test fun unreadCountUsesLastReadNumber() {
        val list = ChapterUtil.prepare(listOf(ch("a", "1"), ch("b", "2"), ch("c", "3")))
        assertEquals(1, ChapterUtil.unreadCount(list, "2"))
        assertEquals(3, ChapterUtil.unreadCount(list, null))
        assertEquals(0, ChapterUtil.unreadCount(list, "3"))
    }

    @Test fun continueTargetStartsAtFirstChapterThenNext() {
        val list = ChapterUtil.prepare(listOf(ch("a", "1"), ch("b", "2"), ch("c", "3")))
        assertEquals("a", ChapterUtil.continueTarget(list, null)?.id)
        assertEquals("c", ChapterUtil.continueTarget(list, "b")?.id)
        assertEquals("c", ChapterUtil.continueTarget(list, "c")?.id)
    }
}
