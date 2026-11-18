"use client";
import { getErrorMessage, normalizeFirestoreValue } from "@/lib/utils";
import type { DocumentData } from "firebase/firestore";

import {
  GoogleAuthProvider,
  signInWithPopup,
  signOut,
  type UserInfo,
} from "firebase/auth";
import {
  auth,
  db,
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

const normalizeUsername = (raw: string): string =>
  raw.trim().toLowerCase().replace(/^@/, "").replace(/[^a-z0-9_]/g, "");

const isValidUsernameFormat = (username: string): boolean =>
  username.length >= 3 && username.length <= 30 && /^[a-z0-9_]+$/.test(username);

export class AuthRepository {
  private dbRef = db;

  get currentUserId(): string | null {
    return auth.currentUser?.uid ?? null;
  }

  get currentUser(): UserProfile | null {
    return auth.currentUser
      ? {
          id: auth.currentUser.uid,
          userId: auth.currentUser.uid,
          username: auth.currentUser.displayName || "",
          displayName: auth.currentUser.displayName || "User",
          photoUrl: auth.currentUser.photoURL || "",
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
    return auth.onAuthStateChanged(callback);
  }

  async signInWithGoogle(): Promise<{ user: UserInfo | null; error: Error | null }> {
    try {
      const provider = new GoogleAuthProvider();
      const result = await signInWithPopup(auth, provider);
      return { user: result.user, error: null };
    } catch (e) {
      return { user: null, error: e instanceof Error ? e : new Error(getErrorMessage(e)) };
    }
  }

  async signOut(): Promise<void> {
    await signOut(auth);
  }

  observeUserProfile(userId: string): (callback: (profile: UserProfile | null) => void) => () => void {
    return (callback) => {
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

  async isUsernameAvailable(rawUsername: string): Promise<boolean> {
    const normalized = normalizeUsername(rawUsername);
    if (!isValidUsernameFormat(normalized)) return false;
    const docRef = doc(this.dbRef, "usernames", normalized);
    const snap = await getDoc(docRef);
    return !snap.exists();
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

    const username = normalizeUsername(params.username);
    if (!isValidUsernameFormat(username)) {
      return { success: false, error: "Username must be 3-30 lowercase letters, numbers, or underscores." };
    }

    const safeDisplayName = params.displayName.trim() || username;
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
          await tx.delete(doc(this.dbRef, "usernames", oldUsername));
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
    if (!targetUid) return;
    const sessionId = `web_session_${Date.now()}`;
    const docRef = doc(this.dbRef, "users", targetUid, "sessions", sessionId);
    try {
      await setDoc(
        docRef,
        {
          userId: targetUid,
          deviceName: "Web Browser",
          platform: `Web ${navigator.platform}`,
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
