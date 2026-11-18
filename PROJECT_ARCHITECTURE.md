# PrivacyX — Project Architecture

## Overview
PrivacyX is a native Android messaging, social, calling, and AI assistant application built with **Kotlin**, **Jetpack Compose**, **Material 3**, **Firebase Authentication (Credential Manager Google Sign-In)**, **Cloud Firestore**, **Firebase App Check**, **Firebase AI Logic (`Firebase.ai`)**, **Stream WebRTC (`io.getstream:stream-webrtc-android`)**, **CameraX**, **Supabase Storage (`chat-media` private bucket)**, **Room**, **WorkManager**, and **Google Drive Backup**.

## Architectural Layers (MVVM + Repository Pattern)

```
+-------------------------------------------------------------------+
|                     UI Layer (Jetpack Compose)                    |
|  Auth / Onboarding / Chats / ChatDetail / Discover / Stories /    |
|  Communities / Calls / Profile & Settings / StorageAndBackup /    |
|  AI / Super Admin / RadialFabMenuOverlay / Adaptive NavBar & Rail |
+-------------------------------------------------------------------+
                                 |
                                 v
+-------------------------------------------------------------------+
|                   ViewModel Layer (StateFlow)                     |
|  AppViewModel | ChatViewModel | SocialViewModel | CallViewModel   |
|  AiViewModel  | AdminViewModel                                    |
+-------------------------------------------------------------------+
                                 |
                                 v
+-------------------------------------------------------------------+
|          Repository, Backup & Hardware Services                   |
|  AuthRepository       | MessagingRepository  | SocialRepository   |
|  CallRepository       | AiAssistantRepository| AdminRepository    |
|  LocalMediaRepository | SupabaseMediaService | VoiceRecorderPlayer|
|  GoogleDriveBackupManager | BackupCryptoZip  | WebRtcCallManager  |
+-------------------------------------------------------------------+
                                 |
                                 v
+-------------------------------------------------------------------+
|            Backend, Local Persistence & Cloud Services            |
|  Cloud Firestore (Named DB) | Room DB (Media/Cache/Outbox)        |
|  Firebase Auth + App Check  | Firebase AI Logic (Gemini)          |
|  Supabase Storage (`chat-media` private bucket) + Edge Functions  |
|  Cloud Functions (/functions) | Google Drive (`drive.file`)       |
|  WorkManager Scheduled Backups | WebRTC STUN/TURN                 |
+-------------------------------------------------------------------+
```

## Theme System Architecture
Three live-switchable visual themes are implemented via `PrivacyXThemeTokens` and `LocalPrivacyXTokens`:
1. **Material Liquid Glass** (Default): Lavender, violet, soft pink, and mint ambient background gradients with frosted glass cards and specular borders.
2. **Pure iOS Glass**: Deep translucent crystal surfaces, crisp highlights, and soft frosted layering.
3. **Classic Black & White**: Flat high-contrast monochrome without ambient gradients or glass blur.

Preferences (`themeId`, `appearanceMode`, `accentColorHex`, `reducedMotion`) persist locally in Jetpack DataStore (`ThemePreferencesStore`) and synchronize to Firestore (`users/{uid}/userSettings/preferences`).

## Signature Radial Floating Action Menu
Implemented in `RadialFabMenu.kt` using `BoxWithConstraints` responsive arc geometry across 3 concentric rings (12 actions), staggered spring physics, blurred/dimmed tap-blocking backdrop, morphing close button, outside-tap dismissal, `BackHandler` integration, and reduced-motion accessibility fallback.

## Local-First Media & 7-Day Cloud Retention
- `SupabaseMediaService` optimizes images (Exif orientation fix, <=1600px, WebP encoding, plus 320px WebP thumbnail), uploads Android chat videos to cloud storage, and exchanges Firebase ID tokens with the backend bridge (`SUPABASE_MEDIA_BRIDGE_URL`) for short-lived signed upload/read URLs.
- `LocalMediaDatabase` (Room) stores cached messages and the offline outbox. Uploaded photos are kept in `filesDir/local_media/<conversationId>/` beside their cloud copies; video, voice, and document staging files are removed after successful upload. Explicit media downloads can still be saved locally.
- Cloud objects expire after 7 days (`mediaExpiresAt`, `mediaExpired`); expired cloud media gracefully resolves to local saved copies, `"Available in Backup"`, or a calm `"Media unavailable"` state while keeping text intact.

## Google Drive Backup & Restore
- `GoogleDriveBackupManager` and `BackupCryptoZip` package `manifest.json`, `conversations.json`, `messages.json`, `settings.json`, `media_metadata.json`, and optional `media/*` inside a ZIP archive (optionally encrypted with AES-256-GCM + PBKDF2WithHmacSHA256 using `PXBK1` header).
- Restores populate Room local media and cached conversations directly without re-uploading files to Supabase Storage or resetting cloud expiry.

## Adaptive Layout & Edge-to-Edge
- `enableEdgeToEdge()` enabled in `MainActivity`.
- Bottom floating capsule navigation applies `WindowInsets.navigationBars`.
- Tablet / landscape widths switch to `NavigationRail` + split-friendly containers.
