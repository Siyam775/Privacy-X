# PrivacyX — Firebase & Supabase Backend Setup

## Provisioned Firebase Project Details
- **Firebase Project ID**: `privacy-x`
- **Android Application ID**: `com.privacy.x`
- **Named Firestore Database ID**: `ai-studio-android-privacyx-5767d797-31a9-488e-9a25-d28909171c2e`
- **Config Files**:
  - `app/google-services.json` (contains Android OAuth client + SHA-1 debug certificate hash `4462c42805fe8a70eaf5506abeb421924eaf06a6` and Web Client ID `default_web_client_id`)
  - `app/src/main/res/values/firebase_applet_config.xml` (`firestore_database_id` and `recaptcha_site_key`)

## Authentication
- **Provider**: Google Sign-In via Android Jetpack `CredentialManager` (`GetGoogleIdOption` and `GetSignInWithGoogleOption`).
- Anonymous authentication and email/password authentication are intentionally **not** used in application runtime code.
- Google Drive backup authorization uses Play Services `Identity.getAuthorizationClient` requesting `https://www.googleapis.com/auth/drive.file`.

## Firebase App Check
- Initialized in `MainActivity.onCreate()` via `configureAppCheck(this, intent)` **before** any Compose UI or Firebase calls.
- **Debug builds**: `DebugAppCheckProviderFactory` (supports `FIREBASE_APPCHECK_DEBUG_TOKEN` intent extra).
- **Release builds**: `RecaptchaAppCheckProviderFactory` using `R.string.recaptcha_site_key`.

## Firebase AI Logic
- Uses `Firebase.ai.generativeModel("gemini-3.8-flash")` without any client-side `GEMINI_API_KEY`.
- Authenticated by Firebase App Check attestation.

## Supabase Setup (`/supabase`)
Apply SQL migration `/supabase/migrations/20261116_chat_media.sql` on your existing Supabase project to provision private bucket `chat-media`, table `media_objects`, and RLS policies.
Deploy the Supabase Edge Functions if hosting the bridge on Supabase:
```bash
supabase secrets set FIREBASE_PROJECT_ID=privacy-x SUPABASE_SERVICE_ROLE_KEY=<service_role_key> CLEANUP_CRON_SECRET=<cron_secret>
supabase functions deploy media-bridge
supabase functions deploy cleanup-expired-media
```

## Firebase Cloud Functions (`/functions`)
Optionally deploy the Cloud Functions bridge, cleanup endpoints, and FCM dispatcher:
```bash
cd functions
npm install
npm run build
firebase functions:secrets:set SUPABASE_URL
firebase functions:secrets:set SUPABASE_SERVICE_ROLE_KEY
firebase deploy --only functions
```
