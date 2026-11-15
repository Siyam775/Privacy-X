package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.AppConstants
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.ConversationMember
import com.example.data.model.GroupTopic
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
import java.util.UUID

class MessagingRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeMyConversations(userId: String): Flow<List<Conversation>> = flow {
        val query = db.collection("conversations")
            .whereArrayContains("memberUids", userId)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        Conversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending {
                        it.lastMessageAt ?: it.updatedAt ?: it.createdAt ?: Timestamp(0, 0)
                    }
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(error, OperationType.LIST, "conversations")
                    }
                    throw error
                }
        )
    }

    fun observePublicDiscoveryConversations(): Flow<List<Conversation>> = flow {
        val query = db.collection("conversations")
            .whereEqualTo("isPublic", true)
            .limit(50)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        Conversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.LIST, "conversations")
                    throw error
                }
        )
    }

    fun observeConversation(conversationId: String): Flow<Conversation?> = flow {
        val docRef = db.collection("conversations").document(conversationId)
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            Conversation::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.GET, docRef.path)
                    throw error
                }
        )
    }

    fun observeMessages(
        conversationId: String,
        limit: Long = AppConstants.MESSAGE_PAGE_SIZE
    ): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        val path = "conversations/$conversationId/messages"
        val query = db.collection("conversations").document(conversationId)
            .collection("messages")
            .whereArrayContains("memberUids", uid)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(limit)

        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observePublicChannelMessages(
        conversationId: String,
        limit: Long = AppConstants.MESSAGE_PAGE_SIZE
    ): Flow<List<ChatMessage>> = flow {
        val path = "conversations/$conversationId/messages"
        val query = db.collection("conversations").document(conversationId)
            .collection("messages")
            .whereEqualTo("isPublic", true)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(limit)

        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeMemberState(conversationId: String, userId: String): Flow<ConversationMember?> = flow {
        val docRef = db.collection("conversations").document(conversationId)
            .collection("members").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            ConversationMember::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(error, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeConversationMembers(conversationId: String): Flow<List<ConversationMember>> = flow {
        val uid = requireUserId()
        val query = db.collection("conversations").document(conversationId)
            .collection("members")
            .whereArrayContains("memberUids", uid)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        ConversationMember::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.LIST, "conversations/$conversationId/members")
                    throw error
                }
        )
    }

    fun observeTopics(conversationId: String): Flow<List<GroupTopic>> = flow {
        val uid = requireUserId()
        val query = db.collection("conversations").document(conversationId)
            .collection("topics")
            .whereArrayContains("memberUids", uid)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        GroupTopic::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.LIST, "conversations/$conversationId/topics")
                    throw error
                }
        )
    }

    suspend fun createOrGetDirectConversation(
        otherUserId: String,
        otherDisplayName: String,
        otherPhotoUrl: String
    ): Result<String> {
        val uid = requireUserId()
        val sorted = listOf(uid, otherUserId).sorted()
        val conversationId = "dm_${sorted[0]}_${sorted[1]}"
        val docRef = db.collection("conversations").document(conversationId)

        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "type" to "direct",
                    "title" to otherDisplayName.trim().ifEmpty { "Direct Chat" }.take(120),
                    "description" to "",
                    "photoUrl" to otherPhotoUrl.take(1000),
                    "ownerId" to uid,
                    "memberUids" to sorted,
                    "adminUids" to sorted,
                    "communityId" to "",
                    "isPublic" to false,
                    "slowModeSeconds" to 0L,
                    "memberApprovalRequired" to false,
                    "inviteCode" to "",
                    "lastMessageText" to "",
                    "lastMessageSenderId" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(conversationId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createGroupOrCommunityOrChannel(
        type: String,
        title: String,
        description: String,
        isPublic: Boolean,
        memberUids: List<String> = emptyList(),
        communityId: String = "",
        slowModeSeconds: Long = 0L
    ): Result<String> {
        val uid = requireUserId()
        val convId = "${type}_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("conversations").document(convId)
        val allMembers = (listOf(uid) + memberUids).distinct().take(100)
        val inviteCode = UUID.randomUUID().toString().replace("-", "").take(10).uppercase()

        return try {
            docRef.set(
                mapOf(
                    "type" to type,
                    "title" to title.trim().ifEmpty { "Untitled $type" }.take(120),
                    "description" to description.trim().take(500),
                    "photoUrl" to "",
                    "ownerId" to uid,
                    "memberUids" to allMembers,
                    "adminUids" to listOf(uid),
                    "communityId" to communityId.take(128),
                    "isPublic" to isPublic,
                    "slowModeSeconds" to slowModeSeconds,
                    "memberApprovalRequired" to false,
                    "adminsOnlyMessaging" to (type == "channel"),
                    "inviteCode" to inviteCode,
                    "lastMessageText" to "Created $type",
                    "lastMessageSenderId" to uid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(convId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun joinPublicConversation(conversationId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("conversations").document(conversationId)
        return try {
            docRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun leaveConversation(conversationId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("conversations").document(conversationId)
        return try {
            docRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayRemove(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun addMember(conversationId: String, targetUid: String): Result<Unit> {
        val docRef = db.collection("conversations").document(conversationId)
        return try {
            docRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(targetUid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun removeMember(conversationId: String, targetUid: String): Result<Unit> {
        val docRef = db.collection("conversations").document(conversationId)
        return try {
            docRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayRemove(targetUid),
                    "adminUids" to FieldValue.arrayRemove(targetUid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun promoteMemberToAdmin(conversationId: String, targetUid: String): Result<Unit> {
        val docRef = db.collection("conversations").document(conversationId)
        return try {
            docRef.update(
                mapOf(
                    "adminUids" to FieldValue.arrayUnion(targetUid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun demoteMemberFromAdmin(conversationId: String, targetUid: String): Result<Unit> {
        val docRef = db.collection("conversations").document(conversationId)
        return try {
            docRef.update(
                mapOf(
                    "adminUids" to FieldValue.arrayRemove(targetUid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateConversationSettings(
        conversationId: String,
        title: String? = null,
        description: String? = null,
        photoUrl: String? = null,
        isPublic: Boolean? = null,
        slowModeSeconds: Long? = null,
        memberApprovalRequired: Boolean? = null,
        adminsOnlyMessaging: Boolean? = null
    ): Result<Unit> {
        val docRef = db.collection("conversations").document(conversationId)
        val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
        title?.let {
            val clean = it.trim().take(120)
            if (clean.isNotEmpty()) updates["title"] = clean
        }
        description?.let { updates["description"] = it.trim().take(500) }
        photoUrl?.let { updates["photoUrl"] = it.trim().take(1000) }
        isPublic?.let { updates["isPublic"] = it }
        slowModeSeconds?.let { updates["slowModeSeconds"] = it.coerceAtLeast(0L) }
        memberApprovalRequired?.let { updates["memberApprovalRequired"] = it }
        adminsOnlyMessaging?.let { updates["adminsOnlyMessaging"] = it }
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun regenerateInviteCode(conversationId: String): Result<String> {
        val docRef = db.collection("conversations").document(conversationId)
        val newCode = UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
        return try {
            docRef.update(
                mapOf(
                    "inviteCode" to newCode,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(newCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        conversation: Conversation,
        senderName: String,
        senderAvatarUrl: String,
        text: String,
        mediaType: String = "text",
        mediaUrl: String = "",
        fileName: String = "",
        fileSizeBytes: Long = 0L,
        mimeType: String = "",
        durationMs: Long = 0L,
        replyToId: String = "",
        replyToPreview: String = "",
        forwardedFrom: String = "",
        topicId: String = "",
        pollQuestion: String = "",
        pollOptions: List<String> = emptyList(),
        disappearsInMinutes: Long? = null,
        scheduledDelayMinutes: Long? = null
    ): Result<String> {
        val uid = requireUserId()
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val convRef = db.collection("conversations").document(conversation.id)
        val msgRef = convRef.collection("messages").document(msgId)

        val nowMs = System.currentTimeMillis()
        val disappearsAt = disappearsInMinutes?.takeIf { it > 0 }?.let {
            Timestamp(java.util.Date(nowMs + it * 60_000L))
        }
        val scheduledFor = scheduledDelayMinutes?.takeIf { it > 0 }?.let {
            Timestamp(java.util.Date(nowMs + it * 60_000L))
        }

        val payload = mutableMapOf<String, Any>(
            "conversationId" to conversation.id,
            "senderId" to uid,
            "senderName" to senderName.ifEmpty { "User" }.take(80),
            "senderAvatarUrl" to senderAvatarUrl.take(1000),
            "memberUids" to conversation.memberUids,
            "isPublic" to conversation.isPublic,
            "text" to text.take(8000),
            "mediaUrl" to mediaUrl.take(1000),
            "mediaType" to mediaType,
            "fileName" to fileName.take(256),
            "fileSizeBytes" to fileSizeBytes,
            "mimeType" to mimeType.take(128),
            "durationMs" to durationMs,
            "replyToId" to replyToId.take(128),
            "replyToPreview" to replyToPreview.take(300),
            "forwardedFrom" to forwardedFrom.take(120),
            "topicId" to topicId.take(128),
            "isPinned" to false,
            "isDeleted" to false,
            "pollQuestion" to pollQuestion.take(300),
            "pollOptions" to pollOptions.take(10),
            "pollVotes" to emptyMap<String, List<String>>(),
            "reactions" to emptyMap<String, List<String>>(),
            "readBy" to listOf(uid),
            "deliveredTo" to listOf(uid),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (disappearsAt != null) payload["disappearsAt"] = disappearsAt
        if (scheduledFor != null) payload["scheduledFor"] = scheduledFor

        val previewText = when (mediaType) {
            "image" -> "Photo"
            "video" -> "Video"
            "voice" -> "Voice message"
            "document" -> fileName.ifEmpty { "Document" }
            "poll" -> "Poll: ${pollQuestion.take(60)}"
            "sticker" -> "Sticker"
            else -> text.take(120)
        }

        return try {
            val batch = db.batch()
            batch.set(msgRef, payload)
            batch.update(
                convRef,
                mapOf(
                    "lastMessageText" to previewText.take(500),
                    "lastMessageSenderId" to uid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(msgId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun editMessage(conversationId: String, messageId: String, newText: String): Result<Unit> {
        val msgRef = db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
        return try {
            msgRef.update(
                mapOf(
                    "text" to newText.take(8000),
                    "editedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(conversationId: String, messageId: String): Result<Unit> {
        val msgRef = db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
        return try {
            msgRef.update(
                mapOf(
                    "text" to "This message was deleted",
                    "isDeleted" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleReaction(conversationId: String, messageId: String, emoji: String): Result<Unit> {
        val uid = requireUserId()
        val msgRef = db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
        return try {
            db.runTransaction { tx ->
                val snap = tx.get(msgRef)
                val existingReactions = (snap.get("reactions") as? Map<*, *>)?.entries
                    ?.associate { (k, v) ->
                        k.toString() to ((v as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList())
                    }?.toMutableMap() ?: mutableMapOf()
                val usersForEmoji = existingReactions[emoji]?.toMutableList() ?: mutableListOf()
                if (uid in usersForEmoji) {
                    usersForEmoji.remove(uid)
                } else {
                    usersForEmoji.add(uid)
                }
                if (usersForEmoji.isEmpty()) existingReactions.remove(emoji)
                else existingReactions[emoji] = usersForEmoji

                tx.update(
                    msgRef,
                    mapOf(
                        "reactions" to existingReactions,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun votePoll(conversationId: String, messageId: String, option: String): Result<Unit> {
        val uid = requireUserId()
        val msgRef = db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
        return try {
            db.runTransaction { tx ->
                val snap = tx.get(msgRef)
                val votes = (snap.get("pollVotes") as? Map<*, *>)?.entries
                    ?.associate { (k, v) ->
                        k.toString() to ((v as? List<*>)?.mapNotNull { it?.toString() }?.toMutableList()
                            ?: mutableListOf())
                    }?.toMutableMap() ?: mutableMapOf()

                votes.values.forEach { it.remove(uid) }
                val targetList = votes.getOrPut(option) { mutableListOf() }
                targetList.add(uid)

                tx.update(
                    msgRef,
                    mapOf(
                        "pollVotes" to votes,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun togglePinMessage(
        conversationId: String,
        messageId: String,
        previewText: String,
        pin: Boolean
    ): Result<Unit> {
        val convRef = db.collection("conversations").document(conversationId)
        val msgRef = convRef.collection("messages").document(messageId)
        return try {
            val batch = db.batch()
            batch.update(
                msgRef,
                mapOf(
                    "isPinned" to pin,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                convRef,
                mapOf(
                    "pinnedMessageId" to if (pin) messageId else "",
                    "pinnedMessageText" to if (pin) previewText.take(500) else "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun markMessagesRead(conversationId: String, messages: List<ChatMessage>) {
        val uid = auth.currentUser?.uid ?: return
        val unread = messages.filter { it.senderId != uid && uid !in it.readBy }.take(15)
        if (unread.isEmpty()) return
        try {
            val batch = db.batch()
            unread.forEach { msg ->
                val ref = db.collection("conversations").document(conversationId)
                    .collection("messages").document(msg.id)
                batch.update(
                    ref,
                    mapOf(
                        "readBy" to FieldValue.arrayUnion(uid),
                        "deliveredTo" to FieldValue.arrayUnion(uid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "conversations/$conversationId/messages")
        }
    }

    suspend fun updateMemberState(
        conversationId: String,
        memberUids: List<String>,
        role: String = "member",
        isMuted: Boolean? = null,
        isArchived: Boolean? = null,
        markedUnread: Boolean? = null,
        folder: String? = null,
        draftText: String? = null,
        customTheme: String? = null,
        customWallpaper: String? = null,
        typingNow: Boolean? = null
    ) {
        val uid = auth.currentUser?.uid ?: return
        val ref = db.collection("conversations").document(conversationId)
            .collection("members").document(uid)
        val payload = mutableMapOf<String, Any>(
            "userId" to uid,
            "conversationId" to conversationId,
            "memberUids" to memberUids,
            "role" to role,
            "isMuted" to (isMuted ?: false),
            "isArchived" to (isArchived ?: false),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (markedUnread != null) payload["markedUnread"] = markedUnread
        if (folder != null) payload["folder"] = folder.take(40)
        if (draftText != null) payload["draftText"] = draftText.take(4000)
        if (customTheme != null) payload["customTheme"] = customTheme.take(40)
        if (customWallpaper != null) payload["customWallpaper"] = customWallpaper.take(100)
        if (typingNow == true) payload["typingAt"] = FieldValue.serverTimestamp()
        try {
            ref.set(payload, SetOptions.merge()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
        }
    }

    suspend fun createTopic(
        conversation: Conversation,
        title: String,
        iconEmoji: String
    ): Result<Unit> {
        val uid = requireUserId()
        val topicId = "topic_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val ref = db.collection("conversations").document(conversation.id)
            .collection("topics").document(topicId)
        return try {
            ref.set(
                mapOf(
                    "conversationId" to conversation.id,
                    "title" to title.trim().ifEmpty { "General" }.take(80),
                    "iconEmoji" to iconEmoji.ifEmpty { "💬" }.take(16),
                    "createdBy" to uid,
                    "isPublic" to conversation.isPublic,
                    "memberUids" to conversation.memberUids,
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
}
