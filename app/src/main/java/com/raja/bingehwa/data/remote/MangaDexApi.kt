package com.raja.bingehwa.data.remote

import com.raja.bingehwa.data.model.Chapter
import com.raja.bingehwa.data.model.Series
import com.raja.bingehwa.data.model.SeriesStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Thrown for any non-2xx answer so the UI can show a clear message instead of crashing. */
class ApiException(val code: Int, message: String) : IOException(message)

/**
 * Why this exists: MangaDex asks clients to stay near 5 requests per second and answers 429
 * when they do not. Spacing calls here keeps every caller polite without each one knowing.
 */
class PoliteInterceptor(private val minGapMs: Long = 230) : Interceptor {
    private val lock = Any()
    private var last = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        waitTurn()
        val first = chain.proceed(chain.request())
        if (first.code != 429) return first
        // One gentle retry after the server told us to slow down.
        val waitSec = first.header("Retry-After")?.toLongOrNull() ?: 2L
        first.close()
        Thread.sleep((waitSec.coerceIn(1L, 5L)) * 1000)
        waitTurn()
        return chain.proceed(chain.request())
    }

    private fun waitTurn() {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val wait = last + minGapMs - now
            if (wait > 0) Thread.sleep(wait)
            last = System.currentTimeMillis()
        }
    }
}

class MangaDexApi(
    private val http: OkHttpClient = defaultClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    companion object {
        const val BASE = "https://api.mangadex.org"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .addInterceptor(PoliteInterceptor())
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", "BingeHwa/0.1 (personal reader)").build())
            }
            .build()
    }

    private suspend fun fetch(url: HttpUrl): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw ApiException(res.code, "MangaDex answered ${res.code}")
            body
        }
    }

    private fun url(path: String, build: HttpUrl.Builder.() -> Unit = {}): HttpUrl =
        (BASE + path).toHttpUrl().newBuilder().apply(build).build()

    private fun HttpUrl.Builder.safeContent() {
        addQueryParameter("contentRating[]", "safe")
        addQueryParameter("contentRating[]", "suggestive")
    }

    /** Search or browse. [order] is a MangaDex sort key such as followedCount, rating, latestUploadedChapter. */
    suspend fun searchManga(
        title: String? = null,
        includedTagIds: List<String> = emptyList(),
        order: String = "followedCount",
        limit: Int = 20,
        offset: Int = 0,
        lang: String = "en",
    ): List<Series> {
        val u = url("/manga") {
            if (!title.isNullOrBlank()) addQueryParameter("title", title)
            includedTagIds.forEach { addQueryParameter("includedTags[]", it) }
            addQueryParameter("limit", limit.toString())
            addQueryParameter("offset", offset.toString())
            addQueryParameter("includes[]", "cover_art")
            addQueryParameter("availableTranslatedLanguage[]", lang)
            addQueryParameter("hasAvailableChapters", "true")
            safeContent()
            val key = if (!title.isNullOrBlank() && order == "followedCount") "relevance" else order
            addQueryParameter("order[$key]", "desc")
        }
        return json.decodeFromString<MdList<MdManga>>(fetch(u)).data.map { it.toSeries() }
    }

    suspend fun getManga(id: String): Series {
        val u = url("/manga/$id") { addQueryParameter("includes[]", "cover_art") }
        return json.decodeFromString<MdOne<MdManga>>(fetch(u)).data.toSeries()
    }

    /** Fetches up to [maxPages] x 100 chapters, enough for almost every series. */
    suspend fun getChapters(seriesId: String, lang: String = "en", maxPages: Int = 6): List<Chapter> {
        val out = ArrayList<Chapter>()
        var offset = 0
        repeat(maxPages) {
            val u = url("/manga/$seriesId/feed") {
                addQueryParameter("translatedLanguage[]", lang)
                addQueryParameter("order[chapter]", "desc")
                addQueryParameter("limit", "100")
                addQueryParameter("offset", offset.toString())
                safeContent()
            }
            val page = json.decodeFromString<MdList<MdChapter>>(fetch(u))
            out += page.data.map { it.toChapter(seriesId) }
            offset += 100
            if (offset >= page.total || page.data.isEmpty()) return out
        }
        return out
    }

    /** Just the newest few chapters, used by the Updates feed so it stays quick. */
    suspend fun getLatestChapters(seriesId: String, lang: String = "en", count: Int = 3): List<Chapter> {
        val u = url("/manga/$seriesId/feed") {
            addQueryParameter("translatedLanguage[]", lang)
            addQueryParameter("order[chapter]", "desc")
            addQueryParameter("limit", count.toString())
            safeContent()
        }
        return json.decodeFromString<MdList<MdChapter>>(fetch(u)).data.map { it.toChapter(seriesId) }
    }

    suspend fun getPageUrls(chapterId: String, dataSaver: Boolean): List<String> {
        val u = url("/at-home/server/$chapterId")
        return json.decodeFromString<MdAtHome>(fetch(u)).pageUrls(dataSaver)
    }

    suspend fun getStats(ids: List<String>): Map<String, SeriesStats> {
        if (ids.isEmpty()) return emptyMap()
        val u = url("/statistics/manga") { ids.forEach { addQueryParameter("manga[]", it) } }
        return json.decodeFromString<MdStatsResponse>(fetch(u)).statistics
            .mapValues { SeriesStats(it.value.rating?.average ?: it.value.rating?.bayesian, it.value.follows) }
    }

    /** Genre and theme tags with their ids, needed to filter searches by tag. */
    suspend fun getTags(): Map<String, String> {
        val list = json.decodeFromString<MdList<MdTag>>(fetch(url("/manga/tag"))).data
        return list
            .filter { it.attributes.group == "genre" || it.attributes.group == "theme" }
            .mapNotNull { t -> t.attributes.name["en"]?.let { it to t.id } }
            .toMap()
    }
}
