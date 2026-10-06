package com.raja.bingehwa.data

import com.raja.bingehwa.data.remote.MdAtHome
import com.raja.bingehwa.data.remote.MdList
import com.raja.bingehwa.data.remote.MdManga
import com.raja.bingehwa.data.remote.pageUrls
import com.raja.bingehwa.data.remote.toChapter
import com.raja.bingehwa.data.remote.toSeries
import com.raja.bingehwa.data.remote.MdChapter
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappingTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test fun mangaMapsTitleCoverAndTags() {
        val raw = """
        {"result":"ok","data":[{"id":"abc","type":"manga","attributes":{
          "title":{"ja-ro":"Kage"},"altTitles":[{"en":"Shadow"}],
          "description":{"en":"A tale."},"status":"ongoing","year":2020,
          "links":{"engtl":"https://official.example/shadow"},
          "tags":[{"id":"t1","attributes":{"name":{"en":"Fantasy"},"group":"genre"}},
                  {"id":"t2","attributes":{"name":{"en":"Long Strip"},"group":"format"}}]},
          "relationships":[{"id":"c1","type":"cover_art","attributes":{"fileName":"x.jpg"}},
                           {"id":"a1","type":"author","attributes":{"name":"Someone"}}]}],"total":1}
        """.trimIndent()
        val s = json.decodeFromString<MdList<MdManga>>(raw).data.single().toSeries()
        assertEquals("Shadow", s.title)
        assertEquals("A tale.", s.description)
        assertEquals(listOf("Fantasy"), s.tags)
        assertEquals("https://uploads.mangadex.org/covers/abc/x.jpg.512.jpg", s.coverUrl)
        assertEquals("https://official.example/shadow", s.officialUrl)
    }

    @Test fun chapterWithoutNumberBecomesOneshot() {
        val raw = """{"id":"c","attributes":{"chapter":null,"pages":12,"publishAt":"2026-01-01T00:00:00+00:00"}}"""
        val c = json.decodeFromString<MdChapter>(raw).toChapter("s")
        assertEquals("Oneshot", c.number)
        assertEquals(12, c.pages)
        assertNull(c.externalUrl)
    }

    @Test fun pageUrlsRespectDataSaver() {
        val raw = """{"baseUrl":"https://h.example","chapter":{"hash":"H","data":["1.png"],"dataSaver":["1.jpg"]}}"""
        val a = json.decodeFromString<MdAtHome>(raw)
        assertEquals(listOf("https://h.example/data/H/1.png"), a.pageUrls(false))
        assertEquals(listOf("https://h.example/data-saver/H/1.jpg"), a.pageUrls(true))
    }
}
