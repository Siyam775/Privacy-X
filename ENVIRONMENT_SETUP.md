# PrivacyX — Environment Setup

## Build & Runtime Requirements
- **Android Studio / Gradle**: AGP 9.1.1, Kotlin 2.2.10, `compileSdk = 36`, `minSdk = 24`, `targetSdk = 36`.
- **Application ID**: `com.privacy.x` (namespace `com.example`).

## Secrets Configuration (AI Studio Secrets Panel)
All optional third-party secrets are declared in `.env.example` and injected via Secrets Gradle Plugin into `BuildConfig`. **Do not use `local.properties` for secrets.**

| Variable | Purpose | Required? |
|---|---|---|
| `CLOUDINARY_CLOUD_NAME` | Cloudinary media upload host | Required for remote media uploads |
| `CLOUDINARY_SIGNING_ENDPOINT` | Cloud Function URL for signed uploads | Recommended for production media |
| `CLOUDINARY_UPLOAD_PRESET` | Unsigned preset (dev alternative) | Optional if signing endpoint set |
| `TURN_SERVER_URL` | WebRTC TURN relay URL | Optional (Google STUN enabled by default) |
| `TURN_USERNAME` | WebRTC TURN username | Optional |
| `TURN_CREDENTIAL` | WebRTC TURN password/credential | Optional |

## Gemini AI Setup
No `GEMINI_API_KEY` is needed. AI Assistant features run through **Firebase AI Logic (`Firebase.ai`)** authenticated by **Firebase App Check**.
