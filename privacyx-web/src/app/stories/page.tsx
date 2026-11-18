"use client";

import { useEffect, useState } from "react";
import Image from "next/image";
import { SocialRepository } from "@/services/socialRepository";
import { supabaseMediaService } from "@/services/supabaseMediaService";
import { useAuthStore, useSocialStore } from "@/stores/index";
import { UserAvatar, Badge } from "@/components/ui";
import { formatTime, cn, getErrorMessage } from "@/lib/utils";
import type { Story } from "@/types/index";

const socialRepo = new SocialRepository();

export default function StoriesPage() {
  const { user } = useAuthStore();
  const { stories, setStories } = useSocialStore();
  const [myStories, setMyStories] = useState<Story[]>([]);
  const [isCreating, setIsCreating] = useState(false);
  const [storyCaption, setStoryCaption] = useState("");
  const [storyPrivacy, setStoryPrivacy] = useState("public");
  const [storyMedia, setStoryMedia] = useState<File | null>(null);
  const [storyPreview, setStoryPreview] = useState<string | null>(null);
  const [selectedStory, setSelectedStory] = useState<(typeof stories)[number] | null>(null);

  useEffect(() => {
    if (!user) return;
    const unsub = socialRepo.observeActiveStories(user.userId)((stories) => {
      setStories(stories);
    });
    const unsubMy = socialRepo.observeMyArchivedStories(user.userId)((stories) => {
      setMyStories(stories);
    });
    return () => { unsub(); unsubMy(); };
  }, [user, setStories]);

  const handleImageSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setStoryMedia(file);
      setStoryPreview(URL.createObjectURL(file));
    }
  };

  const createStory = async () => {
    if (!storyMedia && !storyCaption) return;
    setIsCreating(true);
    try {
      let mediaUrl = "";
      let mediaType = "text";

      if (storyMedia) {
        const result = await supabaseMediaService.uploadMedia(storyMedia, "stories", true);
        mediaUrl = result.secureUrl;
        mediaType = result.category === "image" ? "image" : "text";
      }

      const result = await socialRepo.createStory({
        authorName: user!.displayName || "User",
        authorAvatarUrl: user!.photoUrl || "",
        caption: storyCaption,
        mediaType,
        mediaUrl,
        backgroundGradient: "lavender_violet",
        privacy: storyPrivacy,
        allowedUids: [],
        hiddenUids: [],
        highlightTitle: "",
      });
      if (!result.success) throw new Error(result.error || "Failed to create story");

      setIsCreating(false);
      setStoryCaption("");
      setStoryPrivacy("public");
      setStoryMedia(null);
      setStoryPreview(null);
    } catch (err) {
      setIsCreating(false);
      alert(getErrorMessage(err, "Failed to create story"));
    }
  };

  const archiveOrHighlight = async (storyId: string) => {
    const title = window.prompt("Enter a highlight title, or leave blank to archive this story:");
    if (title === null) return;
    const result = await socialRepo.archiveOrHighlightStory(storyId, !title.trim(), title.trim());
    if (!result.success) alert(result.error || "Could not update story.");
  };

  return (
    <div className="flex min-h-dvh flex-col theme-transition bg-orbs relative">
      <div className="absolute inset-0 bg-gradient-to-b from-[var(--px-bg)] to-[var(--px-bg-secondary)] pointer-events-none" />
      {/* Header */}
      <div className="h-16 border-b border-[var(--px-border)] bg-[var(--px-surface)] backdrop-blur-lg shrink-0 flex items-center justify-between px-4 z-10 sticky top-0 relative z-20">
        <div className="flex items-center gap-3 cursor-pointer">
          <UserAvatar name={user?.displayName || "User"} photoUrl={user?.photoUrl} size={40} />
          <div>
            <h1 className="font-semibold text-[var(--px-text-primary)]">Stories</h1>
            <p className="text-xs text-[var(--px-text-muted)]">24-hour stories</p>
          </div>
        </div>
        <button aria-label="Create story" onClick={() => setIsCreating(true)} className="p-2 rounded-xl hover:bg-[var(--px-accent-glass)] text-[var(--px-text-secondary)] transition-all border border-transparent hover:border-white/30 dark:hover:border-gray-600/50">
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 13l4 4L19 7" />
          </svg>
        </button>
      </div>

      {/* Create Story */}
      <div className="p-4 bg-gradient-to-r from-violet-500 to-pink-500 rounded-2xl shadow-lg mb-6 relative overflow-hidden z-10">
        <div className="absolute inset-0 opacity-10 bg-white">
          <div className="absolute top-0 left-1/4 w-32 h-32 bg-white/20 rounded-full blur-3xl" />
          <div className="absolute bottom-0 right-1/4 w-32 h-32 bg-white/20 rounded-full blur-3xl" />
        </div>
        <div className="relative z-10 flex items-center gap-4">
          <div className="w-14 h-14 rounded-full bg-white/20 backdrop-blur flex items-center justify-center border border-white/30">
            <span className="text-2xl">📖</span>
          </div>
          <div className="flex-1">
            <h3 className="text-white font-bold">Create Story</h3>
            <p className="text-white/90 text-sm">Share a moment that disappears in 24 hours</p>
          </div>
          <button
            onClick={() => setIsCreating(true)}
            className="bg-white text-violet-600 px-4 py-2 rounded-xl font-bold hover:bg-violet-50 transition-colors"
          >
            Create
          </button>
        </div>
      </div>

      {/* Story Creation Modal */}
      {isCreating && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur flex items-center justify-center p-4">
          <div className="max-h-[calc(100dvh-2rem)] w-full max-w-md overflow-y-auto rounded-2xl border border-white/80 bg-white/95 p-4 backdrop-blur dark:border-gray-700 dark:bg-gray-900/95 sm:p-6 z-10">
            <h3 className="text-lg font-bold text-[var(--px-text-primary)] mb-4">New Story</h3>
            <div className="space-y-4">
              <textarea
                value={storyCaption}
                onChange={(e) => setStoryCaption(e.target.value)}
                placeholder="Add a caption..."
                rows={3}
                className="w-full p-3 rounded-xl border border-white/30 dark:border-gray-700 bg-transparent text-[var(--px-text-primary)] focus:outline-none focus:ring-2 focus:ring-violet-500"
              />
              <label className="block cursor-pointer p-4 rounded-xl border-2 border-dashed border-white/30 dark:border-gray-600 hover:bg-[var(--px-accent-glass)] transition-colors text-center">
                <input type="file" accept="image/*" onChange={handleImageSelect} className="hidden" />
                {storyPreview ? (
                  <Image src={storyPreview} alt="Story image preview" width={800} height={400} unoptimized className="mb-2 h-48 w-full rounded-xl object-cover" />
                ) : (
                  <div className="flex flex-col items-center gap-2 text-[var(--px-text-muted)]">
                    <span className="text-2xl">📷</span>
                    <span>Add photo (optional)</span>
                  </div>
                )}
              </label>
              <div className="flex gap-3">
                {["public", "followers", "close_friends"].map((p) => (
                  <button
                    key={p}
                    onClick={() => setStoryPrivacy(p)}
                    className={cn(
                      "flex-1 px-3 py-2 rounded-lg text-sm font-medium transition-colors",
                      storyPrivacy === p
                        ? "bg-violet-600 text-white"
                        : "bg-white/50 dark:bg-gray-800/50 text-[var(--px-text-secondary)] hover:bg-white/100 dark:hover:bg-gray-700"
                    )}
                  >
                    {p}
                  </button>
                ))}
              </div>
            </div>
            <div className="flex gap-3 mt-6">
              <button
                onClick={() => setIsCreating(false)}
                className="flex-1 py-3 rounded-xl text-[var(--px-text-secondary)] hover:bg-white/100 dark:hover:bg-gray-800 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={createStory}
                className="flex-1 py-3 rounded-xl bg-violet-600 text-white font-bold hover:bg-violet-700 transition-colors shadow-lg"
                disabled={!storyCaption && !storyMedia}
              >
                Share Story
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Active Stories */}
      <div className="flex-none p-3 pb-28 sm:p-4 sm:pb-28 relative z-10">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-bold text-[var(--px-text-primary)]">Active Stories</h2>
          <span className="text-xs font-medium text-violet-600 dark:text-violet-400">{stories.length} stories</span>
        </div>

        {stories.length === 0 ? (
          <div className="text-center py-20">
            <div className="w-16 h-16 rounded-full bg-white/40 dark:bg-gray-800/40 mx-auto mb-4 flex items-center justify-center">
              <span className="text-3xl">📖</span>
            </div>
            <h3 className="text-lg font-medium text-[var(--px-text-primary)]">No active stories</h3>
            <p className="text-[var(--px-text-muted)] mt-2">Check back later for updates from friends.</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {stories.map((story) => (
              <div
                key={story.id}
                role="button"
                tabIndex={0}
                onClick={() => {
                  setSelectedStory(story);
                  socialRepo.recordStoryView(story, user!.displayName || "User");
                }}
                onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); setSelectedStory(story); void socialRepo.recordStoryView(story, user!.displayName || "User"); } }}
                className="group cursor-pointer"
              >
                <div className="relative">
                  <div className="relative mb-2 flex h-14 w-14 items-center justify-center rounded-full border-4 border-transparent transition-all group-hover:border-violet-500/50">
                    {story.mediaUrl ? (
                      <Image src={story.mediaUrl} alt="Story" fill unoptimized sizes="56px" className="rounded-full border-2 border-white object-cover dark:border-gray-700" />
                    ) : (
                      <UserAvatar name={story.authorName} photoUrl={story.authorAvatarUrl} size={40} />
                    )}
                    <div className="absolute -right-1 -bottom-1 w-3 h-3 rounded-full bg-emerald-500 border-2 border-white dark:border-gray-900" />
                  </div>
                  <div className="w-full h-32 bg-white/20 dark:bg-gray-800/50 rounded-xl overflow-hidden relative">
                    {story.mediaUrl ? (
                      <Image src={story.mediaUrl} alt="Story" fill unoptimized sizes="(min-width: 1024px) 33vw, (min-width: 640px) 50vw, 100vw" className="object-cover" />
                    ) : (
                      <div className="flex items-center justify-center h-full bg-gradient-to-br from-violet-100 to-pink-100 dark:from-violet-900/30 dark:to-pink-900/30">
                        <span className="text-lg">📖</span>
                      </div>
                    )}
                    <div className="absolute top-2 right-2">
                      <Badge count={story.viewCount} />
                    </div>
                  </div>
                </div>
                <div className="flex items-center gap-2 mt-2">
                  <UserAvatar name={story.authorName} photoUrl={story.authorAvatarUrl} size={24} isOnline />
                  <span className="text-sm font-medium text-[var(--px-text-primary)] truncate">{story.authorName}</span>
                </div>
                {story.caption && (
                  <p className="text-xs text-[var(--px-text-muted)] mt-1 line-clamp-2">{story.caption}</p>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {selectedStory && <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/85 p-4" onClick={() => setSelectedStory(null)}>
        <div role="dialog" aria-modal="true" aria-label={`${selectedStory.authorName}'s story`} className="relative flex max-h-[90vh] w-full max-w-lg flex-col overflow-hidden rounded-3xl bg-gray-950 text-white" onClick={(event) => event.stopPropagation()}>
          <button aria-label="Close story" onClick={() => setSelectedStory(null)} className="absolute right-3 top-3 z-10 rounded-full bg-black/50 px-3 py-2 text-sm">Close</button>
          {selectedStory.mediaUrl ? <Image src={selectedStory.mediaUrl} alt={`${selectedStory.authorName}'s story`} width={800} height={1200} unoptimized className="max-h-[75vh] w-full object-contain" /> : <div className="flex min-h-72 items-center justify-center bg-gradient-to-br from-violet-700 to-pink-600 p-8 text-center text-2xl font-semibold">{selectedStory.caption}</div>}
          <div className="p-4"><p className="font-semibold">{selectedStory.authorName}</p>{selectedStory.mediaUrl && selectedStory.caption && <p className="mt-1 text-sm text-white/80">{selectedStory.caption}</p>}<div className="mt-3 flex gap-2" aria-label="React to story">{["❤️", "😂", "🔥"].map((emoji) => <button key={emoji} onClick={() => void socialRepo.recordStoryView(selectedStory, user?.displayName || "User", emoji)} className="rounded-full bg-white/15 px-3 py-2 text-lg hover:bg-white/25" aria-label={`React ${emoji}`}>{emoji}</button>)}</div></div>
        </div>
      </div>}

      {/* My Stories */}
      {myStories.length > 0 && (
        <div className="p-3 pb-28 border-t border-[var(--px-border)] relative z-10 sm:p-4 sm:pb-28">
          <h2 className="text-lg font-bold text-[var(--px-text-primary)] mb-4">My Stories</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {myStories.map((story) => (
              <div key={story.id} className="bg-[var(--px-surface)] rounded-xl p-3 border border-[var(--px-border)]">
                <div className="w-full h-24 bg-white/20 dark:bg-gray-800 rounded-lg mb-2 relative overflow-hidden">
                  {story.mediaUrl ? (
                    <Image src={story.mediaUrl} alt="Story" fill unoptimized sizes="(min-width: 1024px) 33vw, (min-width: 640px) 50vw, 100vw" className="object-cover" />
                  ) : (
                    <div className="flex items-center justify-center h-full text-[var(--px-text-muted)]">📖</div>
                  )}
                  <div className="absolute top-1 right-1">
                    <Badge count={story.viewCount} />
                  </div>
                </div>
                <p className="text-sm text-[var(--px-text-primary)] truncate">{story.caption || "No caption"}</p>
                <button onClick={() => void archiveOrHighlight(story.id)} className="mt-2 rounded-lg border border-violet-500/30 px-3 py-1.5 text-xs font-medium text-violet-600 hover:bg-violet-500/10">{story.highlightTitle ? `Highlight: ${story.highlightTitle}` : story.isArchived ? "Restore story" : "Archive / Highlight"}</button>
                <p className="text-xs text-[var(--px-text-muted)]">
                  {story.privacy} • {formatTime(story.createdAt)}
                </p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
