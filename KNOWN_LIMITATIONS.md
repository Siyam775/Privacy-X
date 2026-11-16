# PrivacyX — Known Limitations & External Dependencies

This report documents every external dependency, credential requirement, free-tier constraint, and environment boundary transparently.

## External Credentials & Services (User-Configured)
1. **Supabase Private Storage (`SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_MEDIA_BRIDGE_URL`)**:
   - Client-side WebP optimization, thumbnail generation, video blocking, Room indexing, and bridge protocol are fully implemented in `SupabaseMediaService.kt`.
   - Remote media upload/download requires setting `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `SUPABASE_MEDIA_BRIDGE_URL` in the **AI Studio Secrets panel**, applying `/supabase/migrations/20261116_chat_media.sql`, and deploying either `/supabase/functions/media-bridge` or Firebase Cloud Function `authorizeSupabaseMedia`. Until configured, local drafts/cache and clear configuration errors surface instead of fabricating URLs.
2. **Free-Tier Automated Cron Scheduling Limitation (7-Day Auto-Delete)**:
   - Deletion logic is completely implemented in both `/supabase/functions/cleanup-expired-media/index.ts` and `/functions/src/index.ts` (`cleanupExpiredCloudMedia` / `triggerExpiredMediaCleanup`), ensuring storage objects are deleted **before** Firestore metadata is marked expired.
   - **Free Tier Notice**: Scheduled Firebase Cloud Functions (`onSchedule`) require Firebase's Blaze plan, and Supabase `pg_cron` / scheduled edge triggers require enabling cron scheduling on your Supabase project. Without enabling paid billing or an external free scheduler (such as GitHub Actions cron calling `cleanup-expired-media` or authenticated `triggerExpiredMediaCleanup`), server-side deletion runs on manual/HTTP trigger rather than automatically on cron. No paid billing has been enabled by default.
3. **Google Drive Backup Scope & OAuth Consent**:
   - Implemented using Play Services `Identity.getAuthorizationClient` with `https://www.googleapis.com/auth/drive.file`.
   - Scheduled `WorkManager` backups (`ScheduledBackupWorker`) and app-open due checks run automatically only while Drive authorization remains granted; encrypted backups (`PXBK1` AES-256-GCM) do not store passphrases on disk, so forgotten passphrases cannot recover encrypted archives.
4. **WebRTC TURN Relay (`TURN_SERVER_URL`, `TURN_USERNAME`, `TURN_CREDENTIAL`)**:
   - Peer-to-peer WebRTC voice & video calling uses public Google STUN servers (`stun.l.google.com:19302`) by default; calls across symmetric NATs require optional TURN credentials.
5. **Remote FCM Push Notifications**:
   - Local notifications run directly on-device via Firestore listeners (`NotificationHelper`), while background push when terminated requires deploying `onMessageCreatedNotify` in `/functions/src/index.ts`.

## Platform & Testing Environment Constraints
- **No Physical Emulator / ADB in Container**: The cloud build environment does not expose ADB or an interactive emulator to the agent; verification relies on Gradle compilation/APK packaging, Firebase Emulator Security Rules tests (Node.js + Robolectric), Room/Backup/Media unit tests, and Robolectric Compose UI tests.
- **Google Sign-In Interactive Flow**: `CredentialManager` Google Sign-In and Play Services Drive Authorization require a Google Play Services device with matching SHA-1 registered in `app/google-services.json`.
- **Video Uploads Intentionally Disabled in Messaging & Stories**: Enforced client-side, UI-side, backend-side, and in `firestore.rules`. Live WebRTC video calling remains active.
