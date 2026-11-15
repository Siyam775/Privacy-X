# PrivacyX — Security Rules Architecture

Security rules are defined in `firestore.rules` and verified against the local Firestore Emulator via both Node.js (`firestore.test.js`) and Android Robolectric (`PrivacyXRepositoryRuleTest.kt`).

## Core Security Guarantees
1. **Default Deny**: `match /{document=**} { allow read, write: if false; }` locks down all unmatched paths.
2. **Strict Super Admin Lock**:
   ```
   function isSuperAdmin() {
     return isSignedIn() && request.auth.uid == '2bL4lJTTt9WMILpCclHeKNYOdz62';
   }
   ```
   - No client-side role field (`role == 'admin'`) is trusted.
   - No user can promote themselves or another user to Super Admin.
   - `adminLogs` are strictly append-only (`allow update, delete: if false`).
3. **PII Isolation**: Email addresses, close-friends lists, hidden story user lists, and FCM tokens live exclusively in `users/{userId}/private/account` (`allow read, write: if isOwner(userId) || isSuperAdmin()`).
4. **Username Uniqueness**: `usernames/{username}` requires `!exists(...)` on creation and strict `userId == request.auth.uid`.
5. **Anti-Update-Gap Field Whitelisting**: Profile updates forbid modifying `isSuspended`, `userId`, or `createdAt`. Message updates by peers only allow reaction/receipt/poll-vote/pin keys.
6. **Conversation & Call Isolation**: Private DMs, groups, messages, and WebRTC signaling documents/ICE candidates are strictly restricted to `request.auth.uid in resource.data.memberUids` / `participantIds`.
7. **Sender Impersonation Prevention**: Message, story, post, comment, report, and call creation enforce `senderId == request.auth.uid` / `authorId == request.auth.uid` / `callerId == request.auth.uid`.
