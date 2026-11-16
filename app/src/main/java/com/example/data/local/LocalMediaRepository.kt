package com.example.data.local

import android.content.Context
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.Date

enum class MediaAvailabilityState(val label: String) {
    CLOUD_AND_LOCAL("Cloud + Local"),
    LOCAL_ONLY("Local Only"),
    CLOUD_ONLY("Cloud Only"),
    BACKUP_ONLY("Backup Only"),
    MISSING("Unavailable")
}

data class StorageStatsSnapshot(
    val localImagesCount: Int = 0,
    val localVoiceCount: Int = 0,
    val localDocumentsCount: Int = 0,
    val localTotalBytes: Long = 0L,
    val tempCacheBytes: Long = 0L,
    val activeCloudFilesCount: Int = 0,
    val estimatedCloudBytes: Long = 0L,
    val pendingOfflineMessagesCount: Int = 0
)

class LocalMediaRepository(private val context: Context) {
    private val dao: LocalStorageDao = PrivacyXLocalDatabase.getInstance(context).localStorageDao()

    val localMediaFlow: Flow<List<LocalMediaEntity>> = dao.observeAllLocalMedia()
    val pendingOutgoingFlow: Flow<List<PendingOutgoingMessageEntity>> = dao.observePendingOutgoing()
    val backupHistoryFlow: Flow<List<BackupHistoryEntity>> = dao.observeBackupHistory()

    val storageStatsFlow: Flow<StorageStatsSnapshot> = localMediaFlow.map { items ->
        val validItems = items.filter { it.localFilePath.isNotBlank() && File(it.localFilePath).exists() }
        val now = System.currentTimeMillis()
        val activeCloud = items.filter { !it.cloudDeleted && !it.isRestoredFromBackup && it.expiresAtMs > now }
        val tempCacheBytes = context.cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        val pendingCount = dao.getPendingOutgoing().size
        StorageStatsSnapshot(
            localImagesCount = validItems.count { it.mediaType == "image" },
            localVoiceCount = validItems.count { it.mediaType == "voice" },
            localDocumentsCount = validItems.count { it.mediaType == "document" },
            localTotalBytes = validItems.sumOf { File(it.localFilePath).length() },
            tempCacheBytes = tempCacheBytes,
            activeCloudFilesCount = activeCloud.size,
            estimatedCloudBytes = activeCloud.sumOf { it.sizeBytes },
            pendingOfflineMessagesCount = pendingCount
        )
    }

    suspend fun saveLocalMediaRecord(entity: LocalMediaEntity) {
        dao.upsertLocalMedia(entity)
    }

    suspend fun linkMessageId(mediaId: String, messageId: String) {
        if (mediaId.isNotBlank() && messageId.isNotBlank()) {
            dao.linkMessageId(mediaId, messageId)
        }
    }

    suspend fun findLocalMedia(mediaId: String, messageId: String): LocalMediaEntity? {
        if (mediaId.isBlank() && messageId.isBlank()) return null
        val row = dao.findByMediaOrMessageId(mediaId, messageId) ?: return null
        return if (row.localFilePath.isNotBlank() && File(row.localFilePath).exists()) row else null
    }

    suspend fun getAllLocalMedia(): List<LocalMediaEntity> = dao.getAllLocalMedia()

    suspend fun markExpiredCloudMedia(nowMs: Long = System.currentTimeMillis()) {
        dao.markExpiredCloudMedia(nowMs)
        dao.markCachedMessagesExpired(nowMs = nowMs)
    }

    fun resolveAvailabilityState(
        message: ChatMessage,
        localMedia: LocalMediaEntity?,
        knownInBackup: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): MediaAvailabilityState {
        val hasLocal = localMedia != null &&
            localMedia.localFilePath.isNotBlank() &&
            File(localMedia.localFilePath).exists()

        val cloudExpired = message.mediaExpired ||
            message.mediaUrl.isBlank() ||
            (message.mediaExpiresAt > 0L && message.mediaExpiresAt <= nowMs)

        val hasCloud = !cloudExpired && message.mediaUrl.isNotBlank()

        return when {
            hasCloud && hasLocal -> MediaAvailabilityState.CLOUD_AND_LOCAL
            hasLocal && !hasCloud -> MediaAvailabilityState.LOCAL_ONLY
            hasCloud && !hasLocal -> MediaAvailabilityState.CLOUD_ONLY
            knownInBackup -> MediaAvailabilityState.BACKUP_ONLY
            else -> MediaAvailabilityState.MISSING
        }
    }

    suspend fun cacheConversations(conversations: List<Conversation>) {
        if (conversations.isEmpty()) return
        dao.upsertConversations(
            conversations.map { c ->
                CachedConversationEntity(
                    id = c.id,
                    type = c.type,
                    title = c.title,
                    description = c.description,
                    photoUrl = c.photoUrl,
                    ownerId = c.ownerId,
                    memberUidsCsv = c.memberUids.joinToString(","),
                    adminUidsCsv = c.adminUids.joinToString(","),
                    lastMessageText = c.lastMessageText,
                    lastMessageSenderId = c.lastMessageSenderId,
                    updatedAtMs = c.lastMessageAt?.toDate()?.time
                        ?: c.updatedAt?.toDate()?.time
                        ?: System.currentTimeMillis()
                )
            }
        )
    }

    suspend fun getCachedConversations(): List<Conversation> =
        dao.getCachedConversations().map { c ->
            Conversation(
                id = c.id,
                type = c.type,
                title = c.title,
                description = c.description,
                photoUrl = c.photoUrl,
                ownerId = c.ownerId,
                memberUids = c.memberUidsCsv.split(",").filter { it.isNotBlank() },
                adminUids = c.adminUidsCsv.split(",").filter { it.isNotBlank() },
                lastMessageText = c.lastMessageText,
                lastMessageSenderId = c.lastMessageSenderId,
                lastMessageAt = Timestamp(Date(c.updatedAtMs)),
                updatedAt = Timestamp(Date(c.updatedAtMs))
            )
        }

    suspend fun cacheMessages(messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        dao.upsertMessages(
            messages.map { m ->
                CachedMessageEntity(
                    id = m.id,
                    conversationId = m.conversationId,
                    senderId = m.senderId,
                    senderName = m.senderName,
                    text = m.text,
                    mediaId = m.mediaId,
                    mediaUrl = m.mediaUrl,
                    mediaThumbnailUrl = m.mediaThumbnailUrl,
                    mediaType = m.mediaType,
                    fileName = m.fileName,
                    fileSizeBytes = m.fileSizeBytes,
                    mimeType = m.mimeType,
                    durationMs = m.durationMs,
                    mediaExpiresAt = m.mediaExpiresAt,
                    mediaExpired = m.mediaExpired,
                    replyToId = m.replyToId,
                    replyToPreview = m.replyToPreview,
                    isPinned = m.isPinned,
                    isDeleted = m.isDeleted,
                    createdAtMs = m.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                )
            }
        )
    }

    suspend fun getAllCachedMessages(): List<CachedMessageEntity> = dao.getAllCachedMessages()

    fun observeCachedMessages(conversationId: String): Flow<List<ChatMessage>> =
        dao.observeCachedMessages(conversationId).map { list ->
            list.map { m ->
                ChatMessage(
                    id = m.id,
                    conversationId = m.conversationId,
                    senderId = m.senderId,
                    senderName = m.senderName,
                    text = m.text,
                    mediaId = m.mediaId,
                    mediaUrl = m.mediaUrl,
                    mediaThumbnailUrl = m.mediaThumbnailUrl,
                    mediaType = m.mediaType,
                    fileName = m.fileName,
                    fileSizeBytes = m.fileSizeBytes,
                    mimeType = m.mimeType,
                    durationMs = m.durationMs,
                    mediaExpiresAt = m.mediaExpiresAt,
                    mediaExpired = m.mediaExpired,
                    replyToId = m.replyToId,
                    replyToPreview = m.replyToPreview,
                    isPinned = m.isPinned,
                    isDeleted = m.isDeleted,
                    createdAt = Timestamp(Date(m.createdAtMs)),
                    updatedAt = Timestamp(Date(m.createdAtMs))
                )
            }
        }

    suspend fun enqueueOfflineMessage(item: PendingOutgoingMessageEntity) {
        dao.upsertPendingOutgoing(item)
    }

    suspend fun getPendingOutgoingMessages(): List<PendingOutgoingMessageEntity> =
        dao.getPendingOutgoing()

    suspend fun removePendingOutgoing(clientMessageId: String) {
        dao.deletePendingOutgoing(clientMessageId)
    }

    suspend fun addBackupHistory(item: BackupHistoryEntity) {
        dao.upsertBackupHistory(item)
    }

    suspend fun getBackupHistory(): List<BackupHistoryEntity> = dao.getBackupHistory()
}
