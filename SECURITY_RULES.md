# PrivacyX — Security Rules Architecture

Security rules are defined in `firestore.rules` (Firestore) and `/supabase/migrations/20261116_chat_media.sql` (Supabase Storage/DB), and verified against the local Firestore Emulator via both Node.js (`firestore.test.js`) and Android Robolectric (`PrivacyXRepositoryRuleTest.kt`).

## Core Security Guarantees
1. **Default Deny**: `match /{document=**} { allow read, write: if false; }` locks down all unmatched Firestore paths, and Supabase `chat-media` bucket + `media_objects` table enforce RLS deny-by-default.
2. **Strict Super Admin Lock**:
   ```
   function isSuperAdmin() {
     return isSignedIn() && request.auth.uid == '2bL4lJTTt9WMILpCclHeKNYOdz62';
   }
   ```
   - No client-side role field is trusted.
   - `adminLogs` are strictly append-only (`allow update, delete: if false`).
3. **Trusted Firebase -> Supabase Bridge**:
   - Supabase service-role credentials are never included in Android client code.
   - `authorizeSupabaseMedia` / `media-bridge` verify Firebase ID tokens cryptographically, check conversation membership, and return short-lived signed upload/read URLs.
4. **Cloud Media Validation**:
   - Firestore accepts video as a chat message media type. The verified media bridge enforces conversation membership, size limits, and the `video/*` MIME type for video uploads.
   - Stories remain image/text only. The Supabase bucket MIME allowlist remains limited to supported formats.
5. **PII Isolation & Username Uniqueness**: Email addresses, close-friends lists, hidden story user lists, and FCM tokens live exclusively in `users/{userId}/private/account`; `usernames/{username}` requires `!exists(...)` and matching `userId == request.auth.uid`.
6. **Conversation & Call Isolation**: Private DMs, groups, messages, and WebRTC signaling documents/ICE candidates are strictly restricted to `request.auth.uid in resource.data.memberUids` / `participantIds`.
7. **Sender Impersonation Prevention**: Message, story, post, comment, report, and call creation enforce `senderId == request.auth.uid` / `authorId == request.auth.uid` / `callerId == request.auth.uid`.
8. **Optional Backup Encryption**: Client-side Google Drive and local ZIP backups support AES-256-GCM (`PBKDF2WithHmacSHA256`) encryption without storing passphrases on disk.
