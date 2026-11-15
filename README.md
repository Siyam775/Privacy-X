# PrivacyX

A next-generation premium Android messaging, social, calling, and AI assistant application built with **Kotlin**, **Jetpack Compose**, **Material 3**, **Cloud Firestore**, **Firebase Auth (Google Credential Manager)**, **Firebase App Check**, **Firebase AI Logic**, **Stream WebRTC**, and **Cloudinary**.

## Key Features
- **3 Live-Switchable Themes**: Material Liquid Glass (default), Pure iOS Glass, and Classic Black & White + Light/Dark/System + Custom Accent + Reduced Motion.
- **Signature Radial Floating Action Menu**: 3-ring responsive radial launcher with spring physics, blurred/dimmed overlay, 12 actions, outside-tap & Back dismissal, and reduced-motion accessibility.
- **Real-Time Messaging**: 1:1 Direct Chats, Groups, Communities, Public/Private Channels, Discussion Topics, Polls, Voice Notes (`MediaRecorder`/`MediaPlayer` 1x/1.5x/2x), Photo Picker & SAF Document Sharing, Reply/Forward/Edit/Delete/Pin/Reactions/Translate/Summarize/Smart Replies, Disappearing & Scheduled options, Draft Persistence.
- **Voice & Video Calling**: `io.getstream:stream-webrtc-android` PeerConnection with Firestore SDP/ICE signaling, camera switch, mute, speaker, and call history.
- **Stories & Social Feed**: 24-hour expiring Stories with privacy controls (`public`, `followers`, `close_friends`, custom, hide users), Archive & Highlights, Viewer Analytics, plus a separated Social Feed with posts, comments, and collections.
- **Built-In AI Assistant**: Streaming Gemini responses (`Firebase.ai`) + multimodal image explanation, persisted AI conversation history, and explicit AI privacy consent.
- **Strict Super Admin Dashboard**: Locked strictly to Firebase Auth UID `2bL4lJTTt9WMILpCclHeKNYOdz62` in both `firestore.rules` and UI/Repository layers, with user management, report moderation, announcements, global settings, and immutable audit logs.

## Documentation Index
- `PROJECT_ARCHITECTURE.md`
- `IMPLEMENTATION_STATUS.md`
- `FIREBASE_SETUP.md`
- `DATABASE_SCHEMA.md`
- `SECURITY_RULES.md`
- `STORAGE_SETUP.md`
- `CALLING_SETUP.md`
- `API_DOCUMENTATION.md`
- `ENVIRONMENT_SETUP.md`
- `TEST_REPORT.md`
- `KNOWN_LIMITATIONS.md`
