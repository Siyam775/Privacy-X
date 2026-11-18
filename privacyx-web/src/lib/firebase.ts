import { initializeApp, getApps, FirebaseApp } from "firebase/app";
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signOut,
  onAuthStateChanged,
} from "firebase/auth";
import {
  getFirestore,
  Firestore,
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

const firebaseConfig = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY,
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN,
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID,
  storageBucket: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID,
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID,
};

const isFirebaseSettingConfigured = (value: string | undefined) => {
  const setting = value?.trim().toLowerCase();
  return Boolean(setting && !setting.startsWith("my_") && !setting.includes("your-"));
};

export const firebaseConfigured = [
  firebaseConfig.apiKey,
  firebaseConfig.authDomain,
  firebaseConfig.projectId,
  firebaseConfig.appId,
].every(isFirebaseSettingConfigured);

let app: FirebaseApp;
let db: Firestore;
let auth: ReturnType<typeof getAuth>;

if (typeof window !== "undefined" && firebaseConfigured) {
  if (getApps().length === 0) {
    app = initializeApp(firebaseConfig);
  } else {
    app = getApps()[0];
  }
  auth = getAuth(app);
  db = getFirestore(app, process.env.NEXT_PUBLIC_FIREBASE_DATABASE_ID || "ai-studio-android-privacyx-5767d797-31a9-488e-9a25-d28909171c2e");

  // Initialize App Check with reCAPTCHA v3 (web equivalent of Android App Check)
  try {
    initializeAppCheck(app, {
      provider: new ReCaptchaV3Provider(process.env.NEXT_PUBLIC_FIREBASE_RECAPTCHA_SITE_KEY || ""),
      isTokenAutoRefreshEnabled: true,
    });
  } catch {
    // App Check already initialized
  }
}

export { app, auth, db, GoogleAuthProvider, signInWithPopup, signOut, onAuthStateChanged };
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
