"use client";
import { getErrorMessage, normalizeFirestoreValue } from "@/lib/utils";

import {
  db,
  auth,
  doc,
  collection,
  query,
  where,
  orderBy,
  limit,
  onSnapshot,
  setDoc,
  updateDoc,
  deleteDoc,
  serverTimestamp,
  Timestamp,
  runTransaction,
} from "@/lib/firebase";
import {
  Story,
  StoryView,
  SocialPost,
  PostComment,
  FollowEdge,
  BlockEdge,
  SavedMessage,
  NotificationItem,
} from "@/types/index";

const LIKE_REACTION = "\u2764\uFE0F";

export class SocialRepository {
  observeActiveStories(currentUserId: string): (callback: (stories: Story[]) => void) => () => void {
    return (callback) => {
      const q = query(collection(db, "stories"), where("isArchived", "==", false), limit(60));
      const unsubscribe = onSnapshot(q, (snap) => {
        const now = new Date();
        const list = snap.docs
          .map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as Story)
          .filter((s) => {
            const notExpired = !s.expiresAt || s.expiresAt.getTime() > now.getTime();
            const notHidden = !s.hiddenUids.includes(currentUserId);
            const canSee =
              s.authorId === currentUserId ||
              s.privacy === "public" ||
              s.privacy === "followers" ||
              s.allowedUids.includes(currentUserId);
            return notExpired && notHidden && canSee;
          })
          .sort((a, b) => (b.createdAt?.getTime() ?? 0) - (a.createdAt?.getTime() ?? 0));
        callback(list);
      });
      return unsubscribe;
    };
  }

  observeMyArchivedStories(userId: string): (callback: (stories: Story[]) => void) => () => void {
    return (callback) => {
      const q = query(collection(db, "stories"), where("authorId", "==", userId), limit(50));
      const unsubscribe = onSnapshot(q, (snap) => {
        callback(snap.docs.map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as Story).sort((a, b) => (b.createdAt?.getTime() ?? 0) - (a.createdAt?.getTime() ?? 0)));
      });
      return unsubscribe;
    };
  }

  observeStoryViewers(storyId: string): (callback: (views: StoryView[]) => void) => () => void {
    return (callback) => {
      const q = query(collection(db, "stories", storyId, "views"));
      const unsubscribe = onSnapshot(q, (snap) => {
        callback(snap.docs.map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as StoryView));
      });
      return unsubscribe;
    };
  }

  async createStory(params: {
    authorName: string;
    authorAvatarUrl: string;
    caption: string;
    mediaType: string;
    mediaUrl: string;
    backgroundGradient: string;
    privacy: string;
    allowedUids: string[];
    hiddenUids: string[];
    highlightTitle: string;
  }): Promise<{ success: boolean; storyId: string | null; error: string | null }> {
    const uid = authCurrentUserId();
    if (!uid) return { success: false, storyId: null, error: "User must be signed in." };
    const storyId = `story_${Math.random().toString(36).slice(2, 18)}`;
    const expiresAt = new Date(Date.now() + 24 * 60 * 60 * 1000);
    try {
      await setDoc(doc(db, "stories", storyId), {
        authorId: uid,
        authorName: params.authorName || "User",
        authorAvatarUrl: params.authorAvatarUrl.slice(0, 1000),
        mediaUrl: params.mediaUrl.slice(0, 1000),
        mediaType: params.mediaType,
        caption: params.caption.trim().slice(0, 500),
        backgroundGradient: params.backgroundGradient.slice(0, 64),
        privacy: params.privacy,
        allowedUids: params.allowedUids.slice(0, 200),
        hiddenUids: params.hiddenUids.slice(0, 200),
        isArchived: false,
        highlightTitle: params.highlightTitle.trim().slice(0, 60),
        viewCount: 0,
        reactions: {},
        expiresAt: Timestamp.fromDate(expiresAt),
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      return { success: true, storyId, error: null };
    } catch (e) {
      return { success: false, storyId: null, error: getErrorMessage(e) };
    }
  }

  async recordStoryView(story: Story, viewerName: string, reaction: string = ""): Promise<void> {
    const uid = authCurrentUserId();
    if (!uid) return;
    const storyRef = doc(db, "stories", story.id);
    const viewRef = doc(db, "stories", story.id, "views", uid);
    try {
      await runTransaction(db, async (tx) => {
        const [existing, storySnap] = await Promise.all([tx.get(viewRef), tx.get(storyRef)]);
        if (!storySnap.exists()) return;
        tx.set(viewRef, {
          viewerId: uid,
          viewerName: viewerName || "User",
          storyAuthorId: story.authorId,
          reaction: reaction.slice(0, 16),
          viewedAt: serverTimestamp(),
        }, { merge: true });
        if (!existing.exists() && story.authorId !== uid) {
          tx.update(storyRef, {
            viewCount: Number(storySnap.data().viewCount || 0) + 1,
            updatedAt: serverTimestamp(),
          });
        }
        });
    } catch (e) {
      console.error("Record story view failed:", e);
    }
  }

  async archiveOrHighlightStory(storyId: string, archive: boolean, highlightTitle: string): Promise<{ success: boolean; error: string | null }> {
    try {
      await updateDoc(doc(db, "stories", storyId), {
        isArchived: archive,
        highlightTitle: highlightTitle.trim().slice(0, 60),
        updatedAt: serverTimestamp(),
      });
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  observePosts(): (callback: (posts: SocialPost[]) => void) => () => void {
    return (callback) => {
      const q = query(collection(db, "posts"), orderBy("createdAt", "desc"), limit(40));
      const unsubscribe = onSnapshot(q, (snap) => {
        callback(snap.docs.map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as SocialPost));
      });
      return unsubscribe;
    };
  }

  observePostComments(postId: string): (callback: (comments: PostComment[]) => void) => () => void {
    return (callback) => {
      const q = query(
        collection(db, "posts", postId, "comments"),
        orderBy("createdAt", "asc")
      );
      const unsubscribe = onSnapshot(q, (snap) => {
        callback(snap.docs.map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as PostComment));
      });
      return unsubscribe;
    };
  }

  async createPost(params: {
    authorName: string;
    authorAvatarUrl: string;
    caption: string;
    mediaUrl: string;
    collectionTag: string;
  }): Promise<{ success: boolean; postId: string | null; error: string | null }> {
    const uid = authCurrentUserId();
    if (!uid) return { success: false, postId: null, error: "User must be signed in." };
    const postId = `post_${Math.random().toString(36).slice(2, 18)}`;
    try {
      await setDoc(doc(db, "posts", postId), {
        authorId: uid,
        authorName: params.authorName || "User",
        authorAvatarUrl: params.authorAvatarUrl.slice(0, 1000),
        caption: params.caption.trim().slice(0, 2000),
        mediaUrl: params.mediaUrl.slice(0, 1000),
        collectionTag: params.collectionTag || "General",
        reactions: {},
        commentCount: 0,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      return { success: true, postId, error: null };
    } catch (e) {
      return { success: false, postId: null, error: getErrorMessage(e) };
    }
  }

  async togglePostLike(postId: string): Promise<{ success: boolean; error: string | null }> {
    const uid = authCurrentUserId();
    if (!uid) return { success: false, error: "User must be signed in." };
    const ref = doc(db, "posts", postId);
    try {
      await runTransaction(db, async (tx) => {
        const snap = await tx.get(ref);
        if (!snap.exists()) throw new Error("This post no longer exists.");
        const reactions = (snap.data()?.reactions || {}) as Record<string, string[]>;
        const likes = [...(reactions[LIKE_REACTION] || [])];
        if (likes.includes(uid)) likes.splice(likes.indexOf(uid), 1);
        else likes.push(uid);
        tx.update(ref, { reactions: { ...reactions, [LIKE_REACTION]: likes }, updatedAt: serverTimestamp() });
      });
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  async addPostComment(postId: string, authorName: string, text: string): Promise<{ success: boolean; error: string | null }> {
    const uid = authCurrentUserId();
    if (!uid) return { success: false, error: "User must be signed in." };
    const commentId = `cmt_${Math.random().toString(36).slice(2, 18)}`;
    const postRef = doc(db, "posts", postId);
    const commentRef = doc(db, "posts", postId, "comments", commentId);
    try {
      await runTransaction(db, async (tx) => {
        const post = await tx.get(postRef);
        if (!post.exists()) throw new Error("This post no longer exists.");
        tx.set(commentRef, {
          postId,
          authorId: uid,
          authorName: authorName || "User",
          text: text.trim().slice(0, 1000),
          createdAt: serverTimestamp(),
        });
        tx.update(postRef, {
          commentCount: Number(post.data().commentCount || 0) + 1,
          updatedAt: serverTimestamp(),
        });
      });
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  observeFollowing(userId: string): (callback: (follows: FollowEdge[]) => void) => () => void {
    return (callback) => {
      const q = query(collection(db, "follows"), where("followerId", "==", userId));
      const unsubscribe = onSnapshot(q, (snap) => callback(snap.docs.map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as FollowEdge)));
      return unsubscribe;
    };
  }

  observeFollowers(userId: string): (callback: (follows: FollowEdge[]) => void) => () => void {
    return (callback) => {
      const q = query(collection(db, "follows"), where("followedId", "==", userId));
      const unsubscribe = onSnapshot(q, (snap) => callback(snap.docs.map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as FollowEdge)));
      return unsubscribe;
    };
  }

  async toggleFollow(targetUserId: string, currentlyFollowing: boolean): Promise<{ success: boolean; error: string | null }> {
    const uid = authCurrentUserId();
    if (!uid) return { success: false, error: "User must be signed in." };
    const docId = `${uid}_${targetUserId}`;
    const ref = doc(db, "follows", docId);
    try {
      if (currentlyFollowing) {
        await deleteDoc(ref);
      } else {
        await setDoc(ref, { followerId: uid, followedId: targetUserId, createdAt: serverTimestamp() });
      }
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  observeBlockedUsers(userId: string): (callback: (blocks: BlockEdge[]) => void) => () => void {
    return (callback) => {
      const q = query(collection(db, "blocks"), where("blockerId", "==", userId));
      const unsubscribe = onSnapshot(q, (snap) => callback(snap.docs.map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as BlockEdge)));
      return unsubscribe;
    };
  }

  async toggleBlockUser(targetUserId: string, currentlyBlocked: boolean): Promise<{ success: boolean; error: string | null }> {
    const uid = authCurrentUserId();
    if (!uid) return { success: false, error: "User must be signed in." };
    const docId = `${uid}_${targetUserId}`;
    const ref = doc(db, "blocks", docId);
    try {
      if (currentlyBlocked) {
        await deleteDoc(ref);
      } else {
        await setDoc(ref, { blockerId: uid, blockedId: targetUserId, createdAt: serverTimestamp() });
      }
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  async submitReport(params: {
    targetType: string;
    targetId: string;
    reason: string;
    details: string;
  }): Promise<{ success: boolean; reportId: string | null; error: string | null }> {
    const uid = authCurrentUserId();
    if (!uid) return { success: false, reportId: null, error: "User must be signed in." };
    const reportId = `rep_${Math.random().toString(36).slice(2, 18)}`;
    try {
      await setDoc(doc(db, "reports", reportId), {
        reporterId: uid,
        targetType: params.targetType,
        targetId: params.targetId.slice(0, 128),
        reason: params.reason.trim().slice(0, 120),
        details: params.details.trim().slice(0, 1000),
        status: "open",
        reviewedBy: "",
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      return { success: true, reportId, error: null };
    } catch (e) {
      return { success: false, reportId: null, error: getErrorMessage(e) };
    }
  }

  observeSavedMessages(userId: string): (callback: (messages: SavedMessage[]) => void) => () => void {
    return (callback) => {
      const q = query(
        collection(db, "users", userId, "savedMessages"),
        orderBy("createdAt", "desc"),
        limit(100)
      );
      const unsubscribe = onSnapshot(q, (snap) => callback(snap.docs.map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as SavedMessage)));
      return unsubscribe;
    };
  }

  async saveMessageBookmark(params: {
    content: string;
    sourceConversationId?: string;
    sourceSenderName?: string;
    mediaUrl?: string;
    mediaType?: string;
  }): Promise<{ success: boolean; error: string | null }> {
    const uid = authCurrentUserId();
    if (!uid) return { success: false, error: "User must be signed in." };
    const itemId = `saved_${Math.random().toString(36).slice(2, 18)}`;
    try {
      await setDoc(doc(db, "users", uid, "savedMessages", itemId), {
        userId: uid,
        content: params.content.slice(0, 5000),
        sourceConversationId: params.sourceConversationId || "",
        sourceSenderName: params.sourceSenderName || "",
        mediaUrl: params.mediaUrl || "",
        mediaType: params.mediaType || "text",
        createdAt: serverTimestamp(),
      });
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  async deleteSavedMessage(itemId: string): Promise<{ success: boolean; error: string | null }> {
    const uid = authCurrentUserId();
    if (!uid) return { success: false, error: "User must be signed in." };
    try {
      await deleteDoc(doc(db, "users", uid, "savedMessages", itemId));
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  observeNotifications(userId: string): (callback: (notifs: NotificationItem[]) => void) => () => void {
    return (callback) => {
      const q = query(
        collection(db, "users", userId, "notifications"),
        orderBy("createdAt", "desc"),
        limit(50)
      );
      const unsubscribe = onSnapshot(q, (snap) => callback(snap.docs.map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as NotificationItem)));
      return unsubscribe;
    };
  }

  async markNotificationRead(notificationId: string): Promise<void> {
    const uid = authCurrentUserId();
    if (!uid) return;
    try {
      await updateDoc(doc(db, "users", uid, "notifications", notificationId), {
        isRead: true,
        updatedAt: serverTimestamp(),
      });
    } catch (e) {
      console.error("Mark notification read failed:", e);
    }
  }
}

function authCurrentUserId(): string | null {
  return auth.currentUser?.uid ?? null;
}
