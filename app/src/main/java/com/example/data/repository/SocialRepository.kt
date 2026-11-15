package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.AppConstants
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.AbuseReport
import com.example.data.model.BlockEdge
import com.example.data.model.FollowEdge
import com.example.data.model.NotificationItem
import com.example.data.model.PostComment
import com.example.data.model.SavedMessage
import com.example.data.model.SocialPost
import com.example.data.model.Story
import com.example.data.model.StoryView
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

class SocialRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeActiveStories(currentUserId: String): Flow<List<Story>> = flow {
        val query = db.collection("stories")
            .whereEqualTo("isArchived", false)
            .limit(60)
        emitAll(
            query.snapshots()
                .map { snap ->
                    val now = Date()
                    snap.toObjects(
                        Story::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).filter { story ->
                        val notExpired = story.expiresAt == null || story.expiresAt.toDate().after(now)
                        val notHidden = currentUserId !in story.hiddenUids
                        val canSee = story.authorId == currentUserId ||
                            story.privacy == "public" ||
                            story.privacy == "followers" ||
                            currentUserId in story.allowedUids
                        notExpired && notHidden && canSee
                    }.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.LIST, "stories")
                    throw error
                }
        )
    }

    fun observeMyArchivedStories(userId: String): Flow<List<Story>> = flow {
        val query = db.collection("stories")
            .whereEqualTo("authorId", userId)
            .limit(50)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        Story::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.LIST, "stories")
                    throw error
                }
        )
    }

    fun observeStoryViewers(storyId: String): Flow<List<StoryView>> = flow {
        val query = db.collection("stories").document(storyId).collection("views")
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        StoryView::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.LIST, "stories/$storyId/views")
                    throw error
                }
        )
    }

    suspend fun createStory(
        authorName: String,
        authorAvatarUrl: String,
        caption: String,
        mediaType: String,
        mediaUrl: String,
        backgroundGradient: String,
        privacy: String,
        allowedUids: List<String>,
        hiddenUids: List<String>,
        highlightTitle: String
    ): Result<String> {
        val uid = requireUserId()
        val storyId = "story_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("stories").document(storyId)
        val expiresAt = Timestamp(Date(System.currentTimeMillis() + AppConstants.STORY_TTL_MILLIS))

        return try {
            docRef.set(
                mapOf(
                    "authorId" to uid,
                    "authorName" to authorName.ifEmpty { "User" }.take(80),
                    "authorAvatarUrl" to authorAvatarUrl.take(1000),
                    "mediaUrl" to mediaUrl.take(1000),
                    "mediaType" to mediaType,
                    "caption" to caption.trim().take(500),
                    "backgroundGradient" to backgroundGradient.take(64),
                    "privacy" to privacy,
                    "allowedUids" to allowedUids.take(200),
                    "hiddenUids" to hiddenUids.take(200),
                    "isArchived" to false,
                    "highlightTitle" to highlightTitle.trim().take(60),
                    "viewCount" to 0L,
                    "reactions" to emptyMap<String, List<String>>(),
                    "expiresAt" to expiresAt,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(storyId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun recordStoryView(story: Story, viewerName: String, reaction: String = "") {
        val uid = auth.currentUser?.uid ?: return
        val storyRef = db.collection("stories").document(story.id)
        val viewRef = storyRef.collection("views").document(uid)
        try {
            val existing = viewRef.get().await()
            viewRef.set(
                mapOf(
                    "viewerId" to uid,
                    "viewerName" to viewerName.ifEmpty { "User" }.take(80),
                    "storyAuthorId" to story.authorId,
                    "reaction" to reaction.take(16),
                    "viewedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
            if (!existing.exists() && story.authorId != uid) {
                storyRef.update(
                    mapOf(
                        "viewCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, viewRef.path)
        }
    }

    suspend fun archiveOrHighlightStory(storyId: String, archive: Boolean, highlightTitle: String): Result<Unit> {
        val storyRef = db.collection("stories").document(storyId)
        return try {
            storyRef.update(
                mapOf(
                    "isArchived" to archive,
                    "highlightTitle" to highlightTitle.trim().take(60),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, storyRef.path)
            Result.failure(e)
        }
    }

    // Social Feed Posts
    fun observePosts(): Flow<List<SocialPost>> = flow {
        val query = db.collection("posts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(40)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        SocialPost::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.LIST, "posts")
                    throw error
                }
        )
    }

    fun observePostComments(postId: String): Flow<List<PostComment>> = flow {
        val query = db.collection("posts").document(postId)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        PostComment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.LIST, "posts/$postId/comments")
                    throw error
                }
        )
    }

    suspend fun createPost(
        authorName: String,
        authorAvatarUrl: String,
        caption: String,
        mediaUrl: String,
        collectionTag: String
    ): Result<String> {
        val uid = requireUserId()
        val postId = "post_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val ref = db.collection("posts").document(postId)
        return try {
            ref.set(
                mapOf(
                    "authorId" to uid,
                    "authorName" to authorName.ifEmpty { "User" }.take(80),
                    "authorAvatarUrl" to authorAvatarUrl.take(1000),
                    "caption" to caption.trim().take(2000),
                    "mediaUrl" to mediaUrl.take(1000),
                    "collectionTag" to collectionTag.ifEmpty { "General" }.take(60),
                    "reactions" to emptyMap<String, List<String>>(),
                    "commentCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(postId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun togglePostLike(postId: String): Result<Unit> {
        val uid = requireUserId()
        val ref = db.collection("posts").document(postId)
        return try {
            db.runTransaction { tx ->
                val snap = tx.get(ref)
                val map = (snap.get("reactions") as? Map<*, *>)?.entries?.associate { (k, v) ->
                    k.toString() to ((v as? List<*>)?.mapNotNull { it?.toString() }?.toMutableList()
                        ?: mutableListOf())
                }?.toMutableMap() ?: mutableMapOf()
                val likes = map.getOrPut("❤️") { mutableListOf() }
                if (uid in likes) likes.remove(uid) else likes.add(uid)
                tx.update(
                    ref,
                    mapOf(
                        "reactions" to map,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun addPostComment(postId: String, authorName: String, text: String): Result<Unit> {
        val uid = requireUserId()
        val commentId = "cmt_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val postRef = db.collection("posts").document(postId)
        val commentRef = postRef.collection("comments").document(commentId)
        return try {
            val batch = db.batch()
            batch.set(
                commentRef,
                mapOf(
                    "postId" to postId,
                    "authorId" to uid,
                    "authorName" to authorName.ifEmpty { "User" }.take(80),
                    "text" to text.trim().take(1000),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                postRef,
                mapOf(
                    "commentCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, commentRef.path)
            Result.failure(e)
        }
    }

    // Follows, Blocks, Reports
    fun observeFollowing(userId: String): Flow<List<FollowEdge>> = flow {
        val query = db.collection("follows").whereEqualTo("followerId", userId)
        emitAll(
            query.snapshots()
                .map { it.toObjects(FollowEdge::class.java) }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.LIST, "follows")
                    throw error
                }
        )
    }

    fun observeFollowers(userId: String): Flow<List<FollowEdge>> = flow {
        val query = db.collection("follows").whereEqualTo("followedId", userId)
        emitAll(
            query.snapshots()
                .map { it.toObjects(FollowEdge::class.java) }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.LIST, "follows")
                    throw error
                }
        )
    }

    suspend fun toggleFollow(targetUserId: String, currentlyFollowing: Boolean): Result<Unit> {
        val uid = requireUserId()
        val docId = "${uid}_${targetUserId}"
        val ref = db.collection("follows").document(docId)
        return try {
            if (currentlyFollowing) {
                ref.delete().await()
            } else {
                ref.set(
                    mapOf(
                        "followerId" to uid,
                        "followedId" to targetUserId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    fun observeBlockedUsers(userId: String): Flow<List<BlockEdge>> = flow {
        val query = db.collection("blocks").whereEqualTo("blockerId", userId)
        emitAll(
            query.snapshots()
                .map { it.toObjects(BlockEdge::class.java) }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.LIST, "blocks")
                    throw error
                }
        )
    }

    suspend fun toggleBlockUser(targetUserId: String, currentlyBlocked: Boolean): Result<Unit> {
        val uid = requireUserId()
        val docId = "${uid}_${targetUserId}"
        val ref = db.collection("blocks").document(docId)
        return try {
            if (currentlyBlocked) {
                ref.delete().await()
            } else {
                ref.set(
                    mapOf(
                        "blockerId" to uid,
                        "blockedId" to targetUserId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun submitReport(
        targetType: String,
        targetId: String,
        reason: String,
        details: String
    ): Result<String> {
        val uid = requireUserId()
        val reportId = "rep_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val ref = db.collection("reports").document(reportId)
        return try {
            ref.set(
                mapOf(
                    "reporterId" to uid,
                    "targetType" to targetType,
                    "targetId" to targetId.take(128),
                    "reason" to reason.trim().take(120),
                    "details" to details.trim().take(1000),
                    "status" to "open",
                    "reviewedBy" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(reportId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    // Saved Messages & Notifications
    fun observeSavedMessages(userId: String): Flow<List<SavedMessage>> = flow {
        val query = db.collection("users").document(userId)
            .collection("savedMessages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        SavedMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.LIST, "users/$userId/savedMessages")
                    throw error
                }
        )
    }

    suspend fun saveMessageBookmark(
        content: String,
        sourceConversationId: String = "",
        sourceSenderName: String = "",
        mediaUrl: String = "",
        mediaType: String = "text"
    ): Result<Unit> {
        val uid = requireUserId()
        val itemId = "saved_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val ref = db.collection("users").document(uid).collection("savedMessages").document(itemId)
        return try {
            ref.set(
                mapOf(
                    "userId" to uid,
                    "content" to content.take(5000),
                    "sourceConversationId" to sourceConversationId.take(128),
                    "sourceSenderName" to sourceSenderName.take(80),
                    "mediaUrl" to mediaUrl.take(1000),
                    "mediaType" to mediaType,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun deleteSavedMessage(itemId: String): Result<Unit> {
        val uid = requireUserId()
        val ref = db.collection("users").document(uid).collection("savedMessages").document(itemId)
        return try {
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(e)
        }
    }

    fun observeNotifications(userId: String): Flow<List<NotificationItem>> = flow {
        val query = db.collection("users").document(userId)
            .collection("notifications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        NotificationItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.LIST, "users/$userId/notifications")
                    throw error
                }
        )
    }

    suspend fun sendInAppNotification(
        targetUserId: String,
        type: String,
        title: String,
        body: String,
        targetId: String = ""
    ) {
        val uid = auth.currentUser?.uid ?: return
        val notifId = "notif_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val ref = db.collection("users").document(targetUserId).collection("notifications").document(notifId)
        try {
            ref.set(
                mapOf(
                    "userId" to targetUserId,
                    "senderId" to uid,
                    "type" to type,
                    "title" to title.take(140),
                    "body" to body.take(500),
                    "targetId" to targetId.take(128),
                    "isRead" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
        }
    }

    suspend fun markNotificationRead(notificationId: String) {
        val uid = auth.currentUser?.uid ?: return
        val ref = db.collection("users").document(uid)
            .collection("notifications").document(notificationId)
        try {
            ref.update(
                mapOf(
                    "isRead" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
        }
    }
}
