# PrivacyX — Implementation Status

## 1. Completed Core Architecture & Platform Setup
- **Package & Application ID**: `com.privacy.x` (namespace `com.example`) configured in `app/build.gradle.kts`, `app/google-services.json`, `metadata.json`, and `settings.gradle.kts`.
- **Custom Fonts & Launcher Icon**: Bundled `plus_jakarta_sans.ttf`, `space_grotesk.ttf`, `jetbrains_mono.ttf`, and custom adaptive launcher icons across all `mipmap-*` densities.
- **Firebase App Check**: `configureAppCheck(this, intent)` invoked in `MainActivity.onCreate()` prior to `setContent` (DebugAppCheckProviderFactory in debug, RecaptchaAppCheckProviderFactory with `recaptcha_site_key` in release).

## 2. Three Live-Switchable Themes
- **Themes Implemented**:
  1. `MATERIAL_LIQUID_GLASS` (Default — translucent lavender-violet liquid glass surfaces, multi-stop radial orbs, purple gradient FAB and bubbles)
  2. `PURE_IOS_GLASS` (iOS 26 ultra-frosted translucent glass surfaces with deep blur & crisp 1dp border)
  3. `CLASSIC_BW` (Pure crisp monochrome Light/Dark zero-blur contrast mode)
- **Live Controls**: Light / Dark / System mode, custom accent hex color swatches, Reduced Motion toggle, persisted locally via Jetpack DataStore (`ThemePreferencesStore`) and synced to Firestore `userSettings`.

## 3. Signature Radial Floating Action Menu
- `RadialFabMenuOverlay` expands 12 interactive actions in 3 concentric rings above the bottom-right purple gradient FAB:
  - **Ring 1**: New Chat, Voice Call, AI Assist
  - **Ring 2**: Group, Community, Story, Image, Document
  - **Ring 3**: Video Call, Theme, Saved, Settings
- Includes blurred/dimmed tap-blocking backdrop scrim, outside-tap dismissal, Android `BackHandler` dismissal, and Reduced Motion spring bypass.

## 4. Authentication & User Accounts
- Google Sign-In implemented exclusively via Jetpack `CredentialManager` (`GetSignInWithGoogleOption` + `GetGoogleIdOption` fallback).
- Atomic `@username` reservation (`usernames/{username}` + `users/{uid}` + `users/{uid}/private/account` + `userSettings/{uid}` via Firestore transaction).
- Live presence tracking (`isOnline`, `lastSeen`), active device sessions list & revocation, JSON account data export via `FileProvider`, local cache clearing, and account deletion.

## 5. Real-Time Messaging, Media & Groups/Channels
- 1:1 Direct Messages, Groups, Communities, Public/Private Channels, and Discussion Topics.
- Paginated message streams, read/delivered receipts, typing indicators, reply/forward/edit/delete/copy/pin/react/bookmark, inline polls with voting, disappearing message timers, scheduled messages, and per-chat draft persistence.
- Media capture via zero-permission Android Photo Picker (`PickVisualMedia`) and SAF (`OpenDocument`), image compression toggle, upload progress & cancel/retry bar, real `MediaRecorder`/`MediaPlayer` voice notes (pause/resume/cancel, 1x/1.5x/2x playback), full-screen pinch-to-zoom image viewer, and `VideoView` player.
- Cloudinary upload client (`CloudinaryMediaService`) supporting unsigned preset uploads and Firebase-authenticated signed upload signature requests.

## 6. Voice & Video Calling
- WebRTC (`io.getstream:stream-webrtc-android:1.3.8`) via `WebRtcCallManager` (`PeerConnectionFactory`, `Camera2Enumerator`, `EglBase`, Google STUN + optional `BuildConfig` TURN).
- Firestore SDP offer/answer + `iceCandidates` subcollection signaling, runtime `RECORD_AUDIO` and `CAMERA` permission checks, incoming call banner, full-screen video/audio call overlay with mute/speaker/camera/flip/duration timer, and call history.

## 7. Stories & Social Feed
- 24-hour expiring Stories with text gradients, Photo Picker media, privacy scopes (`public`, `followers`, `close_friends`, `custom`, hidden users), segmented progress bar viewer, emoji reactions, viewer counts/list, archive, and highlights.
- Separated Social Feed (`DiscoverAndSocialScreen`) with posts, collection tags, likes, comments, follow/unfollow, block/unblock, report submission, and Social Profile Popup.

## 8. Built-In AI Assistant (Firebase AI Logic)
- Implemented via `Firebase.ai.generativeModel("gemini-3.8-flash")` in `AiAssistantRepository` with streaming responses, multimodal `Bitmap` image analysis, per-user persisted AI sessions (`users/{uid}/aiConversations`), explicit AI privacy consent toggle, and in-chat summarize / translate / smart-reply helpers.

## 9. Super Admin Governance
- Strictly UID-locked to **`2bL4lJTTt9WMILpCclHeKNYOdz62`** across `firestore.rules` (`isSuperAdmin()`), `AdminRepository`, `AdminViewModel`, and `SuperAdminDashboardScreen` (user suspension, conversation deletion, abuse report triage, platform announcements, global settings, immutable audit logs).

## 10. Cloud Functions Scaffold (`/functions`)
- TypeScript Firebase Cloud Functions scaffolded for `signCloudinaryUpload`, `syncSuperAdminClaim`, and `onMessageCreatedNotify` (FCM push dispatch).
