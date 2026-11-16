# PrivacyX — Repository & Backend API Documentation

## Android Repositories & Storage Services
- **`AuthRepository`** (`com.example.data.repository`):
  - `observeUserProfile(userId)`, `observePrivateAccount(userId)`, `observeUserSettings(userId)`, `observeSessions(userId)`
  - `isUsernameAvailable(rawUsername)`, `claimUsernameAndCreateProfile(...)` (atomic transaction)
  - `updateProfile(...)`, `updatePresence(isOnline)`, `updateUserSettings(...)`, `searchUsers(queryText)`
  - `exportUserDataJson()`, `revokeSession(sessionId)`, `deleteAccount()`
- **`MessagingRepository`** (`com.example.data.repository`):
  - `observeMyConversations(userId)`, `observePublicDiscoveryConversations()`, `observeConversation(id)`
  - `observeMessages(conversationId, limit)`, `observePublicChannelMessages(conversationId, limit)`
  - `createOrGetDirectConversation(...)`, `createGroupOrCommunityOrChannel(...)`
  - `sendMessage(...)` (supports deterministic `forcedMessageId`, `mediaId`, `mediaThumbnailUrl`, `mediaExpiresAt`, `mediaExpired`, and blocks `"video"`), `editMessage(...)`, `deleteMessage(...)`, `toggleReaction(...)`, `votePoll(...)`, `togglePinMessage(...)`, `markMessagesRead(...)`, `createTopic(...)`
- **`LocalMediaRepository`** (`com.example.data.local`):
  - Room persistence via `LocalMediaDatabase` (`local_media`, `cached_messages`, `pending_outgoing_messages`)
  - `saveMediaToLocal(...)`, `downloadAndSaveMedia(...)` (saves to `filesDir/local_media/<conversationId>/` + `MediaStore.Downloads/PrivacyX`), `resolveMediaState(...)`, `enqueuePendingMessage(...)`, `flushPendingMessages(...)`, `restoreMediaFromBackupZipEntry(...)`, `clearTemporaryCacheOnly()`
- **`SupabaseMediaService`** (`com.example.media`):
  - `isConfigured()`, `isBannedVideo(...)`, `optimizeImageToWebpAndThumb(...)`, `uploadOptimizedImage(...)`, `uploadVoiceOrDocument(...)`, `fetchFreshDownloadUrl(...)`
- **`GoogleDriveBackupManager` & `BackupCryptoZip`** (`com.example.backup`):
  - `buildBackupZip(...)`, `uploadBackupToDrive(...)`, `listDriveBackups(...)`, `restoreBackupFromDrive(...)`, `restoreBackupFromLocalUri(...)`, `getPreferencesFlow()`, `updateSchedule(...)`, `updateIncludeMedia(...)`, `updateEncryptedDefault(...)`
- **`SocialRepository`**, **`CallRepository`**, **`AiAssistantRepository`**, **`AdminRepository`**: Unchanged high-level interfaces; `SocialViewModel` media uploads routed via `SupabaseMediaService` with video disabled.

## Supabase Edge Functions (`/supabase/functions`)
- **`media-bridge`**: Verifies Firebase Auth RS256 ID token via Google JWKS, enforces non-video MIME & 25 MB limits, creates signed upload + 7-day read URLs in private bucket `chat-media`, and tracks expiry in `media_objects`. Actions: `sign_upload`, `sign_download`, `usage`.
- **`cleanup-expired-media`**: Queries expired rows (`expires_at <= now()`), removes objects and thumbnails from `chat-media` bucket first, then marks `deleted_from_storage = true`.

## Firebase Cloud Functions (`/functions/src/index.ts`)
- `POST /authorizeSupabaseMedia`: Verifies Firebase ID token + Firestore conversation membership, signs Supabase upload/read URLs, and writes 7-day `mediaObjects` tracking docs.
- `onSchedule cleanupExpiredCloudMedia` & `POST /triggerExpiredMediaCleanup`: Deletes expired Supabase objects + thumbnails first, then marks `deletedConfirmed = true` and scrubs `mediaUrl` / `mediaThumbnailUrl` + sets `mediaExpired = true` on Firestore messages without deleting text.
- `onCall syncSuperAdminClaim`: Sets `superAdmin: true` claim strictly for UID `2bL4lJTTt9WMILpCclHeKNYOdz62`.
- `onDocumentCreated onMessageCreatedNotify`: Sends FCM push notifications to conversation recipients with stored `fcmToken`.
