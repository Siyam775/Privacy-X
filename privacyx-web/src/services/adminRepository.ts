"use client";
import { getErrorMessage, normalizeFirestoreValue } from "@/lib/utils";

import {
  db,
  auth,
  doc,
  collection,
  query,
  orderBy,
  limit,
  onSnapshot,
  setDoc,
  updateDoc,
  deleteDoc,
  serverTimestamp,
} from "@/lib/firebase";
import { SUPER_ADMIN_UID } from "@/lib/firebase";
import {
  UserProfile,
  Conversation,
  AbuseReport,
  AdminLog,
  PlatformAnnouncement,
  PlatformSettings,
} from "@/types/index";

export class AdminRepository {
  private requireSuperAdmin(): string {
    const uid = authCurrentUserId();
    if (!uid || uid !== SUPER_ADMIN_UID) {
      throw new Error("Unauthorized: Super Admin access is restricted to the designated UID.");
    }
    return uid;
  }

  observeAllUsersForAdmin(): (callback: (users: UserProfile[]) => void) => () => void {
    return (callback) => {
      this.requireSuperAdmin();
      const q = query(collection(db, "users"), limit(100));
      const unsubscribe = onSnapshot(q, (snap) => {
        callback(snap.docs.map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as UserProfile));
      });
      return unsubscribe;
    };
  }

  observeAllConversationsForAdmin(): (callback: (conversations: Conversation[]) => void) => () => void {
    return (callback) => {
      this.requireSuperAdmin();
      const q = query(collection(db, "conversations"), limit(100));
      const unsubscribe = onSnapshot(q, (snap) => {
        callback(snap.docs.map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as Conversation));
      });
      return unsubscribe;
    };
  }

  observeReportsForAdmin(): (callback: (reports: AbuseReport[]) => void) => () => void {
    return (callback) => {
      this.requireSuperAdmin();
      const q = query(collection(db, "reports"), orderBy("createdAt", "desc"), limit(100));
      const unsubscribe = onSnapshot(q, (snap) => {
        callback(snap.docs.map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as AbuseReport));
      });
      return unsubscribe;
    };
  }

  observeAdminLogs(): (callback: (logs: AdminLog[]) => void) => () => void {
    return (callback) => {
      this.requireSuperAdmin();
      const q = query(collection(db, "adminLogs"), orderBy("createdAt", "desc"), limit(100));
      const unsubscribe = onSnapshot(q, (snap) => {
        callback(snap.docs.map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as AdminLog));
      });
      return unsubscribe;
    };
  }

  observePlatformAnnouncements(): (callback: (announcements: PlatformAnnouncement[]) => void) => () => void {
    return (callback) => {
      const q = query(
        collection(db, "platformAnnouncements"),
        orderBy("createdAt", "desc"),
        limit(20)
      );
      const unsubscribe = onSnapshot(q, (snap) => {
        callback(snap.docs.map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as PlatformAnnouncement));
      });
      return unsubscribe;
    };
  }

  observePlatformSettings(): (callback: (settings: PlatformSettings) => void) => () => void {
    return (callback) => {
      const unsubscribe = onSnapshot(doc(db, "platformSettings", "global"), (snap) => {
        callback((snap.exists() ? snap.data() : {}) as PlatformSettings);
      });
      return unsubscribe;
    };
  }

  async setUserSuspended(targetUid: string, suspend: boolean): Promise<{ success: boolean; error: string | null }> {
    this.requireSuperAdmin();
    try {
      await updateDoc(doc(db, "users", targetUid), {
        isSuspended: suspend,
        updatedAt: serverTimestamp(),
      });
      await this.recordAuditLog(this.requireSuperAdmin(), suspend ? "USER_SUSPENDED" : "USER_RESTORED", targetUid, suspend ? "Account suspended by Super Admin" : "Account suspension lifted");
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  async deleteConversationAsAdmin(conversationId: string, title: string): Promise<{ success: boolean; error: string | null }> {
    this.requireSuperAdmin();
    try {
      await deleteDoc(doc(db, "conversations", conversationId));
      await this.recordAuditLog(this.requireSuperAdmin(), "CONVERSATION_REMOVED", conversationId, `Removed conversation: ${title}`);
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  async updateReportStatus(reportId: string, newStatus: string): Promise<{ success: boolean; error: string | null }> {
    this.requireSuperAdmin();
    try {
      await updateDoc(doc(db, "reports", reportId), {
        status: newStatus,
        reviewedBy: this.requireSuperAdmin(),
        updatedAt: serverTimestamp(),
      });
      await this.recordAuditLog(this.requireSuperAdmin(), `REPORT_${newStatus.toUpperCase()}`, reportId, `Report marked as ${newStatus}`);
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  async publishAnnouncement(title: string, body: string, priority: string): Promise<{ success: boolean; error: string | null }> {
    this.requireSuperAdmin();
    const id = `ann_${Math.random().toString(36).slice(2, 18)}`;
    try {
      await setDoc(doc(db, "platformAnnouncements", id), {
        title: title.trim().slice(0, 140),
        body: body.trim().slice(0, 2000),
        priority,
        createdBy: this.requireSuperAdmin(),
        createdAt: serverTimestamp(),
      });
      await this.recordAuditLog(this.requireSuperAdmin(), "ANNOUNCEMENT_PUBLISHED", id, `Published ${priority} announcement: ${title.slice(0, 80)}`);
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  async updatePlatformSettings(params: {
    maxUploadMb: number;
    allowNewRegistrations: boolean;
    maintenanceMode: boolean;
  }): Promise<{ success: boolean; error: string | null }> {
    this.requireSuperAdmin();
    try {
      await setDoc(
        doc(db, "platformSettings", "global"),
        {
          maxUploadMb: Math.max(1, Math.min(100, params.maxUploadMb)),
          allowNewRegistrations: params.allowNewRegistrations,
          maintenanceMode: params.maintenanceMode,
          updatedBy: this.requireSuperAdmin(),
          updatedAt: serverTimestamp(),
        },
        { merge: true }
      );
      await this.recordAuditLog(this.requireSuperAdmin(), "SETTINGS_UPDATED", "global", `maxUploadMb=${params.maxUploadMb}, reg=${params.allowNewRegistrations}, maintenance=${params.maintenanceMode}`);
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  private async recordAuditLog(actorId: string, action: string, targetId: string, details: string): Promise<void> {
    const logId = `log_${Math.random().toString(36).slice(2, 18)}`;
    try {
      await setDoc(doc(db, "adminLogs", logId), {
        actorId,
        action: action.slice(0, 80),
        targetId: targetId.slice(0, 128),
        details: details.slice(0, 1000),
        createdAt: serverTimestamp(),
      });
    } catch (e) {
      console.error("Audit log failed:", e);
    }
  }
}

function authCurrentUserId(): string | null {
  return auth?.currentUser?.uid ?? null;
}
