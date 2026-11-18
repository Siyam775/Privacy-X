"use client";
import { getErrorMessage, normalizeFirestoreValue } from "@/lib/utils";
import type { DocumentData } from "firebase/firestore";

import {
  db,
  auth,
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
  arrayUnion,
  arrayRemove,
  serverTimestamp,
  runTransaction,
  batch,
  Timestamp,
} from "@/lib/firebase";
import {
  Conversation,
  ChatMessage,
  ConversationMember,
  GroupTopic,
} from "@/types/index";

export class MessagingRepository {
  observeMyConversations(userId: string): (callback: (conversations: Conversation[]) => void) => () => void {
    return (callback) => {
      const q = query(collection(db, "conversations"), where("memberUids", "array-contains", userId));
      const unsubscribe = onSnapshot(q, (snap) => {
        const list = snap.docs.map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as Conversation).sort(
          (a, b) =>
            (b.lastMessageAt?.getTime() ?? b.updatedAt?.getTime() ?? b.createdAt?.getTime() ?? 0) -
            (a.lastMessageAt?.getTime() ?? a.updatedAt?.getTime() ?? a.createdAt?.getTime() ?? 0)
        );
        callback(list);
      });
      return unsubscribe;
    };
  }

  observePublicDiscoveryConversations(): (callback: (conversations: Conversation[]) => void) => () => void {
    return (callback) => {
      const q = query(
        collection(db, "conversations"),
        where("isPublic", "==", true),
        limit(50)
      );
      const unsubscribe = onSnapshot(q, (snap) => {
        callback(snap.docs.map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as Conversation));
      });
      return unsubscribe;
    };
  }

  observeConversation(conversationId: string): (callback: (conv: Conversation | null) => void) => () => void {
    return (callback) => {
      const unsubscribe = onSnapshot(doc(db, "conversations", conversationId), (snap) => {
        callback(snap.exists() ? (normalizeFirestoreValue({ ...snap.data(), id: snap.id }) as Conversation) : null);
      });
      return unsubscribe;
    };
  }

  observeMessages(
    conversationId: string,
    limitCount: number = 40
  ): (callback: (messages: ChatMessage[]) => void) => () => void {
    return (callback) => {
      const uid = authCurrentUserId();
      const q = query(
        collection(db, "conversations", conversationId, "messages"),
        where("memberUids", "array-contains", uid),
        orderBy("createdAt", "asc"),
        limitToLast(limitCount)
      );
      const unsubscribe = onSnapshot(q, (snap) => {
        callback(snap.docs.map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as ChatMessage));
      });
      return unsubscribe;
    };
  }

  observeMemberState(
    conversationId: string,
    userId: string
  ): (callback: (member: ConversationMember | null) => void) => () => void {
    return (callback) => {
      const unsubscribe = onSnapshot(
        doc(db, "conversations", conversationId, "members", userId),
        (snap) => {
          callback(snap.exists() ? (normalizeFirestoreValue({ ...snap.data(), id: snap.id }) as ConversationMember) : null);
        }
      );
      return unsubscribe;
    };
  }

  observeConversationMembers(conversationId: string): (callback: (members: ConversationMember[]) => void) => () => void {
    return (callback) => {
      const uid = authCurrentUserId();
      const q = query(
        collection(db, "conversations", conversationId, "members"),
        where("memberUids", "array-contains", uid)
      );
      const unsubscribe = onSnapshot(q, (snap) => {
        callback(snap.docs.map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as ConversationMember));
      });
      return unsubscribe;
    };
  }

  observeTopics(conversationId: string): (callback: (topics: GroupTopic[]) => void) => () => void {
    return (callback) => {
      const uid = authCurrentUserId();
      const q = query(
        collection(db, "conversations", conversationId, "topics"),
        where("memberUids", "array-contains", uid)
      );
      const unsubscribe = onSnapshot(q, (snap) => {
        callback(snap.docs.map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as GroupTopic));
      });
      return unsubscribe;
    };
  }

  async createOrGetDirectConversation(params: {
    otherUserId: string;
    otherDisplayName: string;
    otherPhotoUrl: string;
  }): Promise<{ success: boolean; conversationId: string | null; error: string | null }> {
    const uid = authCurrentUserId();
    if (!uid) return { success: false, conversationId: null, error: "User must be signed in." };

    const sorted = [uid, params.otherUserId].sort();
    const conversationId = `dm_${sorted[0]}_${sorted[1]}`;
    const docRef = doc(db, "conversations", conversationId);

    try {
      const existing = await getDoc(docRef);
      if (!existing.exists()) {
        await setDoc(docRef, {
          type: "direct",
          title: params.otherDisplayName.trim() || "Direct Chat",
          description: "",
          photoUrl: params.otherPhotoUrl.slice(0, 1000),
          ownerId: uid,
          memberUids: sorted,
          adminUids: sorted,
          communityId: "",
          isPublic: false,
          slowModeSeconds: 0,
          memberApprovalRequired: false,
          inviteCode: "",
          lastMessageText: "",
          lastMessageSenderId: "",
          createdAt: serverTimestamp(),
          updatedAt: serverTimestamp(),
        });
      }
      return { success: true, conversationId, error: null };
    } catch (e) {
      return { success: false, conversationId: null, error: getErrorMessage(e) };
    }
  }

  async createGroupOrCommunityOrChannel(params: {
    type: string;
    title: string;
    description: string;
    isPublic: boolean;
    memberUids?: string[];
    communityId?: string;
    slowModeSeconds?: number;
  }): Promise<{ success: boolean; conversationId: string | null; error: string | null }> {
    const uid = authCurrentUserId();
    if (!uid) return { success: false, conversationId: null, error: "User must be signed in." };

    const convId = `${params.type}_${Math.random().toString(36).slice(2, 18)}`;
    const docRef = doc(db, "conversations", convId);
    const allMembers = [...new Set([uid, ...(params.memberUids || [])])].slice(0, 100);
    const inviteCode = Math.random().toString(36).slice(2, 12).toUpperCase();

    try {
      await setDoc(docRef, {
        type: params.type,
        title: params.title.trim() || `Untitled ${params.type}`,
        description: params.description.trim().slice(0, 500),
        photoUrl: "",
        ownerId: uid,
        memberUids: allMembers,
        adminUids: [uid],
        communityId: params.communityId || "",
        isPublic: params.isPublic,
        slowModeSeconds: params.slowModeSeconds || 0,
        memberApprovalRequired: false,
        adminsOnlyMessaging: params.type === "channel",
        inviteCode,
        lastMessageText: `Created ${params.type}`,
        lastMessageSenderId: uid,
        lastMessageAt: serverTimestamp(),
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      return { success: true, conversationId: convId, error: null };
    } catch (e) {
      return { success: false, conversationId: null, error: getErrorMessage(e) };
    }
  }

  async joinPublicConversation(conversationId: string): Promise<{ success: boolean; error: string | null }> {
    const uid = authCurrentUserId();
    if (!uid) return { success: false, error: "User must be signed in." };
    try {
      await updateDoc(doc(db, "conversations", conversationId), {
        memberUids: arrayUnion(uid),
        updatedAt: serverTimestamp(),
      });
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  async addMember(conversationId: string, targetUid: string): Promise<{ success: boolean; error: string | null }> {
    try {
      await updateDoc(doc(db, "conversations", conversationId), {
        memberUids: arrayUnion(targetUid),
        updatedAt: serverTimestamp(),
      });
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  async removeMember(conversationId: string, targetUid: string): Promise<{ success: boolean; error: string | null }> {
    try {
      await updateDoc(doc(db, "conversations", conversationId), {
        memberUids: arrayRemove(targetUid),
        updatedAt: serverTimestamp(),
      });
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  async setConversationArchived(conversationId: string, archived: boolean): Promise<{ success: boolean; error: string | null }> {
    try {
      await updateDoc(doc(db, "conversations", conversationId), {
        isArchived: archived,
        updatedAt: serverTimestamp(),
      });
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: e instanceof Error ? e.message : "Could not update this conversation." };
    }
  }

  async sendMessage(params: {
    conversation: Conversation;
    senderName: string;
    senderAvatarUrl: string;
    text: string;
    mediaType?: string;
    mediaUrl?: string;
    mediaId?: string;
    mediaThumbnailUrl?: string;
    mediaExpiresAt?: number;
    mediaExpired?: boolean;
    fileName?: string;
    fileSizeBytes?: number;
    mimeType?: string;
    durationMs?: number;
    replyToId?: string;
    replyToPreview?: string;
    forwardedFrom?: string;
    topicId?: string;
    pollQuestion?: string;
    pollOptions?: string[];
    disappearsInMinutes?: number | null;
    scheduledDelayMinutes?: number | null;
    customMessageId?: string | null;
  }): Promise<{ success: boolean; messageId: string | null; error: string | null }> {
    if (
      params.mediaType === "video" ||
      params.mimeType?.startsWith("video/")
    ) {
      return {
        success: false,
        messageId: null,
        error: "Video messaging is disabled. Use photos, voice notes, documents, or WebRTC video calls.",
      };
    }

    const uid = authCurrentUserId();
    if (!uid) return { success: false, messageId: null, error: "User must be signed in." };

    const msgId =
      params.customMessageId?.trim() ||
      `msg_${Math.random().toString(36).slice(2, 20)}`;
    const convRef = doc(db, "conversations", params.conversation.id);
    const msgRef = doc(collection(db, "conversations", params.conversation.id, "messages"), msgId);

    const nowMs = Date.now();
    const disappearsAt = params.disappearsInMinutes && params.disappearsInMinutes > 0
      ? new Date(nowMs + params.disappearsInMinutes * 60_000)
      : null;
    const scheduledFor = params.scheduledDelayMinutes && params.scheduledDelayMinutes > 0
      ? new Date(nowMs + params.scheduledDelayMinutes * 60_000)
      : null;

    const previewText = (() => {
      switch (params.mediaType) {
        case "image": return "Photo";
        case "voice": return "Voice message";
        case "document": return params.fileName || "Document";
        case "poll": return `Poll: ${(params.pollQuestion || "").slice(0, 60)}`;
        case "sticker": return "Sticker";
        default: return params.text.slice(0, 120);
      }
    })();

    const payload: DocumentData = {
      conversationId: params.conversation.id,
      senderId: uid,
      senderName: params.senderName || "User",
      senderAvatarUrl: params.senderAvatarUrl.slice(0, 1000),
      memberUids: params.conversation.memberUids,
      isPublic: params.conversation.isPublic,
      text: params.text.slice(0, 8000),
      mediaUrl: params.mediaUrl || "",
      mediaId: params.mediaId || "",
      mediaThumbnailUrl: params.mediaThumbnailUrl || "",
      mediaExpiresAt: params.mediaExpiresAt || 0,
      mediaExpired: params.mediaExpired || false,
      mediaType: params.mediaType || "text",
      fileName: params.fileName || "",
      fileSizeBytes: params.fileSizeBytes || 0,
      mimeType: params.mimeType || "",
      durationMs: params.durationMs || 0,
      replyToId: params.replyToId || "",
      replyToPreview: params.replyToPreview || "",
      forwardedFrom: params.forwardedFrom || "",
      topicId: params.topicId || "",
      isPinned: false,
      isDeleted: false,
      pollQuestion: params.pollQuestion || "",
      pollOptions: params.pollOptions || [],
      pollVotes: {},
      reactions: {},
      readBy: [uid],
      deliveredTo: [uid],
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    };
    if (disappearsAt) payload.disappearsAt = Timestamp.fromDate(disappearsAt);
    if (scheduledFor) payload.scheduledFor = Timestamp.fromDate(scheduledFor);

    try {
      const writeBatch = batch(db);
      writeBatch.set(msgRef, payload);
      writeBatch.update(convRef, {
        lastMessageText: previewText.slice(0, 500),
        lastMessageSenderId: uid,
        lastMessageAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      await writeBatch.commit();
      return { success: true, messageId: msgId, error: null };
    } catch (e) {
      return { success: false, messageId: null, error: getErrorMessage(e) };
    }
  }

  async editMessage(conversationId: string, messageId: string, newText: string): Promise<{ success: boolean; error: string | null }> {
    try {
      await updateDoc(doc(db, "conversations", conversationId, "messages", messageId), {
        text: newText.slice(0, 8000),
        editedAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  async deleteMessage(conversationId: string, messageId: string): Promise<{ success: boolean; error: string | null }> {
    try {
      await updateDoc(doc(db, "conversations", conversationId, "messages", messageId), {
        text: "This message was deleted",
        isDeleted: true,
        updatedAt: serverTimestamp(),
      });
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  async toggleReaction(conversationId: string, messageId: string, emoji: string): Promise<{ success: boolean; error: string | null }> {
    const uid = authCurrentUserId();
    if (!uid) return { success: false, error: "User must be signed in." };
    const msgRef = doc(db, "conversations", conversationId, "messages", messageId);
    try {
      await runTransaction(db, async (tx) => {
        const snap = await tx.get(msgRef);
        if (!snap.exists()) throw new Error("This message no longer exists.");
        const existing = (snap.data()?.reactions || {}) as Record<string, string[]>;
        const users = [...(existing[emoji] || [])];
        if (users.includes(uid)) {
          users.splice(users.indexOf(uid), 1);
        } else {
          users.push(uid);
        }
        if (users.length === 0) delete existing[emoji];
        else existing[emoji] = users;
        tx.update(msgRef, { reactions: existing, updatedAt: serverTimestamp() });
      });
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  async votePoll(conversationId: string, messageId: string, option: string): Promise<{ success: boolean; error: string | null }> {
    const uid = authCurrentUserId();
    if (!uid) return { success: false, error: "User must be signed in." };
    const msgRef = doc(db, "conversations", conversationId, "messages", messageId);
    try {
      await runTransaction(db, async (tx) => {
        const snap = await tx.get(msgRef);
        if (!snap.exists()) throw new Error("This poll no longer exists.");
        const message = snap.data();
        if (message?.mediaType !== "poll" || !message.pollOptions?.includes(option)) {
          throw new Error("That option is not part of this poll.");
        }
        const votes = (message.pollVotes || {}) as Record<string, string[]>;
        Object.values(votes).forEach((list) => {
          const idx = list.indexOf(uid);
          if (idx >= 0) list.splice(idx, 1);
        });
        if (!votes[option]) votes[option] = [];
        votes[option].push(uid);
        tx.update(msgRef, { pollVotes: votes, updatedAt: serverTimestamp() });
      });
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  async togglePinMessage(
    conversationId: string,
    messageId: string,
    previewText: string,
    pin: boolean
  ): Promise<{ success: boolean; error: string | null }> {
    const convRef = doc(db, "conversations", conversationId);
    const msgRef = doc(db, "conversations", conversationId, "messages", messageId);
    try {
      const writeBatch = batch(db);
      writeBatch.update(msgRef, { isPinned: pin, updatedAt: serverTimestamp() });
      writeBatch.update(convRef, {
        pinnedMessageId: pin ? messageId : "",
        pinnedMessageText: pin ? previewText.slice(0, 500) : "",
        updatedAt: serverTimestamp(),
      });
      await writeBatch.commit();
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  async markMessagesRead(conversationId: string, messages: ChatMessage[]): Promise<void> {
    const uid = authCurrentUserId();
    if (!uid) return;
    const unread = messages.filter((m) => m.senderId !== uid && !m.readBy.includes(uid)).slice(0, 15);
    if (unread.length === 0) return;
    try {
      const writeBatch = batch(db);
      unread.forEach((msg) => {
        const ref = doc(db, "conversations", conversationId, "messages", msg.id);
        writeBatch.update(ref, {
          readBy: arrayUnion(uid),
          deliveredTo: arrayUnion(uid),
          updatedAt: serverTimestamp(),
        });
      });
      await writeBatch.commit();
    } catch (e) {
      console.error("Mark read failed:", e);
    }
  }

  async updateMemberState(params: {
    conversationId: string;
    memberUids: string[];
    role?: string;
    isMuted?: boolean;
    isArchived?: boolean;
    markedUnread?: boolean;
    folder?: string;
    draftText?: string;
    customTheme?: string;
    customWallpaper?: string;
    typingNow?: boolean;
  }): Promise<void> {
    const uid = authCurrentUserId();
    if (!uid) return;
    const ref = doc(db, "conversations", params.conversationId, "members", uid);
    const payload: DocumentData = {
      userId: uid,
      conversationId: params.conversationId,
      memberUids: params.memberUids,
      role: params.role || "member",
      isMuted: params.isMuted ?? false,
      isArchived: params.isArchived ?? false,
      updatedAt: serverTimestamp(),
    };
    if (params.markedUnread !== undefined) payload.markedUnread = params.markedUnread;
    if (params.folder) payload.folder = params.folder.slice(0, 40);
    if (params.draftText !== undefined) payload.draftText = params.draftText.slice(0, 4000);
    if (params.customTheme) payload.customTheme = params.customTheme.slice(0, 40);
    if (params.customWallpaper) payload.customWallpaper = params.customWallpaper.slice(0, 100);
    if (params.typingNow === true) payload.typingAt = serverTimestamp();
    try {
      await setDoc(ref, payload, { merge: true });
    } catch (e) {
      console.error("Update member state failed:", e);
    }
  }

  async createTopic(params: {
    conversation: Conversation;
    title: string;
    iconEmoji: string;
  }): Promise<{ success: boolean; error: string | null }> {
    const uid = authCurrentUserId();
    if (!uid) return { success: false, error: "User must be signed in." };
    const topicId = `topic_${Math.random().toString(36).slice(2, 16)}`;
    const ref = doc(db, "conversations", params.conversation.id, "topics", topicId);
    try {
      await setDoc(ref, {
        conversationId: params.conversation.id,
        title: params.title.trim() || "General",
        iconEmoji: params.iconEmoji || "💬",
        createdBy: uid,
        isPublic: params.conversation.isPublic,
        memberUids: params.conversation.memberUids,
        createdAt: serverTimestamp(),
      });
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }
}

function authCurrentUserId(): string | null {
  return auth?.currentUser?.uid ?? null;
}
