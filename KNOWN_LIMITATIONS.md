# PrivacyX — Known Limitations & External Dependencies

This report documents every external dependency, credential requirement, and environment limitation transparently without exaggeration.

## External Credentials & Services (Optional / User-Configured)
1. **Cloudinary Media Uploads (`CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_SIGNING_ENDPOINT` / `CLOUDINARY_UPLOAD_PRESET`)**:
   - Client validation, image compression, progress tracking, cancellation, retry, and signed HTTPS upload logic are fully implemented in `CloudinaryMediaService.kt`.
   - Remote media upload requires the user to configure Cloudinary variables in the **AI Studio Secrets panel** (and deploy `/functions/src/index.ts` for production signed uploads). Until configured, attempting to upload media surfaces a clear configuration error instead of fabricating URLs.
2. **WebRTC TURN Relay (`TURN_SERVER_URL`, `TURN_USERNAME`, `TURN_CREDENTIAL`)**:
   - Peer-to-peer WebRTC signaling (`CallRepository` + `WebRtcCallManager`) uses public Google STUN servers (`stun.l.google.com:19302`) by default.
   - Calls across symmetric NATs or restrictive carrier firewalls require configuring a TURN server via the AI Studio Secrets panel.
3. **Remote FCM Push Notifications & Automated Scheduled Triggers**:
   - In-app notifications and local Android notification posting (`NotificationHelper`) run directly on-device via Firestore listeners and `POST_NOTIFICATIONS`.
   - Background push delivery when the app is terminated requires deploying `/functions/src/index.ts` (`onMessageCreatedNotify`) to Firebase Cloud Functions (Blaze plan) and registering device FCM tokens.
   - 24-hour Story expiry and Disappearing Message TTL are enforced immediately in client queries/rendering; automated server-side document purging requires a scheduled Cloud Function.

## Platform & Testing Environment Constraints
- **No Physical Emulator / ADB in Container**: The cloud build environment does not expose ADB or an interactive emulator to the agent; UI/repository verification relies on Gradle compilation, Firebase Emulator Security Rules tests (Node.js + Robolectric), and JVM unit tests.
- **Google Sign-In Interactive Flow**: `CredentialManager` Google Sign-In (`GetGoogleIdOption` + `GetSignInWithGoogleOption`) requires a Google Play Services device/emulator and SHA-1 registered in `app/google-services.json` (already provisioned with debug SHA-1 `44:62:C4:28:05:FE:8A:70:EA:F5:50:6A:BE:B4:21:92:4E:AF:06:A6`).
- **End-to-End Encryption (E2EE)**: Transport uses TLS/HTTPS + strict Firestore Security Rules + App Check; true client-side Signal Double-Ratchet key exchange is not implemented.
