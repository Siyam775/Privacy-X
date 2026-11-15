# PrivacyX — Voice & Video Calling Setup (WebRTC + Firestore Signaling)

## Implementation
- **Media Engine**: `io.getstream:stream-webrtc-android:1.3.8` in `WebRtcCallManager.kt`.
- **Signaling Transport**: Cloud Firestore `calls/{callId}` (SDP Offer / Answer + Call Status) and `calls/{callId}/candidates/{candidateId}` (trickle ICE candidates) in `CallRepository.kt`.
- **Permissions**: Runtime Compose permission requests for `RECORD_AUDIO` (voice & video calls) and `CAMERA` (video calls).
- **Controls**: Accept, Reject, End Call, Mute/Unmute Microphone, Speakerphone Toggle, Camera On/Off, Front/Rear Camera Switch (`Camera2Enumerator`), real-time call duration timer, and Firestore call history.

## STUN / TURN Configuration
- **Default STUN**: `stun:stun.l.google.com:19302` and `stun:stun1.l.google.com:19302` are enabled out of the box.
- **Optional TURN Relay (for symmetric NAT / strict cellular networks)**:
  Configure in the **AI Studio Secrets panel**:
  - `TURN_SERVER_URL` (e.g., `turn:your-turn-host.com:3478`)
  - `TURN_USERNAME`
  - `TURN_CREDENTIAL`
