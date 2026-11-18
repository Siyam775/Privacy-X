"use client";
import { getErrorMessage, normalizeFirestoreValue } from "@/lib/utils";
import type { DocumentData } from "firebase/firestore";

import type { UserInfo } from "firebase/auth";
import {
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
  signOut as firebaseSignOut,
  doc,
  collection,
  runTransaction,
  setDoc,
  updateDoc,
  deleteDoc,
  serverTimestamp,
  getDoc,
  query,
  orderBy,
  limit,
  getDocs,
  onSnapshot,
} from "@/lib/firebase";
import {
  UserProfile,
  UserPrivateAccount,
  UserSettings,
  UserSession,
  UsernameReservation,
  SUPER_ADMIN_UID,
  SavedMessage,
} from "@/types/index";

export const normalizeUsername = (raw: string): string =>
  raw.trim().toLowerCase().replace(/^@/, "").replace(/[^a-z0-9_]/g, "");

export const isValidUsernameFormat = (username: string): boolean =>
  username.length >= 3 && username.length <= 30 && /^[a-z0-9_]+$/.test(username);

export const validateEmailAddress = (email: string): boolean =>
  /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim());

export function mapFirebaseAuthError(err: unknown, fallback = "Authentication failed. Please try again."): string {
  const code =
    typeof err === "object" && err !== null && "code" in err && typeof (err as { code?: unknown }).code === "string"
      ? (err as { code: string }).code
      : "";

  switch (code) {
    case "auth/email-already-in-use":
      return "An account with this email already exists. Try signing in instead.";
    case "auth/invalid-email":
      return "Please enter a valid email address.";
    case "auth/weak-password":
      return "Password must be at least 6 characters long.";
    case "auth/invalid-credential":
    case "auth/wrong-password":
    case "auth/user-not-found":
      return "Incorrect email or password. Please check your credentials and try again.";
    case "auth/too-many-requests":
      return "Too many attempts. Please wait a moment and try again.";
    case "auth/popup-blocked":
      return "Popup was blocked by your browser. Redirecting to Google sign-in…";
    case "auth/popup-closed-by-user":
    case "auth/cancelled-popup-request":
      return "Google sign-in was cancelled. Please try again.";
    case "auth/unauthorized-domain":
      return "This domain is not yet added to Firebase Authentication Authorized Domains.";
    case "auth/operation-not-allowed":
      return "This sign-in method is not enabled in Firebase Authentication yet.";
    case "auth/account-exists-with-different-credential":
      return "An account already exists with this email using a different sign-in method.";
    case "auth/network-request-failed":
      return "Network error while contacting Firebase Authentication. Please check your connection.";
    case "auth/user-disabled":
      return "This account has been disabled.";
    case "auth/user-token-expired":
    case "auth/requires-recent-login":
      return "Your session expired. Please sign in again.";
    default:
      return getErrorMessage(err, fallback);
  }
}

export class AuthRepository {
  private get dbRef() {
    return db;
  }

  get currentUserId(): string | null {
    return auth?.currentUser?.uid ?? null;
  }

  get currentUser(): UserProfile | null {
    const firebaseUser = auth?.currentUser;
    return firebaseUser
      ? {
          id: firebaseUser.uid,
          userId: firebaseUser.uid,
          username: firebaseUser.displayName || "",
          displayName: firebaseUser.displayName || "User",
          photoUrl: firebaseUser.photoURL || "",
          bio: "",
          statusText: "Available",
          isOnline: true,
          lastSeen: null,
          isSuspended: false,
          profileVisibility: "everyone",
          lastSeenPrivacy: "everyone",
          storyPrivacy: "public",
          customAccentHex: "",
        }
      : null;
  }

  observeAuthState(callback: (user: UserInfo | null) => void) {
    if (!auth) return () => {};
    return auth.onAuthStateChanged(callback);
  }

  async signInWithGoogle(): Promise<{ user: UserInfo | null; redirected?: boolean; error: Error | null }> {
    if (!auth) {
      return { user: null, error: new Error("Firebase Authentication is not initialized.") };
    }
    try {
      await authPersistenceReady;
      const provider = new GoogleAuthProvider();
      provider.setCustomParameters({ prompt: "select_account" });
      const result = await signInWithPopup(auth, provider);
      return { user: result.user, redirected: false, error: null };
    } catch (e) {
      const code =
        typeof e === "object" && e !== null && "code" in e && typeof (e as { code?: unknown }).code === "string"
          ? (e as { code: string }).code
          : "";
      if (code === "auth/popup-blocked" || code === "auth/operation-not-supported-in-this-environment") {
        try {
          const provider = new GoogleAuthProvider();
          provider.setCustomParameters({ prompt: "select_account" });
          await signInWithRedirect(auth, provider);
          return { user: null, redirected: true, error: null };
        } catch (redirectError) {
          return { user: null, redirected: false, error: new Error(mapFirebaseAuthError(redirectError)) };
        }
      }
      return { user: null, redirected: false, error: new Error(mapFirebaseAuthError(e, "Google sign-in failed.")) };
    }
  }

  async signInWithGoogleRedirect(): Promise<{ redirected: boolean; error: Error | null }> {
    if (!auth) {
      return { redirected: false, error: new Error("Firebase Authentication is not initialized.") };
    }
    try {
      await authPersistenceReady;
      const provider = new GoogleAuthProvider();
      provider.setCustomParameters({ prompt: "select_account" });
      await signInWithRedirect(auth, provider);
      return { redirected: true, error: null };
    } catch (e) {
      return { redirected: false, error: new Error(mapFirebaseAuthError(e, "Google redirect failed.")) };
    }
  }

  async consumeGoogleRedirectResult(): Promise<{ user: UserInfo | null; error: string | null }> {
    if (!auth) return { user: null, error: null };
    try {
      await authPersistenceReady;
      const result = await getRedirectResult(auth);
      return { user: result?.user ?? null, error: null };
    } catch (e) {
      return { user: null, error: mapFirebaseAuthError(e, "Google sign-in could not be completed.") };
    }
  }

  async registerWithEmail(params: {
    displayName: string;
    email: string;
    password: string;
  }): Promise<{ user: UserInfo | null; error: string | null }> {
    if (!auth) {
      return { user: null, error: "Firebase Authentication is not initialized." };
    }
    const trimmedName = params.displayName.trim();
    const normalizedEmail = params.email.trim();
    if (trimmedName.length < 2 || trimmedName.length > 60) {
      return { user: null, error: "Display name must be between 2 and 60 characters." };
    }
    if (!validateEmailAddress(normalizedEmail)) {
      return { user: null, error: "Please enter a valid email address." };
    }
    if (params.password.length < 6) {
      return { user: null, error: "Password must be at least 6 characters long." };
    }

    try {
      await authPersistenceReady;
      const cred = await createUserWithEmailAndPassword(auth, normalizedEmail, params.password);
      await updateProfile(cred.user, { displayName: trimmedName }).catch(() => undefined);
      return { user: cred.user, error: null };
    } catch (e) {
      return { user: null, error: mapFirebaseAuthError(e, "Could not create your account.") };
    }
  }

  async signInWithEmail(params: {
    email: string;
    password: string;
  }): Promise<{ user: UserInfo | null; error: string | null }> {
    if (!auth) {
      return { user: null, error: "Firebase Authentication is not initialized." };
    }
    const normalizedEmail = params.email.trim();
    if (!validateEmailAddress(normalizedEmail)) {
      return { user: null, error: "Please enter a valid email address." };
    }
    if (!params.password) {
      return { user: null, error: "Please enter your password." };
    }

    try {
      await authPersistenceReady;
      const cred = await signInWithEmailAndPassword(auth, normalizedEmail, params.password);
      return { user: cred.user, error: null };
    } catch (e) {
      return { user: null, error: mapFirebaseAuthError(e, "Sign in failed.") };
    }
  }

  async sendPasswordReset(email: string): Promise<{ success: boolean; error: string | null }> {
    if (!auth) {
      return { success: false, error: "Firebase Authentication is not initialized." };
    }
    const normalizedEmail = email.trim();
    if (!validateEmailAddress(normalizedEmail)) {
      return { success: false, error: "Enter a valid email address first to reset your password." };
    }
    try {
      await sendPasswordResetEmail(auth, normalizedEmail);
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: mapFirebaseAuthError(e, "Could not send password reset email.") };
    }
  }

  async signOut(): Promise<void> {
    if (!auth) return;
    if (this.currentUserId && this.dbRef) {
      await this.updatePresence(false).catch(() => undefined);
    }
    await firebaseSignOut(auth);
  }

  observeUserProfile(
    userId: string
  ): (callback: (profile: UserProfile | null) => void, onError?: (err: Error) => void) => () => void {
    return (callback, onError) => {
      if (!this.dbRef) {
        callback(null);
        return () => {};
      }
      const unsubscribe = onSnapshot(
        doc(this.dbRef, "users", userId),
        (snap) => {
          if (snap.exists()) {
            callback(normalizeFirestoreValue({ ...snap.data(), id: snap.id }) as UserProfile);
          } else {
            callback(null);
          }
        },
        (err) => {
          console.error("Firestore error:", err);
          onError?.(err instanceof Error ? err : new Error(getErrorMessage(err)));
        }
      );
      return unsubscribe;
    };
  }

  observePrivateAccount(userId: string): (callback: (account: UserPrivateAccount | null) => void) => () => void {
    return (callback) => {
      const unsubscribe = onSnapshot(
        doc(this.dbRef, "users", userId, "private", "account"),
        (snap) => {
          if (snap.exists()) {
            callback(normalizeFirestoreValue({ ...snap.data(), id: snap.id }) as UserPrivateAccount);
          } else {
            callback(null);
          }
        },
        (err) => console.error(err)
      );
      return unsubscribe;
    };
  }

  observeUserSettings(userId: string): (callback: (settings: UserSettings) => void) => () => void {
    return (callback) => {
      const unsubscribe = onSnapshot(
        doc(this.dbRef, "users", userId, "userSettings", "preferences"),
        (snap) => {
          callback((snap.exists() ? snap.data() : { userId }) as UserSettings);
        },
        (err) => console.error(err)
      );
      return unsubscribe;
    };
  }

  observeSessions(userId: string): (callback: (sessions: UserSession[]) => void) => () => void {
    return (callback) => {
      const q = query(
        collection(this.dbRef, "users", userId, "sessions"),
        orderBy("createdAt", "desc"),
        limit(20)
      );
      const unsubscribe = onSnapshot(
        q,
        (snap) => {
          callback(snap.docs.map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as UserSession));
        },
        (err) => console.error(err)
      );
      return unsubscribe;
    };
  }

  async checkUsernameAvailability(rawUsername: string): Promise<{
    username: string;
    valid: boolean;
    available: boolean | null;
    error: string | null;
  }> {
    const normalized = normalizeUsername(rawUsername);
    if (!isValidUsernameFormat(normalized)) {
      return { username: normalized, valid: false, available: false, error: null };
    }
    if (!this.dbRef) {
      return { username: normalized, valid: true, available: null, error: "Database connection unavailable." };
    }
    try {
      const docRef = doc(this.dbRef, "usernames", normalized);
      const snap = await getDoc(docRef);
      const available = !snap.exists() || snap.data()?.userId === this.currentUserId;
      return { username: normalized, valid: true, available, error: null };
    } catch (e) {
      return {
        username: normalized,
        valid: true,
        available: null,
        error: getErrorMessage(e, "Could not verify username availability."),
      };
    }
  }

  async isUsernameAvailable(rawUsername: string): Promise<boolean> {
    const result = await this.checkUsernameAvailability(rawUsername);
    return result.valid && result.available === true;
  }

  async claimUsernameAndCreateProfile(params: {
    displayName: string;
    username: string;
    bio: string;
    photoUrl: string;
    email: string;
  }): Promise<{ success: boolean; error: string | null }> {
    const uid = this.currentUserId;
    if (!uid) return { success: false, error: "User must be signed in." };
    if (!this.dbRef) return { success: false, error: "Database connection is not ready." };

    const username = normalizeUsername(params.username);
    if (!isValidUsernameFormat(username)) {
      return { success: false, error: "Username must be 3-30 lowercase letters, numbers, or underscores." };
    }

    const safeDisplayName = (params.displayName.trim() || username).slice(0, 60);
    const userRef = doc(this.dbRef, "users", uid);
    const usernameRef = doc(this.dbRef, "usernames", username);
    const privateRef = doc(this.dbRef, "users", uid, "private", "account");
    const settingsRef = doc(this.dbRef, "users", uid, "userSettings", "preferences");

    try {
      await runTransaction(this.dbRef, async (tx) => {
        const existingUsername = await tx.get(usernameRef);
        if (existingUsername.exists() && existingUsername.data().userId !== uid) {
          throw new Error(`Username @${username} is already taken.`);
        }

        const existingProfile = await tx.get(userRef);
        const oldUsername = existingProfile.exists() ? existingProfile.data().username : "";

        tx.set(usernameRef, {
          username,
          userId: uid,
          displayName: safeDisplayName,
          photoUrl: params.photoUrl.slice(0, 1000),
          createdAt: serverTimestamp(),
        });

        if (oldUsername && oldUsername !== username) {
          tx.delete(doc(this.dbRef, "usernames", oldUsername));
        }

        const profileData: DocumentData = {
          userId: uid,
          username,
          displayName: safeDisplayName.slice(0, 60),
          photoUrl: params.photoUrl.slice(0, 1000),
          bio: params.bio.trim().slice(0, 300),
          statusText: "Available",
          isOnline: true,
          lastSeen: serverTimestamp(),
          isSuspended: false,
          profileVisibility: "everyone",
          lastSeenPrivacy: "everyone",
          storyPrivacy: "public",
          updatedAt: serverTimestamp(),
        };

        if (!existingProfile.exists()) {
          profileData.createdAt = serverTimestamp();
          tx.set(userRef, profileData);
        } else {
          tx.set(userRef, profileData, { merge: true });
        }

        tx.set(
          privateRef,
          {
            userId: uid,
            email: params.email.slice(0, 256),
            closeFriendsIds: [],
            hiddenStoryUserIds: [],
            savedPostIds: [],
            updatedAt: serverTimestamp(),
          },
          { merge: true }
        );

        tx.set(
          settingsRef,
          {
            userId: uid,
            themeId: "material_liquid_glass",
            appearanceMode: "system",
            accentColorHex: "",
            reducedMotion: false,
            notificationsEnabled: true,
            quietHoursEnabled: false,
            soundEnabled: true,
            vibrationEnabled: true,
            showNotificationPreview: true,
            autoDownloadWifi: true,
            autoDownloadCellular: false,
            aiConsentGranted: false,
            updatedAt: serverTimestamp(),
          },
          { merge: true }
        );
      });

      if (auth?.currentUser && auth.currentUser.displayName !== safeDisplayName) {
        await updateProfile(auth.currentUser, { displayName: safeDisplayName }).catch(() => undefined);
      }
      await this.registerCurrentSession(uid);
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  async updateProfile(params: {
    displayName: string;
    bio: string;
    statusText: string;
    photoUrl: string;
    profileVisibility: string;
    lastSeenPrivacy: string;
    storyPrivacy: string;
    customAccentHex: string;
  }): Promise<{ success: boolean; error: string | null }> {
    const uid = this.currentUserId;
    if (!uid) return { success: false, error: "User must be signed in." };

    const userRef = doc(this.dbRef, "users", uid);
    try {
      const snap = await getDoc(userRef);
      const existing = snap.exists() ? (normalizeFirestoreValue({ ...snap.data(), id: snap.id }) as UserProfile) : null;

      await updateDoc(userRef, {
        displayName: params.displayName.trim() || (existing?.username || "User"),
        bio: params.bio.trim().slice(0, 300),
        statusText: params.statusText.trim() || "Available",
        photoUrl: params.photoUrl.trim().slice(0, 1000),
        profileVisibility: params.profileVisibility,
        lastSeenPrivacy: params.lastSeenPrivacy,
        storyPrivacy: params.storyPrivacy,
        customAccentHex: params.customAccentHex.slice(0, 16),
        updatedAt: serverTimestamp(),
      });

      if (existing?.username) {
        await updateDoc(doc(this.dbRef, "usernames", existing.username), {
          displayName: params.displayName.trim() || existing.username,
          photoUrl: params.photoUrl.trim().slice(0, 1000),
        });
      }

      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  async updatePresence(isOnline: boolean): Promise<void> {
    const uid = this.currentUserId;
    if (!uid) return;
    const userRef = doc(this.dbRef, "users", uid);
    try {
      await updateDoc(userRef, {
        isOnline,
        lastSeen: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
    } catch (e) {
      console.error("Presence update failed:", e);
    }
  }

  async updateUserSettings(settings: Partial<UserSettings>): Promise<{ success: boolean; error: string | null }> {
    const uid = this.currentUserId;
    if (!uid) return { success: false, error: "User must be signed in." };

    const docRef = doc(this.dbRef, "users", uid, "userSettings", "preferences");
    try {
      await setDoc(
        docRef,
        {
          userId: uid,
          themeId: settings.themeId || "material_liquid_glass",
          appearanceMode: settings.appearanceMode || "system",
          accentColorHex: settings.accentColorHex || "",
          reducedMotion: settings.reducedMotion ?? false,
          notificationsEnabled: settings.notificationsEnabled ?? true,
          quietHoursEnabled: settings.quietHoursEnabled ?? false,
          soundEnabled: settings.soundEnabled ?? true,
          vibrationEnabled: settings.vibrationEnabled ?? true,
          showNotificationPreview: settings.showNotificationPreview ?? true,
          autoDownloadWifi: settings.autoDownloadWifi ?? true,
          autoDownloadCellular: settings.autoDownloadCellular ?? false,
          aiConsentGranted: settings.aiConsentGranted ?? false,
          updatedAt: serverTimestamp(),
        },
        { merge: true }
      );
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  async registerCurrentSession(uid?: string): Promise<void> {
    const targetUid = uid || this.currentUserId;
    if (!targetUid || !this.dbRef) return;
    const sessionId = `web_session_${Date.now()}`;
    const docRef = doc(this.dbRef, "users", targetUid, "sessions", sessionId);
    const rawPlatform = typeof navigator !== "undefined" ? navigator.platform || "Browser" : "Browser";
    const platform = `Web ${rawPlatform}`.trim().slice(0, 40) || "Web Browser";
    try {
      await setDoc(
        docRef,
        {
          userId: targetUid,
          deviceName: "Web Browser",
          platform,
          isActive: true,
          lastActiveAt: serverTimestamp(),
          createdAt: serverTimestamp(),
        },
        { merge: true }
      );
    } catch (e) {
      console.error("Session registration failed:", e);
    }
  }

  async revokeSession(sessionId: string): Promise<{ success: boolean; error: string | null }> {
    const uid = this.currentUserId;
    if (!uid) return { success: false, error: "User must be signed in." };
    try {
      await deleteDoc(doc(this.dbRef, "users", uid, "sessions", sessionId));
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  async searchUsers(queryText: string): Promise<UsernameReservation[]> {
    const normalized = queryText.trim().toLowerCase().replace(/^@/, "");
    try {
      const q = query(collection(this.dbRef, "usernames"), orderBy("username"), limit(50));
      const snap = await getDocs(q);
      const currentUid = this.currentUserId;
      return snap.docs
        .map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as UsernameReservation)
        .filter(
          (it) =>
            it.userId !== currentUid &&
            (normalized === "" ||
              it.username.includes(normalized) ||
              it.displayName.toLowerCase().includes(queryText.trim().toLowerCase()))
        );
    } catch (e) {
      console.error("User search failed:", e);
      return [];
    }
  }

  async exportUserDataJson(): Promise<{ success: boolean; data: string | null; error: string | null }> {
    const uid = this.currentUserId;
    if (!uid) return { success: false, data: null, error: "User must be signed in." };

    try {
      const [profileSnap, privSnap, settingsSnap, savedSnap] = await Promise.all([
        getDoc(doc(this.dbRef, "users", uid)),
        getDoc(doc(this.dbRef, "users", uid, "private", "account")),
        getDoc(doc(this.dbRef, "users", uid, "userSettings", "preferences")),
        getDocs(collection(this.dbRef, "users", uid, "savedMessages")),
      ]);

      const root = {
        exportedAt: Date.now(),
        userId: uid,
        formatVersion: 1,
        profile: profileSnap.exists() ? normalizeFirestoreValue(profileSnap.data()) : {},
        privateAccount: privSnap.exists() ? normalizeFirestoreValue(privSnap.data()) : {},
        settings: settingsSnap.exists() ? normalizeFirestoreValue(settingsSnap.data()) : {},
        savedMessages: savedSnap.docs.map((d) => ({ id: d.id, ...normalizeFirestoreValue(d.data()) })),
      };

      return { success: true, data: JSON.stringify(root, null, 2), error: null };
    } catch (e) {
      return { success: false, data: null, error: getErrorMessage(e) };
    }
  }

  async restoreUserDataJson(raw: string): Promise<{ success: boolean; error: string | null }> {
    const uid = this.currentUserId;
    if (!uid) return { success: false, error: "User must be signed in." };
    try {
      const parsed = JSON.parse(raw) as {
        userId?: string;
        profile?: Partial<UserProfile>;
        settings?: Partial<UserSettings>;
        savedMessages?: Array<Omit<Partial<SavedMessage>, "createdAt"> & { createdAt?: string | Date }>;
      };
      if (parsed.userId !== uid) {
        return { success: false, error: "This backup belongs to a different account." };
      }

      if (parsed.profile && Object.keys(parsed.profile).length) {
        const profileResult = await this.updateProfile({
          displayName: parsed.profile.displayName || "User",
          bio: parsed.profile.bio || "",
          statusText: parsed.profile.statusText || "Available",
          photoUrl: parsed.profile.photoUrl || "",
          profileVisibility: parsed.profile.profileVisibility || "everyone",
          lastSeenPrivacy: parsed.profile.lastSeenPrivacy || "everyone",
          storyPrivacy: parsed.profile.storyPrivacy || "public",
          customAccentHex: parsed.profile.customAccentHex || "",
        });
        if (!profileResult.success) return profileResult;
      }

      if (parsed.settings) {
        const settingsResult = await this.updateUserSettings(parsed.settings);
        if (!settingsResult.success) return settingsResult;
      }

      for (const [index, item] of (parsed.savedMessages || []).slice(0, 500).entries()) {
        const id = (item.id || `restored_${Date.now()}_${index}`).replace(/[^a-zA-Z0-9_-]/g, "_").slice(0, 128);
        await setDoc(doc(this.dbRef, "users", uid, "savedMessages", id), {
          userId: uid,
          content: String(item.content || "").slice(0, 5000),
          sourceConversationId: String(item.sourceConversationId || "").slice(0, 128),
          sourceSenderName: String(item.sourceSenderName || "").slice(0, 120),
          mediaUrl: String(item.mediaUrl || "").slice(0, 1000),
          mediaType: String(item.mediaType || "text").slice(0, 32),
          createdAt: item.createdAt
            ? item.createdAt instanceof Date ? item.createdAt : new Date(item.createdAt)
            : serverTimestamp(),
        }, { merge: true });
      }
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: e instanceof Error ? e.message : "Backup file is invalid." };
    }
  }

  async deleteAccount(): Promise<{ success: boolean; error: string | null }> {
    const uid = this.currentUserId;
    if (!uid) return { success: false, error: "User must be signed in." };

    try {
      const userRef = doc(this.dbRef, "users", uid);
      const profileSnap = await getDoc(userRef);
      if (profileSnap.exists()) {
        const username = (profileSnap.data() as UserProfile).username;
        if (username) {
          await deleteDoc(doc(this.dbRef, "usernames", username));
        }
      }
      await deleteDoc(doc(this.dbRef, "users", uid, "private", "account"));
      await deleteDoc(doc(this.dbRef, "users", uid, "userSettings", "preferences"));
      await deleteDoc(userRef);
      if (auth.currentUser) {
        await auth.currentUser.delete();
      }
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  isSuperAdmin(uid?: string): boolean {
    return uid === SUPER_ADMIN_UID;
  }
}
