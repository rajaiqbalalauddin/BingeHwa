package com.raja.bingehwa.data.cloud

/** What the community screens need from a backend. Two implementations: Firebase, or nothing. */
data class RatingInput(val stars: Int, val tags: List<String>, val review: String, val isPublic: Boolean)

data class RatingSummary(val count: Long, val average: Double?)

data class Review(val uid: String, val name: String, val stars: Int, val tags: List<String>, val review: String)

data class CommentItem(
    val id: String,
    val uid: String,
    val name: String,
    val text: String,
    val spoiler: Boolean,
    val likeCount: Long,
    val replyCount: Long,
    val createdAtMs: Long,
    val likedByMe: Boolean,
    val mine: Boolean,
)

enum class CommentSort { TOP, NEWEST }

interface Cloud {
    /** False when google-services.json is missing, so screens can explain why nothing loads. */
    val available: Boolean

    suspend fun uid(): String?
    suspend fun setDisplayName(name: String)

    /** Subscribes this phone to a series' push topic and keeps the "watchers" counter the worker reads. */
    suspend fun watchSeries(seriesId: String, title: String, on: Boolean)

    suspend fun ratingSummary(seriesId: String): RatingSummary
    suspend fun myRating(seriesId: String): RatingInput?
    suspend fun submitRating(seriesId: String, input: RatingInput)
    suspend fun publicReviews(seriesId: String, limit: Int = 20): List<Review>

    /** [chapterId] null means the series-wide thread. */
    suspend fun comments(seriesId: String, chapterId: String?, sort: CommentSort): List<CommentItem>
    suspend fun replies(parentId: String): List<CommentItem>
    suspend fun postComment(seriesId: String, chapterId: String?, parentId: String?, text: String, spoiler: Boolean)
    suspend fun toggleLike(commentId: String, currentlyLiked: Boolean)
    suspend fun deleteComment(comment: CommentItem, parentId: String?)
    suspend fun report(commentId: String, reason: String)
}

/** Thrown by [NoCloud] so the UI shows one clear message instead of an empty screen. */
class CloudUnavailable : Exception("Community features are not set up on this build.")

/** Used when Firebase is not configured. Reading still works; every community call explains itself. */
class NoCloud : Cloud {
    override val available = false
    override suspend fun uid(): String? = null
    override suspend fun setDisplayName(name: String) = Unit
    override suspend fun watchSeries(seriesId: String, title: String, on: Boolean) = Unit
    override suspend fun ratingSummary(seriesId: String) = RatingSummary(0, null)
    override suspend fun myRating(seriesId: String): RatingInput? = null
    override suspend fun submitRating(seriesId: String, input: RatingInput) = throw CloudUnavailable()
    override suspend fun publicReviews(seriesId: String, limit: Int) = emptyList<Review>()
    override suspend fun comments(seriesId: String, chapterId: String?, sort: CommentSort) = throw CloudUnavailable()
    override suspend fun replies(parentId: String) = emptyList<CommentItem>()
    override suspend fun postComment(seriesId: String, chapterId: String?, parentId: String?, text: String, spoiler: Boolean) = throw CloudUnavailable()
    override suspend fun toggleLike(commentId: String, currentlyLiked: Boolean) = throw CloudUnavailable()
    override suspend fun deleteComment(comment: CommentItem, parentId: String?) = throw CloudUnavailable()
    override suspend fun report(commentId: String, reason: String) = throw CloudUnavailable()
}
