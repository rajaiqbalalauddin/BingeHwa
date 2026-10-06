package com.raja.bingehwa.data.repo

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.raja.bingehwa.data.cloud.Cloud
import com.raja.bingehwa.data.local.AppDb
import com.raja.bingehwa.data.local.DownloadEntity
import com.raja.bingehwa.data.local.LibraryEntity
import com.raja.bingehwa.data.local.ProgressEntity
import com.raja.bingehwa.data.model.Chapter
import com.raja.bingehwa.data.model.PageSource
import com.raja.bingehwa.data.model.Series
import com.raja.bingehwa.data.model.SeriesStats
import com.raja.bingehwa.data.prefs.Settings
import com.raja.bingehwa.data.remote.MangaDexApi
import com.raja.bingehwa.domain.ChapterUtil
import com.raja.bingehwa.domain.Recommender
import com.raja.bingehwa.download.DownloadWorker
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import kotlin.random.Random

/** One row of the Updates feed. */
data class FeedItem(val series: LibraryEntity, val chapter: Chapter, val unread: Boolean)

data class DiscoverData(
    val matches: List<Recommender.Scored>,
    val trending: List<Series>,
    val topRated: List<Pair<Series, SeriesStats?>>,
)

/**
 * The single door between screens and data. Screens never touch the API, database or cloud
 * directly, so offline fallbacks and error handling live in one place.
 */
class Repo(
    private val api: MangaDexApi,
    private val db: AppDb,
    val settings: Settings,
    val cloud: Cloud,
    private val anilist: AniListClient,
    private val context: Context,
) {
    private data class Cached(val at: Long, val list: List<Chapter>)

    private val chapterCache = HashMap<String, Cached>()
    private var tagIds: Map<String, String>? = null

    // ---- library -------------------------------------------------------------------------

    fun observeLibrary(): Flow<List<LibraryEntity>> = db.library().observeAll()
    fun observeLibraryEntry(id: String): Flow<LibraryEntity?> = db.library().observe(id)

    suspend fun follow(series: Series) {
        if (db.library().get(series.id) != null) return
        db.library().upsert(
            LibraryEntity(
                seriesId = series.id,
                title = series.title,
                coverUrl = series.coverUrl,
                tags = series.tags.joinToString("|"),
                addedAt = System.currentTimeMillis(),
                alertsOn = false,
                lastReadChapterId = null,
                lastReadNumber = null,
                lastReadAt = 0L,
                officialUrl = series.officialUrl,
            ),
        )
    }

    suspend fun unfollow(seriesId: String) {
        val entry = db.library().get(seriesId)
        if (entry?.alertsOn == true) runCatching { cloud.watchSeries(seriesId, entry.title, false) }
        db.library().delete(seriesId)
    }

    /** Cloud first, database second, so a failed subscription never leaves the switch lying. */
    suspend fun setAlerts(seriesId: String, on: Boolean) {
        val entry = db.library().get(seriesId) ?: return
        if (entry.alertsOn == on) return
        cloud.watchSeries(seriesId, entry.title, on)
        db.library().setAlerts(seriesId, on)
    }

    // ---- series and chapters --------------------------------------------------------------

    suspend fun series(id: String): Series = try {
        api.getManga(id)
    } catch (e: Exception) {
        val local = db.library().get(id) ?: throw e
        Series(local.seriesId, local.title, "", local.coverUrl, local.tagList, "unknown", null, local.officialUrl)
    }

    suspend fun search(query: String): List<Series> = api.searchManga(title = query, lang = settings.current.chapterLang)

    suspend fun chapters(seriesId: String, force: Boolean = false): List<Chapter> {
        val now = System.currentTimeMillis()
        val hit = chapterCache[seriesId]
        if (!force && hit != null && now - hit.at < 5 * 60_000) return hit.list
        return try {
            val fresh = ChapterUtil.prepare(api.getChapters(seriesId, settings.current.chapterLang))
            chapterCache[seriesId] = Cached(now, fresh)
            fresh
        } catch (e: Exception) {
            // Offline: show whatever was downloaded rather than an error wall.
            val offline = db.downloads().doneForSeries(seriesId).map {
                Chapter(it.chapterId, it.seriesId, it.number, it.title, it.pageCount, "", null)
            }
            if (offline.isEmpty()) throw e
            ChapterUtil.prepare(offline)
        }
    }

    fun observeProgress(seriesId: String): Flow<List<ProgressEntity>> = db.progress().observeForSeries(seriesId)
    suspend fun progressFor(chapterId: String): ProgressEntity? = db.progress().get(chapterId)

    /** Records the page position; marks the chapter done near the end and moves the "last read" marker forward. */
    suspend fun saveProgress(seriesId: String, chapter: Chapter, page: Int, pageCount: Int) {
        val done = pageCount > 0 && page >= pageCount - 1
        val now = System.currentTimeMillis()
        val previous = db.progress().get(chapter.id)
        db.progress().upsert(
            ProgressEntity(chapter.id, seriesId, chapter.number, page, pageCount, done || previous?.done == true, now),
        )
        val entry = db.library().get(seriesId) ?: return
        val newValue = ChapterUtil.numberValue(chapter.number)
        val oldValue = entry.lastReadNumber?.let { ChapterUtil.numberValue(it) }
        val moveForward = entry.lastReadChapterId == null || newValue == null || oldValue == null || newValue >= oldValue
        // "Last read" only moves when a chapter is finished, so Continue never skips an unfinished one.
        if (done && moveForward) {
            db.library().setLastRead(seriesId, chapter.id, chapter.number, now)
        }
        if (done && settings.current.deleteAfterRead) deleteDownload(chapter.id)
    }

    fun observeLatestProgress(): Flow<ProgressEntity?> = db.progress().observeLatest()
    fun observeDoneCount(): Flow<Int> = db.progress().observeDoneCount()
    fun observeProgressSince(since: Long): Flow<List<ProgressEntity>> = db.progress().observeSince(since)

    // ---- reading ----------------------------------------------------------------------------

    /** Uses the downloaded copy when it exists, otherwise asks MangaDex for fresh image URLs. */
    suspend fun pages(chapter: Chapter): List<PageSource> {
        val dl = db.downloads().get(chapter.id)
        if (dl?.state == "DONE") {
            val dir = File(context.filesDir, "downloads/${chapter.id}")
            val files = dir.listFiles()?.sortedBy { it.name }.orEmpty()
            if (files.isNotEmpty()) return files.map { PageSource.Local(it.absolutePath) }
        }
        return api.getPageUrls(chapter.id, settings.current.dataSaver).map { PageSource.Remote(it) }
    }

    // ---- updates feed -----------------------------------------------------------------------

    suspend fun feed(): List<FeedItem> = coroutineScope {
        val lib = db.library().getAll().take(15)
        val gate = Semaphore(3)
        lib.map { entry ->
            async {
                gate.withPermit {
                    runCatching { api.getLatestChapters(entry.seriesId, settings.current.chapterLang, 3) }
                        .getOrDefault(emptyList())
                        .map { c ->
                            val read = entry.lastReadNumber?.let { ChapterUtil.numberValue(it) }
                            val n = ChapterUtil.numberValue(c.number)
                            FeedItem(entry, c, unread = read == null || (n != null && n > read))
                        }
                }
            }
        }.flatMap { it.await() }
            .sortedByDescending { it.chapter.publishAt }
            .take(60)
    }

    // ---- discover ---------------------------------------------------------------------------

    private suspend fun tagMap(): Map<String, String> =
        tagIds ?: api.getTags().also { tagIds = it }

    suspend fun discover(): DiscoverData = coroutineScope {
        val lang = settings.current.chapterLang
        val lib = db.library().getAll()
        val picks = settings.current.tastePicks
        val owned = lib.map { Recommender.Owned(it.title, it.tagList) }
        val profile = Recommender.profile(owned, picks)
        val topTags = profile.entries.sortedByDescending { it.value }.take(4).map { it.key }

        val trendingJob = async { runCatching { api.searchManga(lang = lang, limit = 12) }.getOrDefault(emptyList()) }
        val topJob = async { runCatching { api.searchManga(order = "rating", lang = lang, limit = 12) }.getOrDefault(emptyList()) }
        val candidateJob = async {
            val ids = runCatching { tagMap() }.getOrDefault(emptyMap())
            topTags.mapNotNull { ids[it] }.map { id ->
                async { runCatching { api.searchManga(includedTagIds = listOf(id), lang = lang, limit = 20) }.getOrDefault(emptyList()) }
            }.flatMap { it.await() }
        }
        val trending = trendingJob.await()
        val top = topJob.await()
        val candidates = candidateJob.await().ifEmpty { trending }
        val stats = runCatching { api.getStats(top.map { it.id }) }.getOrDefault(emptyMap())
        DiscoverData(
            matches = Recommender.rank(candidates + trending, owned, lib.map { it.seriesId }.toSet(), picks),
            trending = trending,
            topRated = top.map { it to stats[it.id] },
        )
    }

    /** "Surprise me": a random pick from the first few hundred popular series. */
    suspend fun surprise(): Series? {
        val lang = settings.current.chapterLang
        val owned = db.library().getAll().map { it.seriesId }.toSet()
        repeat(2) {
            val pick = api.searchManga(lang = lang, limit = 10, offset = Random.nextInt(0, 300))
                .filter { it.id !in owned }.randomOrNull()
            if (pick != null) return pick
        }
        return api.searchManga(lang = lang, limit = 10).randomOrNull()
    }

    suspend fun stats(ids: List<String>): Map<String, SeriesStats> = runCatching { api.getStats(ids) }.getOrDefault(emptyMap())

    // ---- downloads --------------------------------------------------------------------------

    fun observeDownloads(): Flow<List<DownloadEntity>> = db.downloads().observeAll()
    fun observeDownloadsFor(seriesId: String): Flow<List<DownloadEntity>> = db.downloads().observeForSeries(seriesId)
    fun observeDownloadBytes(): Flow<Long> = db.downloads().observeTotalBytes()

    suspend fun download(seriesTitle: String, chapter: Chapter) {
        if (!chapter.readableInApp) return
        val existing = db.downloads().get(chapter.id)
        if (existing?.state == "DONE" || existing?.state == "RUNNING") return
        db.downloads().upsert(
            DownloadEntity(
                chapterId = chapter.id, seriesId = chapter.seriesId, seriesTitle = seriesTitle,
                number = chapter.number, title = chapter.title, pageCount = chapter.pages,
                downloaded = 0, bytes = 0L, state = "QUEUED", createdAt = System.currentTimeMillis(),
            ),
        )
        val network = if (settings.current.wifiOnlyDownloads) NetworkType.UNMETERED else NetworkType.CONNECTED
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .setInputData(workDataOf(DownloadWorker.KEY_CHAPTER to chapter.id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("dl_${chapter.id}", ExistingWorkPolicy.KEEP, req)
    }

    suspend fun deleteDownload(chapterId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("dl_$chapterId")
        File(context.filesDir, "downloads/$chapterId").deleteRecursively()
        db.downloads().delete(chapterId)
    }

    suspend fun deleteAllDownloads() {
        WorkManager.getInstance(context).cancelAllWork()
        db.downloads().allDone().forEach { deleteDownload(it.chapterId) }
    }

    // ---- import -----------------------------------------------------------------------------

    data class ImportResult(val added: Int, val missed: List<String>)

    /** Matches AniList titles against MangaDex by search, adding the best hit to the library. */
    suspend fun importFromAniList(userName: String): ImportResult {
        val titles = anilist.readingTitles(userName.trim()).take(30)
        var added = 0
        val missed = ArrayList<String>()
        for (t in titles) {
            val hit = runCatching { api.searchManga(title = t, limit = 1, lang = settings.current.chapterLang) }.getOrDefault(emptyList()).firstOrNull()
            if (hit == null) missed += t else { follow(hit); added++ }
        }
        return ImportResult(added, missed)
    }
}
