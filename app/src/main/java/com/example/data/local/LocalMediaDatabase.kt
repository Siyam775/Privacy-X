package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "local_media")
data class LocalMediaEntity(
    @PrimaryKey val mediaId: String,
    val messageId: String,
    val conversationId: String,
    val mediaType: String, // "image" | "voice" | "document"
    val localFilePath: String,
    val localThumbPath: String = "",
    val remotePath: String = "",
    val mimeType: String,
    val fileName: String,
    val sizeBytes: Long,
    val durationMs: Long = 0L,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val cloudDeleted: Boolean = false,
    val isRestoredFromBackup: Boolean = false,
    val downloadedToDevice: Boolean = false,
    val downloadedAtMs: Long = 0L
)

@Entity(tableName = "cached_conversations")
data class CachedConversationEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val description: String,
    val photoUrl: String,
    val ownerId: String,
    val memberUidsCsv: String,
    val adminUidsCsv: String,
    val lastMessageText: String,
    val lastMessageSenderId: String,
    val updatedAtMs: Long
)

@Entity(tableName = "cached_messages")
data class CachedMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val mediaId: String,
    val mediaUrl: String,
    val mediaThumbnailUrl: String,
    val mediaType: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val durationMs: Long,
    val mediaExpiresAt: Long,
    val mediaExpired: Boolean,
    val replyToId: String,
    val replyToPreview: String,
    val isPinned: Boolean,
    val isDeleted: Boolean,
    val createdAtMs: Long
)

@Entity(tableName = "pending_outgoing_messages")
data class PendingOutgoingMessageEntity(
    @PrimaryKey val clientMessageId: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val senderAvatarUrl: String,
    val text: String,
    val replyToId: String = "",
    val replyToPreview: String = "",
    val topicId: String = "",
    val disappearsInMinutes: Long? = null,
    val scheduledDelayMinutes: Long? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val status: String = "pending", // "pending" | "sending" | "failed"
    val attempts: Int = 0
)

@Entity(tableName = "backup_history")
data class BackupHistoryEntity(
    @PrimaryKey val backupId: String,
    val provider: String, // "google_drive" | "local_zip"
    val fileName: String,
    val driveFileId: String = "",
    val createdAtMs: Long,
    val sizeBytes: Long,
    val includedMedia: Boolean,
    val includedMediaCount: Int,
    val isEncrypted: Boolean,
    val status: String, // "completed" | "restored" | "failed"
    val summary: String = ""
)

@Dao
interface LocalStorageDao {
    @Query("SELECT * FROM local_media ORDER BY createdAtMs DESC")
    fun observeAllLocalMedia(): Flow<List<LocalMediaEntity>>

    @Query("SELECT * FROM local_media ORDER BY createdAtMs DESC")
    suspend fun getAllLocalMedia(): List<LocalMediaEntity>

    @Query("SELECT * FROM local_media WHERE mediaId = :mediaId OR messageId = :messageId LIMIT 1")
    suspend fun findByMediaOrMessageId(mediaId: String, messageId: String): LocalMediaEntity?

    @Query("SELECT * FROM local_media WHERE conversationId = :conversationId ORDER BY createdAtMs DESC")
    suspend fun getMediaForConversation(conversationId: String): List<LocalMediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocalMedia(entity: LocalMediaEntity)

    @Query("UPDATE local_media SET messageId = :messageId WHERE mediaId = :mediaId")
    suspend fun linkMessageId(mediaId: String, messageId: String)

    @Query("UPDATE local_media SET cloudDeleted = 1 WHERE expiresAtMs <= :nowMs")
    suspend fun markExpiredCloudMedia(nowMs: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversations(items: List<CachedConversationEntity>)

    @Query("SELECT * FROM cached_conversations ORDER BY updatedAtMs DESC")
    fun observeCachedConversations(): Flow<List<CachedConversationEntity>>

    @Query("SELECT * FROM cached_conversations ORDER BY updatedAtMs DESC")
    suspend fun getCachedConversations(): List<CachedConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(items: List<CachedMessageEntity>)

    @Query("SELECT * FROM cached_messages WHERE conversationId = :conversationId ORDER BY createdAtMs ASC")
    fun observeCachedMessages(conversationId: String): Flow<List<CachedMessageEntity>>

    @Query("SELECT * FROM cached_messages ORDER BY createdAtMs ASC")
    suspend fun getAllCachedMessages(): List<CachedMessageEntity>

    @Query("UPDATE cached_messages SET mediaExpired = 1, mediaUrl = '', mediaThumbnailUrl = '' WHERE mediaId = :mediaId OR (mediaExpiresAt > 0 AND mediaExpiresAt <= :nowMs)")
    suspend fun markCachedMessagesExpired(mediaId: String = "", nowMs: Long = System.currentTimeMillis())

    @Query("SELECT * FROM pending_outgoing_messages ORDER BY createdAtMs ASC")
    fun observePendingOutgoing(): Flow<List<PendingOutgoingMessageEntity>>

    @Query("SELECT * FROM pending_outgoing_messages WHERE status != 'sent' ORDER BY createdAtMs ASC")
    suspend fun getPendingOutgoing(): List<PendingOutgoingMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPendingOutgoing(item: PendingOutgoingMessageEntity)

    @Query("DELETE FROM pending_outgoing_messages WHERE clientMessageId = :clientMessageId")
    suspend fun deletePendingOutgoing(clientMessageId: String)

    @Query("SELECT * FROM backup_history ORDER BY createdAtMs DESC")
    fun observeBackupHistory(): Flow<List<BackupHistoryEntity>>

    @Query("SELECT * FROM backup_history ORDER BY createdAtMs DESC")
    suspend fun getBackupHistory(): List<BackupHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBackupHistory(item: BackupHistoryEntity)
}

@Database(
    entities = [
        LocalMediaEntity::class,
        CachedConversationEntity::class,
        CachedMessageEntity::class,
        PendingOutgoingMessageEntity::class,
        BackupHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PrivacyXLocalDatabase : RoomDatabase() {
    abstract fun localStorageDao(): LocalStorageDao

    companion object {
        @Volatile
        private var INSTANCE: PrivacyXLocalDatabase? = null

        fun getInstance(context: Context): PrivacyXLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PrivacyXLocalDatabase::class.java,
                    "privacyx_local_storage.db"
                ).fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
