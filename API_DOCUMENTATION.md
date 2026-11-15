# PrivacyX — Repository & Cloud Function API Documentation

## Android Repositories (`com.example.data.repository`)
- **`AuthRepository`**:
  - `observeUserProfile(userId)`, `observePrivateAccount(userId)`, `observeUserSettings(userId)`, `observeSessions(userId)`
  - `isUsernameAvailable(rawUsername)`, `claimUsernameAndCreateProfile(...)` (atomic transaction)
  - `updateProfile(...)`, `updatePresence(isOnline)`, `updateUserSettings(...)`, `searchUsers(queryText)`
  - `exportUserDataJson()`, `revokeSession(sessionId)`, `deleteAccount()`
- **`MessagingRepository`**:
  - `observeMyConversations(userId)`, `observePublicDiscoveryConversations()`, `observeConversation(id)`
  - `observeMessages(conversationId, limit)`, `observePublicChannelMessages(conversationId, limit)`
  - `createOrGetDirectConversation(...)`, `createGroupOrCommunityOrChannel(...)`
  - `sendMessage(...)`, `editMessage(...)`, `deleteMessage(...)`, `toggleReaction(...)`, `votePoll(...)`, `togglePinMessage(...)`, `markMessagesRead(...)`, `createTopic(...)`
- **`SocialRepository`**:
  - `observeActiveStories(uid)`, `observeMyArchivedStories(uid)`, `createStory(...)`, `recordStoryView(...)`, `archiveOrHighlightStory(...)`
  - `observePosts()`, `createPost(...)`, `togglePostLike(...)`, `addPostComment(...)`
  - `toggleFollow(...)`, `toggleBlockUser(...)`, `submitReport(...)`, `saveMessageBookmark(...)`, `observeNotifications(uid)`
- **`CallRepository`**:
  - `observeMyCalls(uid)`, `observeCall(callId)`, `observeIceCandidates(callId)`
  - `createOutgoingCall(...)`, `answerCall(...)`, `updateCallStatus(...)`, `addIceCandidate(...)`
- **`AiAssistantRepository`**:
  - `observeAiSessions(uid)`, `observeAiMessages(uid, sessionId)`, `createAiSession(title)`, `saveAiTurn(...)`
  - `streamPrompt(prompt, history, attachedImage)` (60s timeout), `generateQuickText(prompt)`
- **`AdminRepository`** (Super Admin UID `2bL4lJTTt9WMILpCclHeKNYOdz62` enforced):
  - `setUserSuspended(...)`, `deleteConversationAsAdmin(...)`, `updateReportStatus(...)`, `publishAnnouncement(...)`, `updatePlatformSettings(...)`

## Cloud Functions (`/functions/src/index.ts`)
- `POST /signCloudinaryUpload`: Verifies Firebase ID token and returns SHA-1 Cloudinary upload signature.
- `onCall syncSuperAdminClaim`: Sets `superAdmin: true` claim strictly for UID `2bL4lJTTt9WMILpCclHeKNYOdz62`.
- `onDocumentCreated onMessageCreatedNotify`: Sends FCM push notifications to conversation recipients with stored `fcmToken`.
