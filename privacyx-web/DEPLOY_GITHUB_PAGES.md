# Deploy PrivacyX Web to GitHub Pages

The repository workflow at `.github/workflows/deploy-privacyx-web.yml` builds `privacyx-web` as a static Next.js export and deploys the `out/` directory whenever `main` or `master` changes.

## GitHub setup

1. Push the repository with the `privacyx-web` directory to GitHub.
2. In **Settings → Pages**, set the publishing source to **GitHub Actions**.
3. Add the public Firebase and Supabase configuration values below under **Settings → Secrets and variables → Actions → Variables**. These values are compiled into the public website; never put a private service key here.
4. Push to `main` or `master`, then check the **Actions** tab for the deployment URL.
5. Add the deployed `owner.github.io` domain to Firebase Authentication's authorized domains and configure Google sign-in's redirect domain as required by Firebase.

The workflow reads these optional public Actions variables:

- `NEXT_PUBLIC_FIREBASE_API_KEY`
- `NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN`
- `NEXT_PUBLIC_FIREBASE_PROJECT_ID`
- `NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET`
- `NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID`
- `NEXT_PUBLIC_FIREBASE_APP_ID`
- `NEXT_PUBLIC_FIREBASE_DATABASE_ID`
- `NEXT_PUBLIC_FIREBASE_RECAPTCHA_SITE_KEY`
- `NEXT_PUBLIC_SUPER_ADMIN_UID`
- `NEXT_PUBLIC_SUPABASE_URL`
- `NEXT_PUBLIC_SUPABASE_ANON_KEY`
- `NEXT_PUBLIC_SUPABASE_MEDIA_BRIDGE_URL`
- `NEXT_PUBLIC_AI_API_URL`

For a project site such as `owner.github.io/repository`, the build sets the matching Next.js base path automatically. User and organization sites named `owner.github.io` use the root path.

## AI backend

GitHub Pages only serves static files. The AI page therefore needs an external HTTPS backend if AI should work after deployment. Set `NEXT_PUBLIC_AI_API_URL` to its base URL; it must accept CORS requests from the Pages domain and implement `POST /stream` (plain-text response stream) and `POST /generate` (JSON response with a `text` field). Keep the Gemini API key on that backend, never in a `NEXT_PUBLIC_*` variable.

Chat, Firebase auth/data, and Supabase media continue to use their client SDKs. Firebase rules, authorized domains, and Supabase storage/CORS policies still need to allow the deployed site.

## Local export

Run `npm run build` inside `privacyx-web`. Next.js writes the deployable static site to `privacyx-web/out/`.
