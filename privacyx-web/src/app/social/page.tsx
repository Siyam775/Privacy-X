"use client";

import { useEffect, useState } from "react";
import Image from "next/image";
import { SocialRepository } from "@/services/socialRepository";
import { supabaseMediaService } from "@/services/supabaseMediaService";
import { useAuthStore, useSocialStore } from "@/stores/index";
import { UserAvatar } from "@/components/ui";
import { formatTime } from "@/lib/utils";
import { useRouter } from "next/navigation";

const socialRepo = new SocialRepository();
const LIKE_REACTION = "\u2764\uFE0F";

export default function SocialPage() {
  const router = useRouter();
  const { user } = useAuthStore();
  const { posts, setPosts } = useSocialStore();
  const [isCreating, setIsCreating] = useState(false);
  const [postCaption, setPostCaption] = useState("");
  const [postMedia, setPostMedia] = useState<File | null>(null);
  const [postPreview, setPostPreview] = useState<string | null>(null);
  const [collectionTag, setCollectionTag] = useState("General");
  const [error, setError] = useState("");
  const [followingIds, setFollowingIds] = useState<string[]>([]);

  useEffect(() => {
    if (!user) return;
    const unsub = socialRepo.observePosts()((posts) => {
      setPosts(posts);
    });
    const unsubFollowing = socialRepo.observeFollowing(user.userId)((edges) => setFollowingIds(edges.map((edge) => edge.followedId)));
    return () => { unsub(); unsubFollowing(); };
  }, [user, setPosts]);

  const handleImageSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setPostMedia(file);
      setPostPreview(URL.createObjectURL(file));
    }
  };

  const createPost = async () => {
    if (!postCaption.trim() && !postMedia) return;
    setIsCreating(true);
    try {
      let mediaUrl = "";
      if (postMedia) {
        const result = await supabaseMediaService.uploadMedia(postMedia, "social", true);
        mediaUrl = result.secureUrl;
      }

      const result = await socialRepo.createPost({
        authorName: user!.displayName || "User",
        authorAvatarUrl: user!.photoUrl || "",
        caption: postCaption,
        mediaUrl,
        collectionTag,
      });
      if (!result.success) throw new Error(result.error || "Failed to create post");

      setIsCreating(false);
      setPostCaption("");
      setPostMedia(null);
      setPostPreview(null);
      setCollectionTag("General");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create post");
    }
  };

  const toggleLike = async (postId: string) => {
    const result = await socialRepo.togglePostLike(postId);
    if (!result.success) setError(result.error || "Could not update like.");
  };

  const addComment = async (postId: string) => {
    const text = prompt("Add a comment:");
    if (text) {
      const result = await socialRepo.addPostComment(postId, user!.displayName || "User", text);
      if (!result.success) setError(result.error || "Could not add comment.");
    }
  };

  const toggleFollow = async (targetUserId: string) => {
    if (!user || targetUserId === user.userId) return;
    const result = await socialRepo.toggleFollow(targetUserId, followingIds.includes(targetUserId));
    if (!result.success) setError(result.error || "Could not update follow.");
  };

  const reportPost = async (postId: string) => {
    const reason = window.prompt("Why are you reporting this post?");
    if (!reason?.trim()) return;
    const result = await socialRepo.submitReport({ targetType: "post", targetId: postId, reason, details: "" });
    if (!result.success) setError(result.error || "Could not submit report.");
    else setError("Report submitted for review.");
  };

  const blockAuthor = async (targetUserId: string) => {
    if (!user || targetUserId === user.userId || !window.confirm("Block this account?")) return;
    const result = await socialRepo.toggleBlockUser(targetUserId, false);
    if (!result.success) setError(result.error || "Could not block account.");
    else setError("Account blocked.");
  };

  return (
    <div className="flex h-dvh min-h-0 flex-col theme-transition bg-orbs relative overflow-hidden">
      <div className="absolute inset-0 bg-gradient-to-b from-[var(--px-bg)] to-[var(--px-bg-secondary)] pointer-events-none" />
      {/* Header */}
      <div className="h-16 border-b border-[var(--px-border)] bg-[var(--px-surface)] backdrop-blur-lg shrink-0 flex items-center justify-between px-4 z-10 sticky top-0 relative z-20">
        <div className="flex items-center gap-3 cursor-pointer">
          <UserAvatar name={user?.displayName || "User"} photoUrl={user?.photoUrl} size={40} />
          <div>
            <h1 className="font-semibold text-[var(--px-text-primary)]">Social Feed</h1>
            <p className="text-xs text-[var(--px-text-muted)]">Discover & connect</p>
          </div>
        </div>
        <button aria-label="Browse spaces" onClick={() => router.push("/communities")} className="p-2 rounded-xl hover:bg-[var(--px-accent-glass)] text-[var(--px-text-secondary)] transition-all border border-transparent hover:border-white/30 dark:hover:border-gray-600/50">
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 20h14a2 2 0 002-2V6a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
          </svg>
        </button>
      </div>

      {/* Create Post */}
      <div className="p-4 bg-[var(--px-surface)] backdrop-blur-lg border-b border-[var(--px-border)] shrink-0 relative z-10">
        <div className="flex gap-3">
          <UserAvatar name={user?.displayName || "User"} photoUrl={user?.photoUrl} size={40} />
          <div className="flex-1">
            {isCreating ? (
              <div className="space-y-3">
                <textarea
                  value={postCaption}
                  onChange={(e) => setPostCaption(e.target.value)}
                  placeholder="What's on your mind?"
                  rows={3}
                  className="w-full p-2 rounded-xl border border-white/30 dark:border-gray-700 bg-transparent text-[var(--px-text-primary)] focus:outline-none focus:ring-2 focus:ring-violet-500"
                />
                <div className="flex items-center gap-3">
                  <label className="cursor-pointer p-2 rounded-xl hover:bg-white/30 dark:hover:bg-gray-800">
                    <input type="file" accept="image/*" onChange={handleImageSelect} className="hidden" />
                    <svg className="w-5 h-5 text-[var(--px-text-muted)]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                    </svg>
                  </label>
                  <select
                    value={collectionTag}
                    onChange={(e) => setCollectionTag(e.target.value)}
                    className="bg-transparent text-[var(--px-text-primary)] text-sm border-none focus:ring-0"
                  >
                    {["General", "Tech", "Life", "Work", "Fun"].map((tag) => (
                      <option key={tag} value={tag}>{tag}</option>
                    ))}
                  </select>
                </div>
                {postPreview && (
                  <Image src={postPreview} alt="Post image preview" width={800} height={400} unoptimized className="mb-2 h-48 w-full rounded-xl object-cover" />
                )}
                {error && <p role="alert" className="text-sm text-rose-600">{error}</p>}
                <div className="flex justify-end gap-2">
                  <button
                    onClick={() => setIsCreating(false)}
                    className="px-4 py-2 rounded-lg text-[var(--px-text-secondary)] hover:bg-white/100 dark:hover:bg-gray-800"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={createPost}
                    className="px-4 py-2 rounded-lg bg-violet-600 text-white font-bold hover:bg-violet-700 disabled:opacity-50"
                    disabled={!postCaption.trim() && !postMedia}
                  >
                    Post
                  </button>
                </div>
              </div>
            ) : (
              <div className="relative">
                <textarea
                  value={postCaption}
                  onChange={(e) => setPostCaption(e.target.value)}
                  placeholder="What's on your mind?"
                  rows={1}
                  className="w-full resize-none py-2 px-4 rounded-full border border-white/30 dark:border-gray-700 bg-transparent text-[var(--px-text-primary)] focus:outline-none focus:ring-2 focus:ring-violet-500/50"
                  onClick={() => setIsCreating(true)}
                />
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Feed */}
      <div className="min-h-0 flex-1 overflow-y-auto p-3 pb-28 sm:p-4 sm:pb-28 relative z-10">
        {error && !isCreating && <p role="alert" className="mb-4 rounded-xl bg-rose-500/10 p-3 text-sm text-rose-600">{error}</p>}
        {posts.length === 0 ? (
          <div className="text-center py-20">
            <div className="w-16 h-16 rounded-full bg-white/40 dark:bg-gray-800/40 mx-auto mb-4 flex items-center justify-center">
              <span className="text-3xl">📢</span>
            </div>
            <h3 className="text-lg font-medium text-[var(--px-text-primary)]">No posts yet</h3>
            <p className="text-[var(--px-text-muted)] mt-2">Be the first to share something!</p>
          </div>
        ) : (
          <div className="space-y-6">
            {posts.map((post) => (
              <div key={post.id} className="bg-[var(--px-surface)] backdrop-blur rounded-2xl p-4 border border-[var(--px-border)]">
                <div className="flex min-w-0 flex-wrap items-center gap-3 mb-3">
                  <UserAvatar name={post.authorName} photoUrl={post.authorAvatarUrl} size={40} />
                  {user?.userId !== post.authorId && <div className="ml-auto flex items-center gap-1 sm:gap-2">
                    <button onClick={() => void toggleFollow(post.authorId)} className="rounded-lg border border-violet-500/30 px-3 py-1.5 text-xs font-medium text-violet-600 hover:bg-violet-500/10">{followingIds.includes(post.authorId) ? "Following" : "Follow"}</button>
                    <details className="relative"><summary className="cursor-pointer list-none rounded-lg px-2 py-1 text-lg text-[var(--px-text-muted)] hover:bg-violet-500/10" aria-label="Post options">···</summary><div className="absolute right-0 z-20 mt-1 w-36 rounded-xl border border-[var(--px-border)] bg-[var(--px-surface)] p-1 shadow-xl"><button onClick={() => void reportPost(post.id)} className="w-full rounded-lg px-3 py-2 text-left text-xs hover:bg-violet-500/10">Report post</button><button onClick={() => void blockAuthor(post.authorId)} className="w-full rounded-lg px-3 py-2 text-left text-xs text-rose-600 hover:bg-rose-500/10">Block author</button></div></details>
                  </div>}
                  <div>
                    <h3 className="font-semibold text-[var(--px-text-primary)]">{post.authorName}</h3>
                    <p className="text-xs text-[var(--px-text-muted)]">{post.collectionTag} • {formatTime(post.createdAt)}</p>
                  </div>
                </div>
                {post.caption && <p className="text-[var(--px-text-primary)] mb-3 leading-relaxed">{post.caption}</p>}
                {post.mediaUrl && (
                  <Image src={post.mediaUrl} alt="Post" width={800} height={500} unoptimized className="mb-3 h-64 w-full rounded-xl object-cover" />
                )}
                <div className="flex items-center gap-4 text-sm">
                  <button
                    onClick={() => toggleLike(post.id)}
                    className="flex items-center gap-1.5 text-[var(--px-text-secondary)] hover:text-violet-600 dark:hover:text-violet-400 transition-colors"
                  >
                    <span>❤️</span>
                    <span>{post.reactions?.[LIKE_REACTION]?.length || 0}</span>
                  </button>
                  <button
                    onClick={() => addComment(post.id)}
                    className="flex items-center gap-1.5 text-[var(--px-text-secondary)] hover:text-violet-600 dark:hover:text-violet-400 transition-colors"
                  >
                    <span>💬</span>
                    <span>{post.commentCount}</span>
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
