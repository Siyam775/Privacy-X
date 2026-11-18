# PrivacyX — Media, Storage & Backup Architecture (Supabase + Local + Google Drive)

## 1. Supabase Storage Pipeline (`SupabaseMediaService.kt`)
- **Bucket**: Private bucket `chat-media` (never public; RLS enabled in `/supabase/migrations/20261116_chat_media.sql`).
- **Authentication Bridge**:
  - Android client obtains a fresh Firebase ID token (`FirebaseAuth.currentUser?.getIdToken(false)`).
  - Backend bridge (`authorizeSupabaseMedia` in `/functions/src/index.ts` or `/supabase/functions/media-bridge/index.ts`) verifies the Firebase JWT, checks conversation membership in Firestore, enforces MIME/size rules, creates `media_objects` / `mediaObjects` rows with `expiresAt = now + 7 days`, and returns short-lived signed upload URLs + signed read URLs.
  - `SUPABASE_SERVICE_ROLE_KEY` is stored strictly in backend Secrets and **never** shipped inside the Android APK.
- **Android Chat Video Storage**:
  - Android chat videos are stored in the private `chat-media` bucket and remain cloud-only unless explicitly downloaded. Message text and uploaded photos are retained locally alongside cloud copies.
  - Apply `supabase/migrations/20261118_video_chat_media.sql` to allow supported video MIME types in the bucket. The 25 MB upload cap and 7-day cloud expiration still apply.
  - Stories remain image/text only. WebRTC voice and video calling remains unaffected.
- **Image Optimization & Thumbnails**:
  - Corrects Exif rotation, scales images proportionally to max 1600px, and encodes WebP targeting low storage footprint (100–400 KB range) alongside a 320px WebP thumbnail (`_thumb.webp`).
  - Maximum upload limit validated against `platformSettings.maxUploadMb` (default 25 MB, hard cap 25 MB).

## 2. 7-Day Cloud Media Expiration
- Cloud media records carry `mediaId`, `mediaExpiresAt` (epoch ms), and `mediaExpired` (`Boolean`).
- Cleanup endpoints (`cleanupExpiredCloudMedia` / `triggerExpiredMediaCleanup` / `cleanup-expired-media`):
  1. Delete main object and thumbnail object from Supabase `chat-media` first.
  2. Only after storage deletion succeeds, mark `deletedConfirmed = true` and update Firestore message fields (`mediaUrl = ""`, `mediaThumbnailUrl = ""`, `mediaExpired = true`) while **preserving message text and metadata**.
- Client (`MediaAvailabilityState`):
  1. Uses saved local file in `filesDir/local_media/<conversationId>/` whenever present, even after 7-day cloud expiry.
  2. Otherwise downloads from Supabase signed URL (cache-first in `cacheDir` or persistent save via Download action -> internal storage + `MediaStore.Downloads/PrivacyX`).
  3. If cloud media has expired and no local file exists, displays `"Available in Backup"` (when indexed in backup metadata) or a calm `"Media unavailable"` placeholder.

## 3. Local-First Room Storage & Cache Isolation
- `LocalMediaDatabase` indexes downloaded/saved media (`LocalMediaEntity`), cached conversations/messages (`CachedMessageEntity`), and offline outgoing messages (`PendingOutgoingMessageEntity`).
- `"Clear Temporary Cache"` clears `context.cacheDir` only—never deleting `filesDir/local_media/` or saved wallpapers.

## 4. Google Drive Backup & Local ZIP Export (`GoogleDriveBackupManager.kt`)
- Authorizes access cleanly via Google Play Services `Identity.getAuthorizationClient` with `https://www.googleapis.com/auth/drive.file` (strictly app-created files inside `My Messaging App Backups` folder).
- Produces ZIP archives (`BackupCryptoZip.kt`) containing `manifest.json`, `conversations.json`, `messages.json`, `settings.json`, `media_metadata.json`, and optional `media/*`.
- Supports optional AES-256-GCM encryption (`PBKDF2WithHmacSHA256`, 65,536 iterations, `PXBK1` header; passphrase never stored on disk).
- Restoring writes metadata and files directly to Room and `filesDir/local_media` without re-uploading to Supabase or altering cloud expiration.

## 5. Configuration via AI Studio Secrets Panel
Set these variables in the **AI Studio Secrets panel** (declared in `.env.example`, injected via `BuildConfig`):
- `SUPABASE_URL`: Your Supabase project URL (e.g., `https://<ref>.supabase.co`).
- `SUPABASE_ANON_KEY`: Your Supabase project anon/publishable key.
- `SUPABASE_MEDIA_BRIDGE_URL`: HTTPS URL of the deployed `authorizeSupabaseMedia` Cloud Function or `media-bridge` Supabase Edge Function.
