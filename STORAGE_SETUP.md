# PrivacyX — Media & Storage Setup (Cloudinary)

## Architecture
- Client-side validation & upload pipeline lives in `CloudinaryMediaService.kt`.
- Media picker uses zero-permission Android Photo Picker (`PickVisualMedia`) and Storage Access Framework (`OpenDocument`). No broad storage permissions (`READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`) are requested.
- Enforces file size validation (configurable via Super Admin `platformSettings.maxUploadMb`, default 25 MB) and MIME allowlist (`image/*`, `video/*`, `audio/*`, PDF, DOC/DOCX, XLS/XLSX, PPT/PPTX, TXT, ZIP).
- Supports optional image compression (max 1600px, JPEG 82%), live upload progress (`ProgressFileRequestBody`), cancellation (`suspendCancellableCoroutine` + `call.cancel()`), and retry.

## Configuration via AI Studio Secrets Panel
Set these variables in the **AI Studio Secrets panel** (never commit secrets in source code):
- `CLOUDINARY_CLOUD_NAME`: Your Cloudinary cloud name.
- **Recommended (Signed Upload)**:
  - `CLOUDINARY_SIGNING_ENDPOINT`: HTTPS URL of the deployed `signCloudinaryUpload` Firebase Cloud Function (`/functions/src/index.ts`).
  - Backend secret (`CLOUDINARY_API_KEY` and `CLOUDINARY_API_SECRET`) lives exclusively in Firebase Cloud Functions secrets—never in the Android APK.
- **Alternative (Unsigned Preset for Development)**:
  - `CLOUDINARY_UPLOAD_PRESET`: Unsigned upload preset configured in Cloudinary Console.

If Cloudinary credentials are unconfigured (`MY_*` placeholders), the UI surfaces a clear setup prompt instead of faking remote URLs.
