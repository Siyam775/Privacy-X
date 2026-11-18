export type AppThemeId = "material_liquid_glass" | "pure_ios_glass" | "classic_bw";
export type AppearanceMode = "system" | "light" | "dark";

export interface UserProfile {
  id: string;
  userId: string;
  username: string;
  displayName: string;
  photoUrl: string;
  bio: string;
  statusText: string;
  isOnline: boolean;
  lastSeen?: Date | null;
  isSuspended: boolean;
  profileVisibility: string;
  lastSeenPrivacy: string;
  storyPrivacy: string;
  customAccentHex: string;
  createdAt?: Date | null;
  updatedAt?: Date | null;
}

export interface UserPrivateAccount {
  id: string;
  userId: string;
  email: string;
  closeFriendsIds: string[];
  hiddenStoryUserIds: string[];
  savedPostIds: string[];
  fcmToken: string;
  updatedAt?: Date | null;
}

export interface UserSettings {
  id: string;
  userId: string;
  themeId: string;
  appearanceMode: string;
  accentColorHex: string;
  reducedMotion: boolean;
  notificationsEnabled: boolean;
  quietHoursEnabled: boolean;
  soundEnabled: boolean;
  vibrationEnabled: boolean;
  showNotificationPreview: boolean;
  autoDownloadWifi: boolean;
  autoDownloadCellular: boolean;
  aiConsentGranted: boolean;
  updatedAt?: Date | null;
}

export interface UserSession {
  id: string;
  userId: string;
  deviceName: string;
  platform: string;
  isActive: boolean;
  lastActiveAt?: Date | null;
  createdAt?: Date | null;
}

export interface Conversation {
  id: string;
  type: "direct" | "group" | "community" | "channel";
  title: string;
  description: string;
  photoUrl: string;
  ownerId: string;
  memberUids: string[];
  adminUids: string[];
  communityId: string;
  isPublic: boolean;
  slowModeSeconds: number;
  memberApprovalRequired: boolean;
  adminsOnlyMessaging: boolean;
  inviteCode: string;
  isArchived?: boolean;
  lastMessageText: string;
  lastMessageSenderId: string;
  lastMessageAt?: Date | null;
  pinnedMessageId: string;
  pinnedMessageText: string;
  createdAt?: Date | null;
  updatedAt?: Date | null;
}

export interface ChatMessage {
  id: string;
  conversationId: string;
  senderId: string;
  senderName: string;
  senderAvatarUrl: string;
  memberUids: string[];
  isPublic: boolean;
  text: string;
  mediaUrl: string;
  mediaId: string;
  mediaThumbnailUrl: string;
  mediaExpiresAt: number;
  mediaExpired: boolean;
    mediaType: "text" | "image" | "voice" | "document" | "video" | "poll" | "sticker";
  fileName: string;
  fileSizeBytes: number;
  mimeType: string;
  durationMs: number;
  replyToId: string;
  replyToPreview: string;
  forwardedFrom: string;
  topicId: string;
  isPinned: boolean;
  isDeleted: boolean;
  pollQuestion: string;
  pollOptions: string[];
  pollVotes: Record<string, string[]>;
  reactions: Record<string, string[]>;
  readBy: string[];
  deliveredTo: string[];
  disappearsAt?: Date | null;
  scheduledFor?: Date | null;
  editedAt?: Date | null;
  createdAt?: Date | null;
  updatedAt?: Date | null;
}

export interface ConversationMember {
  id: string;
  userId: string;
  conversationId: string;
  memberUids: string[];
  role: "owner" | "admin" | "member";
  isMuted: boolean;
  isArchived: boolean;
  markedUnread: boolean;
  unreadCount: number;
  folder: string;
  draftText: string;
  customTheme: string;
  customWallpaper: string;
  typingAt?: Date | null;
  joinedAt?: Date | null;
  updatedAt?: Date | null;
}

export interface GroupTopic {
  id: string;
  conversationId: string;
  title: string;
  iconEmoji: string;
  createdBy: string;
  isPublic: boolean;
  memberUids: string[];
  createdAt?: Date | null;
}

export interface Story {
  id: string;
  authorId: string;
  authorName: string;
  authorAvatarUrl: string;
  mediaUrl: string;
  mediaType: "text" | "image";
  caption: string;
  backgroundGradient: string;
  privacy: "public" | "followers" | "close_friends" | "custom";
  allowedUids: string[];
  hiddenUids: string[];
  isArchived: boolean;
  highlightTitle: string;
  viewCount: number;
  reactions: Record<string, string[]>;
  expiresAt?: Date | null;
  createdAt?: Date | null;
  updatedAt?: Date | null;
}

export interface StoryView {
  id: string;
  viewerId: string;
  viewerName: string;
  storyAuthorId: string;
  reaction: string;
  viewedAt?: Date | null;
}

export interface SocialPost {
  id: string;
  authorId: string;
  authorName: string;
  authorAvatarUrl: string;
  caption: string;
  mediaUrl: string;
  collectionTag: string;
  reactions: Record<string, string[]>;
  commentCount: number;
  createdAt?: Date | null;
  updatedAt?: Date | null;
}

export interface PostComment {
  id: string;
  postId: string;
  authorId: string;
  authorName: string;
  text: string;
  createdAt?: Date | null;
}

export interface FollowEdge {
  id: string;
  followerId: string;
  followedId: string;
  createdAt?: Date | null;
}

export interface BlockEdge {
  id: string;
  blockerId: string;
  blockedId: string;
  createdAt?: Date | null;
}

export interface CallSession {
  id: string;
  callerId: string;
  callerName: string;
  conversationId: string;
  participantIds: string[];
  callType: "voice" | "video";
  status: "ringing" | "active" | "ended" | "missed" | "rejected";
  offerSdp: string;
  answerSdp: string;
  durationSeconds: number;
  createdAt?: Date | null;
  updatedAt?: Date | null;
}

export interface IceCandidateDoc {
  id: string;
  senderId: string;
  participantIds: string[];
  sdpMid: string;
  sdpMLineIndex: number;
  candidate: string;
  createdAt?: Date | null;
}

export interface SavedMessage {
  id: string;
  userId: string;
  content: string;
  sourceConversationId: string;
  sourceSenderName: string;
  mediaUrl: string;
  mediaType: string;
  createdAt?: Date | null;
}

export interface NotificationItem {
  id: string;
  userId: string;
  senderId: string;
  type: string;
  title: string;
  body: string;
  targetId: string;
  isRead: boolean;
  createdAt?: Date | null;
  updatedAt?: Date | null;
}

export interface AbuseReport {
  id: string;
  reporterId: string;
  targetType: string;
  targetId: string;
  reason: string;
  details: string;
  status: string;
  reviewedBy: string;
  createdAt?: Date | null;
  updatedAt?: Date | null;
}

export interface PlatformAnnouncement {
  id: string;
  title: string;
  body: string;
  priority: string;
  createdBy: string;
  createdAt?: Date | null;
}

export interface AdminLog {
  id: string;
  actorId: string;
  action: string;
  targetId: string;
  details: string;
  createdAt?: Date | null;
}

export interface PlatformSettings {
  id: string;
  maxUploadMb: number;
  allowNewRegistrations: boolean;
  maintenanceMode: boolean;
  updatedBy: string;
  updatedAt?: Date | null;
}

export interface AiConversation {
  id: string;
  userId: string;
  title: string;
  lastPrompt: string;
  createdAt?: Date | null;
  updatedAt?: Date | null;
}

export interface AiMessage {
  id: string;
  userId: string;
  role: "user" | "assistant";
  text: string;
  createdAt?: Date | null;
}

export const SUPER_ADMIN_UID = "2bL4lJTTt9WMILpCclHeKNYOdz62";

export interface UsernameReservation {
  id: string;
  username: string;
  userId: string;
  displayName: string;
  photoUrl: string;
  createdAt?: Date | null;
}

export interface MediaAvailabilityState {
  label: "cloud_and_local" | "local_only" | "cloud_only" | "backup_only" | "missing";
}

export interface LocalMediaEntity {
  mediaId: string;
  messageId: string;
  conversationId: string;
  mediaType: "image" | "voice" | "document";
  localFilePath: string;
  localThumbPath: string;
  remotePath: string;
  mimeType: string;
  fileName: string;
  sizeBytes: number;
  durationMs: number;
  createdAtMs: number;
  expiresAtMs: number;
  cloudDeleted: boolean;
  isRestoredFromBackup: boolean;
  downloadedToDevice: boolean;
  downloadedAtMs: number;
}

export interface CachedConversationEntity {
  id: string;
  type: string;
  title: string;
  description: string;
  photoUrl: string;
  ownerId: string;
  memberUidsCsv: string;
  adminUidsCsv: string;
  lastMessageText: string;
  lastMessageSenderId: string;
  updatedAtMs: number;
}

export interface CachedMessageEntity {
  id: string;
  conversationId: string;
  senderId: string;
  senderName: string;
  text: string;
  mediaId: string;
  mediaUrl: string;
  mediaThumbnailUrl: string;
  mediaType: string;
  fileName: string;
  fileSizeBytes: number;
  mimeType: string;
  durationMs: number;
  mediaExpiresAt: number;
  mediaExpired: boolean;
  replyToId: string;
  replyToPreview: string;
  isPinned: boolean;
  isDeleted: boolean;
  createdAtMs: number;
}

export interface PendingOutgoingMessageEntity {
  clientMessageId: string;
  conversationId: string;
  senderId: string;
  senderName: string;
  senderAvatarUrl: string;
  text: string;
  replyToId: string;
  replyToPreview: string;
  topicId: string;
  disappearsInMinutes?: number | null;
  scheduledDelayMinutes?: number | null;
  createdAtMs: number;
  status: "pending" | "sending" | "failed";
  attempts: number;
}

export interface BackupHistoryEntity {
  backupId: string;
  provider: "google_drive" | "local_zip";
  fileName: string;
  driveFileId: string;
  createdAtMs: number;
  sizeBytes: number;
  includedMedia: boolean;
  includedMediaCount: number;
  isEncrypted: boolean;
  status: "completed" | "restored" | "failed";
  summary: string;
}

export interface UploadedMediaResult {
  mediaId: string;
  secureUrl: string;
  thumbnailUrl: string;
  remotePath: string;
  localFilePath: string;
  localThumbPath: string;
  fileName: string;
  mimeType: string;
  sizeBytes: number;
  expiresAtMs: number;
  category: "image" | "voice" | "document";
}

export interface MediaValidationResult {
  fileName: string;
  mimeType: string;
  sizeBytes: number;
  category: "image" | "voice" | "document";
}

export interface AuthGateState {
  type: "loading" | "signed_out" | "needs_onboarding" | "ready";
  uid?: string;
  email?: string;
  suggestedName?: string;
  profile?: UserProfile;
}

export interface SecondaryDestination {
  type: "none" | "chat_detail" | "ai_assistant" | "notifications" | "saved_messages" | "storage_and_backup" | "super_admin";
  conversationId?: string;
  isPublicPreview?: boolean;
}
