package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val bio: String = "",
    val statusText: String = "Available",
    val isOnline: Boolean = false,
    val lastSeen: Timestamp? = null,
    val isSuspended: Boolean = false,
    val profileVisibility: String = "everyone",
    val lastSeenPrivacy: String = "everyone",
    val storyPrivacy: String = "public",
    val customAccentHex: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserPrivateAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val closeFriendsIds: List<String> = emptyList(),
    val hiddenStoryUserIds: List<String> = emptyList(),
    val savedPostIds: List<String> = emptyList(),
    val fcmToken: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UsernameReservation(
    @DocumentId val id: String = "",
    val username: String = "",
    val userId: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val themeId: String = "material_liquid_glass",
    val appearanceMode: String = "system",
    val accentColorHex: String = "",
    val reducedMotion: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val showNotificationPreview: Boolean = true,
    val autoDownloadWifi: Boolean = true,
    val autoDownloadCellular: Boolean = false,
    val aiConsentGranted: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceName: String = "",
    val platform: String = "Android",
    val isActive: Boolean = true,
    val lastActiveAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val type: String = "direct",
    val title: String = "",
    val description: String = "",
    val photoUrl: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val communityId: String = "",
    val isPublic: Boolean = false,
    val slowModeSeconds: Long = 0L,
    val memberApprovalRequired: Boolean = false,
    val adminsOnlyMessaging: Boolean = false,
    val inviteCode: String = "",
    val lastMessageText: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageAt: Timestamp? = null,
    val pinnedMessageId: String = "",
    val pinnedMessageText: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderAvatarUrl: String = "",
    val memberUids: List<String> = emptyList(),
    val isPublic: Boolean = false,
    val text: String = "",
    val mediaUrl: String = "",
    val mediaType: String = "text",
    val fileName: String = "",
    val fileSizeBytes: Long = 0L,
    val mimeType: String = "",
    val durationMs: Long = 0L,
    val replyToId: String = "",
    val replyToPreview: String = "",
    val forwardedFrom: String = "",
    val topicId: String = "",
    val isPinned: Boolean = false,
    val isDeleted: Boolean = false,
    val pollQuestion: String = "",
    val pollOptions: List<String> = emptyList(),
    val pollVotes: Map<String, List<String>> = emptyMap(),
    val reactions: Map<String, List<String>> = emptyMap(),
    val readBy: List<String> = emptyList(),
    val deliveredTo: List<String> = emptyList(),
    val disappearsAt: Timestamp? = null,
    val scheduledFor: Timestamp? = null,
    val reminderAt: Timestamp? = null,
    val editedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ConversationMember(
    @DocumentId val id: String = "",
    val userId: String = "",
    val conversationId: String = "",
    val memberUids: List<String> = emptyList(),
    val role: String = "member",
    val isMuted: Boolean = false,
    val isArchived: Boolean = false,
    val markedUnread: Boolean = false,
    val unreadCount: Long = 0L,
    val folder: String = "All",
    val draftText: String = "",
    val customTheme: String = "",
    val customWallpaper: String = "",
    val typingAt: Timestamp? = null,
    val joinedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class GroupTopic(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val title: String = "",
    val iconEmoji: String = "💬",
    val createdBy: String = "",
    val isPublic: Boolean = false,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Story(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatarUrl: String = "",
    val mediaUrl: String = "",
    val mediaType: String = "text",
    val caption: String = "",
    val backgroundGradient: String = "lavender_violet",
    val privacy: String = "public",
    val allowedUids: List<String> = emptyList(),
    val hiddenUids: List<String> = emptyList(),
    val isArchived: Boolean = false,
    val highlightTitle: String = "",
    val viewCount: Long = 0L,
    val reactions: Map<String, List<String>> = emptyMap(),
    val expiresAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StoryView(
    @DocumentId val id: String = "",
    val viewerId: String = "",
    val viewerName: String = "",
    val storyAuthorId: String = "",
    val reaction: String = "",
    @ServerTimestamp val viewedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SocialPost(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatarUrl: String = "",
    val caption: String = "",
    val mediaUrl: String = "",
    val collectionTag: String = "General",
    val reactions: Map<String, List<String>> = emptyMap(),
    val commentCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PostComment(
    @DocumentId val id: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FollowEdge(
    @DocumentId val id: String = "",
    val followerId: String = "",
    val followedId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val conversationId: String = "",
    val participantIds: List<String> = emptyList(),
    val callType: String = "voice",
    val status: String = "ringing",
    val offerSdp: String = "",
    val answerSdp: String = "",
    val durationSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class IceCandidateDoc(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val participantIds: List<String> = emptyList(),
    val sdpMid: String = "",
    val sdpMLineIndex: Long = 0L,
    val candidate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SavedMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val content: String = "",
    val sourceConversationId: String = "",
    val sourceSenderName: String = "",
    val mediaUrl: String = "",
    val mediaType: String = "text",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val senderId: String = "",
    val type: String = "message",
    val title: String = "",
    val body: String = "",
    val targetId: String = "",
    val isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AbuseReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val targetType: String = "user",
    val targetId: String = "",
    val reason: String = "",
    val details: String = "",
    val status: String = "open",
    val reviewedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BlockEdge(
    @DocumentId val id: String = "",
    val blockerId: String = "",
    val blockedId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PlatformAnnouncement(
    @DocumentId val id: String = "",
    val title: String = "",
    val body: String = "",
    val priority: String = "normal",
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AdminLog(
    @DocumentId val id: String = "",
    val actorId: String = "",
    val action: String = "",
    val targetId: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PlatformSettings(
    @DocumentId val id: String = "",
    val maxUploadMb: Long = 25L,
    val allowNewRegistrations: Boolean = true,
    val maintenanceMode: Boolean = false,
    val updatedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AiConversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val lastPrompt: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AiMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "user",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
