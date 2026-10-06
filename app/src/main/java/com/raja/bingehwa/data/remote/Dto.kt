package com.raja.bingehwa.data.remote

import kotlinx.serialization.Serializable

// Shapes of the MangaDex JSON we read. Only the fields we use are listed; the rest is ignored.

@Serializable
data class MdList<T>(val data: List<T> = emptyList(), val total: Int = 0)

@Serializable
data class MdOne<T>(val data: T)

@Serializable
data class MdManga(
    val id: String,
    val attributes: MdMangaAttr,
    val relationships: List<MdRel> = emptyList(),
)

@Serializable
data class MdMangaAttr(
    val title: Map<String, String> = emptyMap(),
    val altTitles: List<Map<String, String>> = emptyList(),
    val description: Map<String, String> = emptyMap(),
    val status: String? = null,
    val year: Int? = null,
    val originalLanguage: String? = null,
    val contentRating: String? = null,
    val tags: List<MdTag> = emptyList(),
    val links: Map<String, String>? = null,
)

@Serializable
data class MdTag(val id: String, val attributes: MdTagAttr)

@Serializable
data class MdTagAttr(val name: Map<String, String> = emptyMap(), val group: String? = null)

@Serializable
data class MdRel(val id: String, val type: String, val attributes: MdRelAttr? = null)

@Serializable
data class MdRelAttr(val fileName: String? = null)

@Serializable
data class MdChapter(
    val id: String,
    val attributes: MdChapterAttr,
    val relationships: List<MdRel> = emptyList(),
)

@Serializable
data class MdChapterAttr(
    val chapter: String? = null,
    val title: String? = null,
    val pages: Int = 0,
    val translatedLanguage: String? = null,
    val publishAt: String = "",
    val externalUrl: String? = null,
)

@Serializable
data class MdAtHome(val baseUrl: String, val chapter: MdAtHomeChapter)

@Serializable
data class MdAtHomeChapter(
    val hash: String,
    val data: List<String> = emptyList(),
    val dataSaver: List<String> = emptyList(),
)

@Serializable
data class MdStatsResponse(val statistics: Map<String, MdStat> = emptyMap())

@Serializable
data class MdStat(val rating: MdRating? = null, val follows: Int? = null)

@Serializable
data class MdRating(val average: Double? = null, val bayesian: Double? = null)
