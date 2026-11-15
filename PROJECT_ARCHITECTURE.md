# PrivacyX — Project Architecture

## Overview
PrivacyX is a native Android messaging, social, calling, and AI assistant application built with **Kotlin**, **Jetpack Compose**, **Material 3**, **Firebase Authentication (Credential Manager Google Sign-In)**, **Cloud Firestore**, **Firebase App Check**, **Firebase AI Logic (`Firebase.ai`)**, **Stream WebRTC (`io.getstream:stream-webrtc-android`)**, **CameraX**, and **Cloudinary** media transport.

## Architectural Layers (MVVM + Repository Pattern)

```
+-------------------------------------------------------------------+
|                     UI Layer (Jetpack Compose)                    |
|  Auth / Onboarding / Chats / ChatDetail / Discover / Stories /    |
|  Communities / Calls / Profile & Settings / AI / Super Admin /    |
|  RadialFabMenuOverlay / Adaptive Capsule NavBar & NavRail         |
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
|                 Repository & Hardware Services                    |
|  AuthRepository      | MessagingRepository  | SocialRepository    |
|  CallRepository      | AiAssistantRepository| AdminRepository     |
|  CloudinaryMediaService | VoiceRecorderPlayer | WebRtcCallManager |
+-------------------------------------------------------------------+
                                 |
                                 v
+-------------------------------------------------------------------+
|            Backend, Local Persistence & Cloud Services            |
|  Cloud Firestore (Named DB) | Firebase Auth | Firebase App Check  |
|  Firebase AI Logic (Gemini) | DataStore Prefs | WebRTC STUN/TURN  |
|  Cloudinary HTTPS Upload    | Cloud Functions (/functions)        |
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

## Adaptive Layout & Edge-to-Edge
- `enableEdgeToEdge()` enabled in `MainActivity`.
- Bottom floating capsule navigation applies `WindowInsets.navigationBars`.
- Tablet / landscape widths switch to `NavigationRail` + split-friendly containers.
