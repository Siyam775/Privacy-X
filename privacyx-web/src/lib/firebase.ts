import { initializeApp, getApps, FirebaseApp } from "firebase/app";
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signInWithRedirect,
  getRedirectResult,
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
  sendPasswordResetEmail,
  updateProfile,
  setPersistence,
  browserLocalPersistence,
  browserSessionPersistence,
  connectAuthEmulator,
  signOut,
  onAuthStateChanged,
  onIdTokenChanged,
} from "firebase/auth";
import {
  getFirestore,
  Firestore,
  connectFirestoreEmulator,
  doc,
  collection,
  query,
  where,
  orderBy,
  limit,
  limitToLast,
  onSnapshot,
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  arrayUnion,
  arrayRemove,
  runTransaction,
  writeBatch,
  DocumentSnapshot,
  serverTimestamp,
  Timestamp,
  Query,
  getDocs,
} from "firebase/firestore";
import { initializeAppCheck, ReCaptchaV3Provider } from "firebase/app-check";

const PUBLIC_FIREBASE_DEFAULTS = {
  apiKey: "AIzaSyCLmUK0dvsXGCHK6-8YroGGSQm4-S9an_I",
  authDomain: "privacy-x.firebaseapp.com",
  projectId: "privacy-x",
  storageBucket: "privacy-x.firebasestorage.app",
  messagingSenderId: "891277106805",
} as const;

const isFirebaseSettingConfigured = (value: string | undefined) => {
  const setting = value?.trim().toLowerCase();
  return Boolean(
    setting &&
      !setting.startsWith("my_") &&
      !setting.startsWith("your_") &&
      !setting.includes("your-")
  );
};

const pickFirebaseSetting = (envValue: string | undefined, fallback = ""): string =>
  isFirebaseSettingConfigured(envValue) ? envValue!.trim() : fallback;

const configuredAppId = pickFirebaseSetting(process.env.NEXT_PUBLIC_FIREBASE_APP_ID);
const configuredRecaptchaSiteKey = pickFirebaseSetting(process.env.NEXT_PUBLIC_FIREBASE_RECAPTCHA_SITE_KEY);

const firebaseConfig = {
  apiKey: pickFirebaseSetting(process.env.NEXT_PUBLIC_FIREBASE_API_KEY, PUBLIC_FIREBASE_DEFAULTS.apiKey),
  authDomain: pickFirebaseSetting(process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN, PUBLIC_FIREBASE_DEFAULTS.authDomain),
  projectId: pickFirebaseSetting(process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID, PUBLIC_FIREBASE_DEFAULTS.projectId),
  storageBucket: pickFirebaseSetting(process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET, PUBLIC_FIREBASE_DEFAULTS.storageBucket),
  messagingSenderId: pickFirebaseSetting(
    process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID,
    PUBLIC_FIREBASE_DEFAULTS.messagingSenderId
  ),
  ...(configuredAppId ? { appId: configuredAppId } : {}),
};

export const firebaseConfigured = [
  firebaseConfig.apiKey,
  firebaseConfig.authDomain,
  firebaseConfig.projectId,
].every(isFirebaseSettingConfigured);

let app: FirebaseApp;
let db: Firestore;
let auth: ReturnType<typeof getAuth>;
let authPersistenceReady: Promise<void> = Promise.resolve();

if (typeof window !== "undefined" && firebaseConfigured) {
  const isNewApp = getApps().length === 0;
  app = isNewApp ? initializeApp(firebaseConfig) : getApps()[0];
  auth = getAuth(app);
  db = getFirestore(
    app,
    pickFirebaseSetting(
      process.env.NEXT_PUBLIC_FIREBASE_DATABASE_ID,
      "ai-studio-android-privacyx-5767d797-31a9-488e-9a25-d28909171c2e"
    )
  );

  if (process.env.NEXT_PUBLIC_USE_FIREBASE_EMULATOR === "true" && isNewApp) {
    try {
      connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
      connectFirestoreEmulator(db, "127.0.0.1", 8085);
    } catch {
      // Emulator already connected
    }
  }

  authPersistenceReady = setPersistence(auth, browserLocalPersistence)
    .catch(() => setPersistence(auth, browserSessionPersistence))
    .catch(() => undefined);

  // Only initialize App Check when both a real reCAPTCHA site key and a Web App ID are configured.
  if (configuredRecaptchaSiteKey && configuredAppId) {
    try {
      initializeAppCheck(app, {
        provider: new ReCaptchaV3Provider(configuredRecaptchaSiteKey),
        isTokenAutoRefreshEnabled: true,
      });
    } catch {
      // App Check already initialized
    }
  }
}

export {
  app,
  auth,
  db,
  authPersistenceReady,
  GoogleAuthProvider,
  signInWithPopup,
  signInWithRedirect,
  getRedirectResult,
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
  sendPasswordResetEmail,
  updateProfile,
  signOut,
  onAuthStateChanged,
  onIdTokenChanged,
};
export {
  doc,
  collection,
  query,
  where,
  orderBy,
  limit,
  limitToLast,
  onSnapshot,
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  arrayUnion,
  arrayRemove,
  runTransaction,
  writeBatch as batch,
  DocumentSnapshot,
  serverTimestamp,
  Timestamp,
  Query,
  getDocs,
};

export const FIREBASE_DB_ID = process.env.NEXT_PUBLIC_FIREBASE_DATABASE_ID || "ai-studio-android-privacyx-5767d797-31a9-488e-9a25-d28909171c2e";
export const SUPER_ADMIN_UID = process.env.NEXT_PUBLIC_SUPER_ADMIN_UID || "2bL4lJTTt9WMILpCclHeKNYOdz62";
export const DEFAULT_MAX_UPLOAD_MB = parseInt(process.env.NEXT_PUBLIC_DEFAULT_MAX_UPLOAD_MB || "25", 10);
export const MEDIA_RETENTION_DAYS = parseInt(process.env.NEXT_PUBLIC_MEDIA_RETENTION_DAYS || "7", 10);
export const MEDIA_RETENTION_MS = MEDIA_RETENTION_DAYS * 24 * 60 * 60 * 1000;
export const STORY_TTL_MS = 24 * 60 * 60 * 1000;
export const MESSAGE_PAGE_SIZE = 40;

export const DEFAULT_ICE_SERVERS = [
  { urls: "stun:stun.l.google.com:19302" },
  { urls: "stun:stun1.l.google.com:19302" },
];

export const cleanSecret = (raw: string | undefined): string => {
  if (!raw || raw.startsWith("MY_") || raw.includes("your-")) return "";
  return raw.trim();
};

export const supabaseUrl = cleanSecret(process.env.NEXT_PUBLIC_SUPABASE_URL);
export const supabaseAnonKey = cleanSecret(process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY);
export const supabaseMediaBridgeUrl = cleanSecret(process.env.NEXT_PUBLIC_SUPABASE_MEDIA_BRIDGE_URL);
export const turnServerUrl = cleanSecret(process.env.NEXT_PUBLIC_TURN_SERVER_URL);
export const turnUsername = cleanSecret(process.env.NEXT_PUBLIC_TURN_USERNAME);
export const turnCredential = cleanSecret(process.env.NEXT_PUBLIC_TURN_CREDENTIAL);
export const googleDriveBackupFolder = process.env.NEXT_PUBLIC_GOOGLE_DRIVE_BACKUP_FOLDER || "My Messaging App Backups";
