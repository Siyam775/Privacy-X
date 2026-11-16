# PrivacyX — Environment Setup

## Build & Runtime Requirements
- **Android Studio / Gradle**: AGP 9.1.1, Kotlin 2.2.10, `compileSdk = 36`, `minSdk = 24`, `targetSdk = 36`.
- **Application ID**: `com.privacy.x` (namespace `com.example`).

## Secrets Configuration (AI Studio Secrets Panel)
All optional third-party secrets are declared in `.env.example` and injected via Secrets Gradle Plugin into `BuildConfig`. **Do not use `local.properties` for secrets, and never place `SUPABASE_SERVICE_ROLE_KEY` in client code.**

| Variable | Purpose | Required? |
|---|---|---|
| `SUPABASE_URL` | Supabase project base URL for `chat-media` storage | Required for remote media uploads/downloads |
| `SUPABASE_ANON_KEY` | Supabase public anon/publishable key | Required for Supabase client headers |
| `SUPABASE_MEDIA_BRIDGE_URL` | Verified Firebase ID token -> Supabase signed URL bridge URL (`authorizeSupabaseMedia` or `media-bridge`) | Required for remote media uploads/downloads |
| `TURN_SERVER_URL` | WebRTC TURN relay URL | Optional (Google STUN enabled by default) |
| `TURN_USERNAME` | WebRTC TURN username | Optional |
| `TURN_CREDENTIAL` | WebRTC TURN password/credential | Optional |

## Gemini AI Setup
No `GEMINI_API_KEY` is needed. AI Assistant features run through **Firebase AI Logic (`Firebase.ai`)** authenticated by **Firebase App Check**.
