# PrivacyX Web

Next.js web client for PrivacyX messaging and social features.

## Develop

```bash
npm install
npm run dev
```

Set the public Firebase and Supabase values listed in `.env.example` in a local `.env.local` file before using sign-in, chat, and media features. Keep service-role keys out of the browser build.

## Build and deploy

```bash
npm run build
```

The production build exports a static site to `out/`. See [DEPLOY_GITHUB_PAGES.md](./DEPLOY_GITHUB_PAGES.md) for GitHub Pages configuration.

Chat messages are cached in browser IndexedDB. Photo blobs are cached there as well; Firestore and Supabase remain the cloud stores.
