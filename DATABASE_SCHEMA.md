# PrivacyX — Database Schema (Cloud Firestore)

All documents reside in named database `ai-studio-android-privacyx-5767d797-31a9-488e-9a25-d28909171c2e`.

## Top-Level Collections
- **`users/{userId}`**: Public/discoverable profile (`userId`, `username`, `displayName`, `photoUrl`, `bio`, `statusText`, `isOnline`, `lastSeen`, `isSuspended`, `profileVisibility`, `lastSeenPrivacy`, `storyPrivacy`, `customAccentHex`, `createdAt`, `updatedAt`).
  - **`users/{userId}/private/account`**: Owner/SuperAdmin-only PII (`email`, `closeFriendsIds`, `hiddenStoryUserIds`, `savedPostIds`, `fcmToken`, `updatedAt`).
  - **`users/{userId}/userSettings/preferences`**: Synced theme & notification/media settings (`themeId`, `appearanceMode`, `accentColorHex`, `reducedMotion`, `notificationsEnabled`, `quietHoursEnabled`, `soundEnabled`, `vibrationEnabled`, `showNotificationPreview`, `autoDownloadWifi`, `autoDownloadCellular`, `aiConsentGranted`, `updatedAt`).
  - **`users/{userId}/sessions/{sessionId}`**: Active device sessions (`deviceName`, `platform`, `isActive`, `lastActiveAt`, `createdAt`).
  - **`users/{userId}/savedMessages/{itemId}`**: Personal bookmarks & notes.
  - **`users/{userId}/notifications/{notificationId}`**: In-app notifications (`type`, `title`, `body`, `targetId`, `isRead`, `createdAt`).
  - **`users/{userId}/aiConversations/{sessionId}`** + `messages/{messageId}`: Persisted AI Assistant sessions.
- **`usernames/{username}`**: Atomic uniqueness reservation (`username`, `userId`, `displayName`, `photoUrl`, `createdAt`).
- **`conversations/{conversationId}`**: 1:1 DMs (`dm_{uid1}_{uid2}`), Groups, Communities, Channels (`type`, `title`, `description`, `ownerId`, `memberUids`, `adminUids`, `communityId`, `isPublic`, `slowModeSeconds`, `memberApprovalRequired`, `inviteCode`, `lastMessageText`, `lastMessageSenderId`, `lastMessageAt`, `pinnedMessageId`, `pinnedMessageText`, `createdAt`, `updatedAt`).
  - **`messages/{messageId}`**: Text, image, video, voice, document, poll, sticker messages + `replyToId`, `forwardedFrom`, `topicId`, `isPinned`, `isDeleted`, `pollQuestion`, `pollOptions`, `pollVotes`, `reactions`, `readBy`, `deliveredTo`, `disappearsAt`, `scheduledFor`, `editedAt`, `createdAt`, `updatedAt`.
  - **`members/{userId}`**: Per-user conversation state (`role`, `isMuted`, `isArchived`, `markedUnread`, `unreadCount`, `folder`, `draftText`, `customTheme`, `customWallpaper`, `typingAt`).
  - **`topics/{topicId}`**: Group discussion topics (`title`, `iconEmoji`, `createdBy`).
- **`stories/{storyId}`** + **`views/{viewerId}`**: 24-hour expiring stories (`expiresAt`), privacy (`public`, `followers`, `close_friends`, `custom`), `hiddenUids`, `isArchived`, `highlightTitle`, `viewCount`.
- **`posts/{postId}`** + **`comments/{commentId}`**: Separated social feed posts & comments.
- **`follows/{followerId_followedId}`**: Follow graph edges.
- **`calls/{callId}`** + **`candidates/{candidateId}`**: WebRTC call signaling (`offerSdp`, `answerSdp`, `status`, `callType`, `durationSeconds`, ICE candidates).
- **`reports/{reportId}`**: User-submitted moderation reports.
- **`blocks/{blockerId_blockedId}`**: User block relationships.
- **`platformAnnouncements/{announcementId}`**: Super Admin global announcements.
- **`adminLogs/{logId}`**: Immutable Super Admin audit trail (`allow update, delete: if false`).
- **`platformSettings/global`**: Global platform configuration (`maxUploadMb`, `allowNewRegistrations`, `maintenanceMode`).
