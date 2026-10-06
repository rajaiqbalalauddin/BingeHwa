package com.raja.bingehwa.data.remote

import com.raja.bingehwa.data.model.Chapter
import com.raja.bingehwa.data.model.Series

/** Pure mapping functions, so DTO to model conversion is unit tested without any network. */

private fun Map<String, String>.bestText(): String =
    this["en"] ?: this.values.firstOrNull() ?: ""

fun MdManga.toSeries(): Series {
    val a = attributes
    val title = a.title["en"]
        ?: a.altTitles.firstNotNullOfOrNull { it["en"] }
        ?: a.title.values.firstOrNull()
        ?: "Untitled"
    val cover = relationships.firstOrNull { it.type == "cover_art" }?.attributes?.fileName
    val tags = a.tags
        .filter { it.attributes.group == "genre" || it.attributes.group == "theme" }
        .mapNotNull { it.attributes.name["en"] }
    return Series(
        id = id,
        title = title,
        description = a.description.bestText().trim(),
        coverUrl = cover?.let { "https://uploads.mangadex.org/covers/$id/$it.512.jpg" },
        tags = tags,
        status = a.status ?: "unknown",
        year = a.year,
        officialUrl = a.links?.get("engtl"),
    )
}

fun MdChapter.toChapter(seriesId: String): Chapter = Chapter(
    id = id,
    seriesId = seriesId,
    number = attributes.chapter?.takeIf { it.isNotBlank() } ?: "Oneshot",
    title = attributes.title?.takeIf { it.isNotBlank() },
    pages = attributes.pages,
    publishAt = attributes.publishAt,
    externalUrl = attributes.externalUrl?.takeIf { it.isNotBlank() },
)

/** Builds the page image URLs for one chapter from the "at-home" server answer. */
fun MdAtHome.pageUrls(dataSaver: Boolean): List<String> {
    val folder = if (dataSaver) "data-saver" else "data"
    val files = if (dataSaver && chapter.dataSaver.isNotEmpty()) chapter.dataSaver else chapter.data
    return files.map { "$baseUrl/$folder/${chapter.hash}/$it" }
}
