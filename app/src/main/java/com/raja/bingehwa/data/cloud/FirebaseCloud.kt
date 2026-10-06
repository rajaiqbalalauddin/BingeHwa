package com.raja.bingehwa.data.cloud

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/**
 * Firebase-backed community layer. Data shapes here must match firestore.rules exactly,
 * which is why the field names are written out by hand instead of using model classes.
 */
class FirebaseCloud(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance(),
    private val nameProvider: () -> String,
) : Cloud {
    override val available = true

    override suspend fun uid(): String? {
        val existing = auth.currentUser
        if (existing != null) return existing.uid
        return auth.signInAnonymously().await().user?.uid
    }

    private suspend fun requireUid(): String = uid() ?: error("Sign-in failed")

    private fun displayName(): String = nameProvider().ifBlank { "reader" }.take(30)

    override suspend fun setDisplayName(name: String) {
        val user = auth.currentUser ?: return
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.take(30)).build()).await()
    }

    override suspend fun watchSeries(seriesId: String, title: String, on: Boolean) {
        val topic = "series_$seriesId"
        requireUid()
        val ref = db.collection("watched").document(seriesId)
        db.runTransaction<Any?> { tx ->
            val snap = tx.get(ref)
            val current = snap.getLong("subscribers") ?: 0L
            val next = if (on) current + 1 else maxOf(0L, current - 1)
            if (snap.exists()) {
                tx.update(ref, mapOf("subscribers" to next))
            } else if (on) {
                tx.set(ref, mapOf("title" to title.take(120), "subscribers" to 1L))
            }
            null
        }.await()
        if (on) messaging.subscribeToTopic(topic).await() else messaging.unsubscribeFromTopic(topic).await()
    }

    override suspend fun ratingSummary(seriesId: String): RatingSummary {
        val snap = db.collection("series").document(seriesId).get().await()
        val count = snap.getLong("ratingCount") ?: 0L
        val sum = snap.getLong("ratingSum") ?: 0L
        return RatingSummary(count, if (count > 0) sum.toDouble() / count else null)
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun myRating(seriesId: String): RatingInput? {
        val uid = requireUid()
        val d = db.collection("series").document(seriesId).collection("ratings").document(uid).get().await()
        if (!d.exists()) return null
        return RatingInput(
            stars = (d.getLong("stars") ?: 0L).toInt(),
            tags = (d.get("tags") as? List<String>).orEmpty(),
            review = d.getString("review").orEmpty(),
            isPublic = d.getBoolean("public") ?: true,
        )
    }

    override suspend fun submitRating(seriesId: String, input: RatingInput) {
        val uid = requireUid()
        val sref = db.collection("series").document(seriesId)
        val rref = sref.collection("ratings").document(uid)
        val name = displayName()
        db.runTransaction<Any?> { tx ->
            // Reads first, writes after: Firestore transactions require that order.
            val old = tx.get(rref)
            val series = tx.get(sref)
            val oldStars = old.getLong("stars") ?: 0L
            val count = series.getLong("ratingCount") ?: 0L
            val sum = series.getLong("ratingSum") ?: 0L
            val newCount = if (old.exists()) count else count + 1
            tx.set(
                rref,
                mapOf(
                    "uid" to uid,
                    "name" to name,
                    "stars" to input.stars.toLong(),
                    "tags" to input.tags.take(6),
                    "review" to input.review.take(400),
                    "public" to input.isPublic,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            tx.set(
                sref,
                mapOf("ratingCount" to newCount, "ratingSum" to (sum - oldStars + input.stars)),
                SetOptions.merge(),
            )
            null
        }.await()
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun publicReviews(seriesId: String, limit: Int): List<Review> {
        val q = db.collection("series").document(seriesId).collection("ratings")
            .whereEqualTo("public", true).limit(limit.toLong()).get().await()
        return q.documents.mapNotNull { d ->
            val review = d.getString("review").orEmpty()
            if (review.isBlank()) return@mapNotNull null
            Review(
                uid = d.getString("uid").orEmpty(),
                name = d.getString("name").orEmpty(),
                stars = (d.getLong("stars") ?: 0L).toInt(),
                tags = (d.get("tags") as? List<String>).orEmpty(),
                review = review,
            )
        }
    }

    private fun scopeKey(seriesId: String, chapterId: String?) =
        if (chapterId == null) "s:$seriesId" else "c:$chapterId"

    override suspend fun comments(seriesId: String, chapterId: String?, sort: CommentSort): List<CommentItem> {
        val uid = requireUid()
        // Equality filters only: no composite index to create. Sorting happens here instead.
        val snap = db.collection("comments")
            .whereEqualTo("scope", scopeKey(seriesId, chapterId))
            .whereEqualTo("parentId", "")
            .limit(150).get().await()
        return loadItems(snap.documents, uid, sort)
    }

    override suspend fun replies(parentId: String): List<CommentItem> {
        val uid = requireUid()
        val snap = db.collection("comments").whereEqualTo("parentId", parentId).limit(100).get().await()
        return loadItems(snap.documents, uid, CommentSort.NEWEST).sortedBy { it.createdAtMs }
    }

    private suspend fun loadItems(
        docs: List<com.google.firebase.firestore.DocumentSnapshot>,
        uid: String,
        sort: CommentSort,
    ): List<CommentItem> = coroutineScope {
        val sorted = when (sort) {
            CommentSort.TOP -> docs.sortedByDescending { it.getLong("likeCount") ?: 0L }
            CommentSort.NEWEST -> docs.sortedByDescending { it.getTimestamp("createdAt")?.toDate()?.time ?: 0L }
        }.take(60)
        sorted.map { d ->
            async {
                val liked = runCatching { db.collection("comments").document(d.id).collection("likes").document(uid).get().await().exists() }
                    .getOrDefault(false)
                CommentItem(
                    id = d.id,
                    uid = d.getString("uid").orEmpty(),
                    name = d.getString("name").orEmpty(),
                    text = d.getString("text").orEmpty(),
                    spoiler = d.getBoolean("spoiler") ?: false,
                    likeCount = d.getLong("likeCount") ?: 0L,
                    replyCount = d.getLong("replyCount") ?: 0L,
                    createdAtMs = d.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis(),
                    likedByMe = liked,
                    mine = d.getString("uid") == uid,
                )
            }
        }.map { it.await() }
    }

    override suspend fun postComment(seriesId: String, chapterId: String?, parentId: String?, text: String, spoiler: Boolean) {
        val uid = requireUid()
        val clean = text.trim().take(1000)
        require(clean.isNotEmpty()) { "Write something first" }
        val ref = db.collection("comments").document()
        val batch = db.batch()
        batch.set(
            ref,
            mapOf(
                "scope" to scopeKey(seriesId, chapterId),
                "seriesId" to seriesId,
                "chapterId" to (chapterId ?: ""),
                "parentId" to (parentId ?: ""),
                "uid" to uid,
                "name" to displayName(),
                "text" to clean,
                "spoiler" to spoiler,
                "likeCount" to 0L,
                "replyCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        )
        if (!parentId.isNullOrEmpty()) {
            batch.update(db.collection("comments").document(parentId), "replyCount", FieldValue.increment(1))
        }
        batch.commit().await()
    }

    override suspend fun toggleLike(commentId: String, currentlyLiked: Boolean) {
        val uid = requireUid()
        val cref = db.collection("comments").document(commentId)
        val lref = cref.collection("likes").document(uid)
        val batch = db.batch()
        if (currentlyLiked) {
            batch.delete(lref)
            batch.update(cref, "likeCount", FieldValue.increment(-1))
        } else {
            batch.set(lref, mapOf("at" to FieldValue.serverTimestamp()))
            batch.update(cref, "likeCount", FieldValue.increment(1))
        }
        batch.commit().await()
    }

    override suspend fun deleteComment(comment: CommentItem, parentId: String?) {
        requireUid()
        db.collection("comments").document(comment.id).delete().await()
    }

    override suspend fun report(commentId: String, reason: String) {
        val uid = requireUid()
        db.collection("reports").document("${commentId}_$uid").set(
            mapOf("commentId" to commentId, "uid" to uid, "reason" to reason.take(200), "createdAt" to FieldValue.serverTimestamp()),
        ).await()
    }
}
