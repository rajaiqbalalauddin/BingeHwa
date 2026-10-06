package com.raja.bingehwa.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raja.bingehwa.BingeApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Downloads one chapter's pages into app storage. A worker (not a coroutine in a screen) is
 * used so downloads survive the app closing and wait for Wi-Fi when the reader asked for that.
 */
class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val c = (applicationContext as BingeApp).container
        val chapterId = inputData.getString(KEY_CHAPTER) ?: return Result.failure()
        val dao = c.db.downloads()
        val entity = dao.get(chapterId) ?: return Result.failure()
        dao.upsert(entity.copy(state = "RUNNING"))
        return try {
            val urls = c.api.getPageUrls(chapterId, c.settings.current.dataSaver)
            val dir = File(applicationContext.filesDir, "downloads/$chapterId").apply { mkdirs() }
            var bytes = 0L
            urls.forEachIndexed { i, url ->
                val file = File(dir, "%04d.img".format(i))
                bytes += if (file.exists() && file.length() > 0) file.length() else fetchTo(c.imageClient, url, file)
                dao.upsert(entity.copy(state = "RUNNING", pageCount = urls.size, downloaded = i + 1, bytes = bytes))
            }
            dao.upsert(entity.copy(state = "DONE", pageCount = urls.size, downloaded = urls.size, bytes = bytes))
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val giveUp = runAttemptCount >= 2
            dao.upsert(entity.copy(state = if (giveUp) "FAILED" else "QUEUED"))
            if (giveUp) Result.failure() else Result.retry()
        }
    }

    /** Writes to a temp file first so a dropped connection never leaves a half image behind. */
    private suspend fun fetchTo(client: okhttp3.OkHttpClient, url: String, target: File): Long =
        withContext(Dispatchers.IO) {
            val tmp = File(target.parentFile, target.name + ".part")
            client.newCall(Request.Builder().url(url).build()).execute().use { res ->
                if (!res.isSuccessful) throw IOException("Image answered ${res.code}")
                val body = res.body ?: throw IOException("Empty image")
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            if (!tmp.renameTo(target)) throw IOException("Could not save image")
            target.length()
        }

    companion object {
        const val KEY_CHAPTER = "chapterId"
    }
}
