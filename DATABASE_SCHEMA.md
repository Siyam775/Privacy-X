# PrivacyX — Database Schema (Cloud Firestore & Local Room)

## 1. Cloud Firestore Collections
All Firestore documents reside in named database `ai-studio-android-privacyx-5767d797-31a9-488e-9a25-d28909171c2e`.

- **`users/{userId}`**: Public/discoverable profile (`userId`, `username`, `displayName`, `photoUrl`, `bio`, `statusText`, `isOnline`, `lastSeen`, `isSuspended`, `profileVisibility`, `lastSeenPrivacy`, `storyPrivacy`, `customAccentHex`, `createdAt`, `updatedAt`).
  - **`users/{userId}/private/account`**: Owner/SuperAdmin-only PII (`email`, `closeFriendsIds`, `hiddenStoryUserIds`, `savedPostIds`, `fcmToken`, `updatedAt`).
  - **`users/{userId}/userSettings/preferences`**: Synced theme & notification/media settings (`themeId`, `appearanceMode`, `accentColorHex`, `reducedMotion`, `notificationsEnabled`, `quietHoursEnabled`, `soundEnabled`, `vibrationEnabled`, `showNotificationPreview`, `autoDownloadWifi`, `autoDownloadCellular`, `aiConsentGranted`, `updatedAt`).
  - **`users/{userId}/sessions/{sessionId}`**: Active device sessions.
  - **`users/{userId}/savedMessages/{itemId}`**: Personal bookmarks & notes.
  - **`users/{userId}/notifications/{notificationId}`**: In-app notifications.
  - **`users/{userId}/aiConversations/{sessionId}`** + `messages/{messageId}`: Persisted AI Assistant sessions.
- **`usernames/{username}`**: Atomic uniqueness reservation (`username`, `userId`, `displayName`, `photoUrl`, `createdAt`).
- **`conversations/{conversationId}`**: 1:1 DMs (`dm_{uid1}_{uid2}`), Groups, Communities, Channels (`type`, `title`, `description`, `ownerId`, `memberUids`, `adminUids`, `communityId`, `isPublic`, `slowModeSeconds`, `memberApprovalRequired`, `inviteCode`, `lastMessageText`, `lastMessageSenderId`, `lastMessageAt`, `pinnedMessageId`, `pinnedMessageText`, `createdAt`, `updatedAt`).
  - **`messages/{messageId}`**: `text`, `image`, `voice`, `document`, `poll`, `sticker` messages (video disallowed) + `mediaId`, `mediaUrl`, `mediaThumbnailUrl`, `mediaExpiresAt`, `mediaExpired`, `replyToId`, `forwardedFrom`, `topicId`, `isPinned`, `isDeleted`, `pollQuestion`, `pollOptions`, `pollVotes`, `reactions`, `readBy`, `deliveredTo`, `disappearsAt`, `scheduledFor`, `editedAt`, `createdAt`, `updatedAt`.
  - **`members/{userId}`**: Per-user conversation state (`role`, `isMuted`, `isArchived`, `markedUnread`, `unreadCount`, `folder`, `draftText`, `customTheme`, `customWallpaper`, `typingAt`).
  - **`topics/{topicId}`**: Group discussion topics (`title`, `iconEmoji`, `createdBy`).
- **`stories/{storyId}`** + **`views/{viewerId}`**: 24-hour expiring stories (`text` and `image` only; `expiresAt`), privacy (`public`, `followers`, `close_friends`, `custom`), `hiddenUids`, `isArchived`, `highlightTitle`, `viewCount`.
- **`posts/{postId}`** + **`comments/{commentId}`**: Separated social feed posts & comments.
- **`mediaObjects/{mediaId}`**: Server-managed 7-day Supabase storage tracking (`objectPath`, `thumbPath`, `ownerUid`, `conversationId`, `messageId`, `sizeBytes`, `expiresAtMs`, `deletedConfirmed`).
- **`follows/{followerId_followedId}`**, **`calls/{callId}`** + **`candidates/{candidateId}`** (WebRTC calling), **`reports/{reportId}`**, **`blocks/{blockerId_blockedId}`**, **`platformAnnouncements/{announcementId}`**, **`adminLogs/{logId}`**, **`platformSettings/global`**.

## 2. Local Android Room Database (`privacyx_local_media.db`)
- **`local_media`**: Tracks `mediaId`, `conversationId`, `messageId`, `mediaType`, `localFilePath`, `localThumbnailPath`, `remoteObjectPath`, `remoteUrl`, `mimeType`, `sizeBytes`, `createdAtMs`, `cloudExpiresAtMs`, `cloudDeleted`, `isDownloadedByUser`, `backedUpToDrive`, `lastBackupId`.
- **`cached_messages`**: Offline message cache indexed by `messageId`, `conversationId`, `createdAtMs`, and `syncState` (`SYNCED`, `PENDING`, `FAILED`).
- **`pending_outgoing_messages`**: Offline outbox queue with deterministic `clientMessageId`, `conversationId`, `payloadJson`, `localAttachmentPath`, `retryCount`, and `status`.

## 3. Website Browser Storage (IndexedDB via Dexie)
- **`cachedMessages`** stores the conversation messages received from Firestore for this browser profile.
- **`mediaFiles`** stores photo blobs in IndexedDB so photos remain available locally after the cloud copy expires.
- Browser data is device/browser-specific and is not synchronized between browsers.

## 4. Supabase PostgreSQL (`media_objects` & `chat-media` bucket)
- Private bucket `chat-media` (`public = false`, 25 MB limit, image/audio/video MIME allowlists, and document MIME allowlist). Android chat videos are cloud-only; Android message text and photos are also retained locally.
- `public.media_objects` stores `media_id`, `object_path`, `thumb_path`, `owner_uid`, `conversation_id`, `category`, `mime_type`, `size_bytes`, `expires_at`, `deleted_from_storage`, `deleted_at` with strict RLS deny-by-default.
