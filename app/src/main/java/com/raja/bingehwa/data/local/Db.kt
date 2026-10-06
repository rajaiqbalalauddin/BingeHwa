package com.raja.bingehwa.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A series the reader follows. Title and cover are copied in so the shelf works offline. */
@Entity(tableName = "library")
data class LibraryEntity(
    @PrimaryKey val seriesId: String,
    val title: String,
    val coverUrl: String?,
    /** Tags joined with "|" (Room cannot store lists without a converter, and this is simpler). */
    val tags: String,
    val addedAt: Long,
    val alertsOn: Boolean,
    val lastReadChapterId: String?,
    val lastReadNumber: String?,
    val lastReadAt: Long,
    val officialUrl: String?,
) {
    val tagList: List<String> get() = if (tags.isBlank()) emptyList() else tags.split("|")
}

/** Where the reader stopped in each chapter, and whether it was finished. */
@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val chapterId: String,
    val seriesId: String,
    val number: String,
    val page: Int,
    val pageCount: Int,
    val done: Boolean,
    val updatedAt: Long,
)

/** State: QUEUED, RUNNING, DONE or FAILED. */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val chapterId: String,
    val seriesId: String,
    val seriesTitle: String,
    val number: String,
    val title: String?,
    val pageCount: Int,
    val downloaded: Int,
    val bytes: Long,
    val state: String,
    val createdAt: Long,
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library ORDER BY lastReadAt DESC, addedAt DESC")
    fun observeAll(): Flow<List<LibraryEntity>>

    @Query("SELECT * FROM library")
    suspend fun getAll(): List<LibraryEntity>

    @Query("SELECT * FROM library WHERE seriesId = :id")
    fun observe(id: String): Flow<LibraryEntity?>

    @Query("SELECT * FROM library WHERE seriesId = :id")
    suspend fun get(id: String): LibraryEntity?

    @Upsert
    suspend fun upsert(entity: LibraryEntity)

    @Query("DELETE FROM library WHERE seriesId = :id")
    suspend fun delete(id: String)

    @Query("UPDATE library SET alertsOn = :on WHERE seriesId = :id")
    suspend fun setAlerts(id: String, on: Boolean)

    @Query("UPDATE library SET lastReadChapterId = :chapterId, lastReadNumber = :number, lastReadAt = :at WHERE seriesId = :id")
    suspend fun setLastRead(id: String, chapterId: String, number: String, at: Long)
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE seriesId = :seriesId")
    fun observeForSeries(seriesId: String): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress WHERE chapterId = :chapterId")
    suspend fun get(chapterId: String): ProgressEntity?

    @Upsert
    suspend fun upsert(entity: ProgressEntity)

    @Query("SELECT * FROM progress ORDER BY updatedAt DESC LIMIT 1")
    fun observeLatest(): Flow<ProgressEntity?>

    @Query("SELECT COUNT(*) FROM progress WHERE done = 1")
    fun observeDoneCount(): Flow<Int>

    @Query("SELECT * FROM progress WHERE updatedAt >= :since")
    fun observeSince(since: Long): Flow<List<ProgressEntity>>
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE seriesId = :seriesId")
    fun observeForSeries(seriesId: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE chapterId = :chapterId")
    suspend fun get(chapterId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE seriesId = :seriesId AND state = 'DONE'")
    suspend fun doneForSeries(seriesId: String): List<DownloadEntity>

    @Upsert
    suspend fun upsert(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE chapterId = :chapterId")
    suspend fun delete(chapterId: String)

    @Query("SELECT COALESCE(SUM(bytes), 0) FROM downloads WHERE state = 'DONE'")
    fun observeTotalBytes(): Flow<Long>

    @Query("SELECT * FROM downloads WHERE state = 'DONE'")
    suspend fun allDone(): List<DownloadEntity>
}

@Database(
    entities = [LibraryEntity::class, ProgressEntity::class, DownloadEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun library(): LibraryDao
    abstract fun progress(): ProgressDao
    abstract fun downloads(): DownloadDao
}
