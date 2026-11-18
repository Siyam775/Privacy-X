"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { auth, signOut as firebaseSignOut } from "@/lib/firebase";
import { supabaseMediaService } from "@/services/supabaseMediaService";
import { AuthRepository } from "@/services/authRepository";
import { SocialRepository } from "@/services/socialRepository";
import { useAuthStore, useThemeStore } from "@/stores/index";
import { UserAvatar, GlassCard, ToggleSwitch } from "@/components/ui";
import type { AppThemeId, AppearanceMode, BlockEdge, UserSession } from "@/types/index";
import { cn } from "@/lib/utils";

const authRepo = new AuthRepository();
const socialRepo = new SocialRepository();
export default function ProfilePage() {
  const router = useRouter();
  const { user, isSuperAdmin, signOut: clearAuthStore } = useAuthStore();
  const { themeId, setTheme, setAppearance, setAccentColor, setReducedMotion, appearanceMode, accentColorHex, reducedMotion } = useThemeStore();
  const [isEditing, setIsEditing] = useState(false);
  const [editName, setEditName] = useState("");
  const [editBio, setEditBio] = useState("");
  const [editPhotoUrl, setEditPhotoUrl] = useState("");
  const [followersCount, setFollowersCount] = useState(0);
  const [followingCount, setFollowingCount] = useState(0);
  const [postsCount, setPostsCount] = useState(0);
  const [profileError, setProfileError] = useState("");
  const [profileBusy, setProfileBusy] = useState(false);
  const [sessions, setSessions] = useState<UserSession[]>([]);
  const [blockedUsers, setBlockedUsers] = useState<BlockEdge[]>([]);

  useEffect(() => {
    if (!user) return;
    const unsubFollowers = socialRepo.observeFollowers(user.userId)((follows) => {
      setFollowersCount(follows.length);
    });
    const unsubFollowing = socialRepo.observeFollowing(user.userId)((follows) => {
      setFollowingCount(follows.length);
    });
    const unsubPosts = socialRepo.observePosts()((posts) => setPostsCount(posts.filter((post) => post.authorId === user.userId).length));
    const unsubSessions = authRepo.observeSessions(user.userId)(setSessions);
    const unsubBlocked = socialRepo.observeBlockedUsers(user.userId)(setBlockedUsers);
    return () => { unsubFollowers(); unsubFollowing(); unsubPosts(); unsubSessions(); unsubBlocked(); };
  }, [user]);

  const revokeSession = async (sessionId: string) => {
    if (!window.confirm("Remove this session record? This does not remotely sign the device out.")) return;
    const result = await authRepo.revokeSession(sessionId);
    if (!result.success) setProfileError(result.error || "Could not revoke session.");
  };

  const unblockUser = async (targetUserId: string) => {
    const result = await socialRepo.toggleBlockUser(targetUserId, true);
    if (!result.success) setProfileError(result.error || "Could not unblock user.");
  };

  const handleUpdateProfile = async () => {
    setProfileBusy(true);
    setProfileError("");
    const result = await authRepo.updateProfile({
      displayName: editName,
      bio: editBio,
      statusText: user?.statusText || "Available",
      photoUrl: editPhotoUrl,
      profileVisibility: user?.profileVisibility || "everyone",
      lastSeenPrivacy: user?.lastSeenPrivacy || "everyone",
      storyPrivacy: user?.storyPrivacy || "public",
      customAccentHex: accentColorHex,
    });
    setProfileBusy(false);
    if (result.success) setIsEditing(false);
    else setProfileError(result.error || "Profile update failed.");
  };

  const handlePhotoSelect = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    setProfileError("");
    try {
      const uploaded = await supabaseMediaService.uploadMedia(file, "profile", true);
      const result = await authRepo.updateProfile({
        displayName: user?.displayName || "User",
        bio: user?.bio || "",
        statusText: user?.statusText || "Available",
        photoUrl: uploaded.secureUrl,
        profileVisibility: user?.profileVisibility || "everyone",
        lastSeenPrivacy: user?.lastSeenPrivacy || "everyone",
        storyPrivacy: user?.storyPrivacy || "public",
        customAccentHex: accentColorHex,
      });
      if (!result.success) throw new Error(result.error || "Could not save the profile photo.");
      setEditPhotoUrl(uploaded.secureUrl);
    } catch (cause) {
      setProfileError(cause instanceof Error ? cause.message : "Photo upload failed.");
    } finally {
      event.target.value = "";
    }
  };

  const handleSignOut = async () => {
    await firebaseSignOut(auth);
    clearAuthStore();
  };

  const handleExportData = async () => {
    const result = await authRepo.exportUserDataJson();
    if (result.success && result.data) {
      const blob = new Blob([result.data], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `privacyx_export_${Date.now()}.json`;
      a.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } else {
      alert(result.error || "Export failed");
    }
  };

  const handleDeleteAccount = async () => {
    if (!confirm("Are you sure you want to delete your account? This cannot be undone.")) return;
    const result = await authRepo.deleteAccount();
    if (result.success) {
      await firebaseSignOut(auth);
      clearAuthStore();
    } else {
      alert(result.error || "Delete failed");
    }
  };

  return (
    <div className="flex min-h-dvh flex-col overflow-y-auto bg-white dark:bg-gray-900 theme-transition pb-28">
      {/* Header */}
      <div className="border-b border-white/30 bg-white/70 px-3 py-3 backdrop-blur dark:border-gray-700 dark:bg-gray-900/70 sm:px-6 sm:py-4 shrink-0 z-10 sticky top-0">
        <div className="flex items-center justify-between">
          <button aria-label="Back to chats" onClick={() => router.push("/")} className="p-2 rounded-full hover:bg-white/30 dark:hover:bg-gray-800 text-gray-600 dark:text-gray-300">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <h1 className="text-base font-bold text-gray-900 dark:text-white sm:text-xl">Profile & Settings</h1>
          <button
            onClick={handleSignOut}
            className="px-4 py-2 rounded-xl text-sm font-medium text-rose-600 dark:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-900/20 transition-colors"
          >
            Sign Out
          </button>
        </div>
      </div>

        <div className="w-full max-w-2xl mx-auto space-y-6 p-4 sm:space-y-8 sm:p-6 md:p-8">
        {profileError && <p role="alert" className="rounded-xl bg-rose-500/10 p-3 text-sm text-rose-600 dark:text-rose-400">{profileError}</p>}
        {/* Profile Section */}
        <div className="flex items-center gap-6">
          <div className="relative">
            <UserAvatar name={user?.displayName || "User"} photoUrl={editPhotoUrl || user?.photoUrl} size={96} isOnline={user?.isOnline} />
            <label title="Change profile photo" className="absolute bottom-0 right-0 flex h-10 w-10 cursor-pointer items-center justify-center rounded-full bg-violet-600 text-white shadow-lg transition-colors hover:bg-violet-700">
              <input type="file" accept="image/*" onChange={handlePhotoSelect} className="sr-only" />
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
            </label>
          </div>
          <div className="flex-1">
            <div className="flex items-center gap-2">
              <h2 className="text-2xl font-bold text-gray-900 dark:text-white">{user?.displayName || "User"}</h2>
              {user?.username && (
                <span className="text-sm text-gray-500 dark:text-gray-400">@{user.username}</span>
              )}
              {isSuperAdmin() && (
                <span className="px-2 py-0.5 rounded text-xs font-bold bg-violet-100 text-violet-700 dark:bg-violet-900/30 dark:text-violet-300 border border-violet-200/50 dark:border-violet-500/30">
                  Super Admin
                </span>
              )}
            </div>
            <p className="text-gray-500 dark:text-gray-400 mt-1">{user?.bio || "No bio yet"}</p>
            <p className="text-xs text-gray-400 mt-2">
              {user?.isOnline ? "🟢 Online" : "⚪ Offline"} • {user?.statusText || "Available"}
            </p>
          </div>
        </div>

        {/* Quick Edit */}
        {isEditing ? (
          <GlassCard elevated>
            <div className="space-y-4 p-6">
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Display Name</label>
                <input
                  value={editName}
                  onChange={(e) => setEditName(e.target.value)}
                  className="w-full p-2 rounded-lg border border-white/30 dark:border-gray-700 bg-transparent text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-violet-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Bio</label>
                <textarea
                  value={editBio}
                  onChange={(e) => setEditBio(e.target.value)}
                  rows={3}
                  className="w-full p-2 rounded-lg border border-white/30 dark:border-gray-700 bg-transparent text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-violet-500"
                />
              </div>
              <div className="flex gap-3">
                <button onClick={handleUpdateProfile} disabled={profileBusy} className="flex-1 py-2.5 rounded-xl bg-violet-600 text-white font-bold hover:bg-violet-700 disabled:opacity-60">{profileBusy ? "Saving…" : "Save"}</button>
                <button onClick={() => setIsEditing(false)} className="flex-1 py-2.5 rounded-xl text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800">Cancel</button>
              </div>
            </div>
          </GlassCard>
        ) : (
          <button
            onClick={() => { setEditName(user?.displayName || ""); setEditBio(user?.bio || ""); setEditPhotoUrl(user?.photoUrl || ""); setIsEditing(true); }}
            className="px-4 py-2 rounded-xl text-sm font-medium text-violet-600 dark:text-violet-400 hover:bg-violet-50 dark:hover:bg-violet-900/20 transition-colors border border-violet-200/50 dark:border-violet-500/30"
          >
            Edit Profile
          </button>
        )}

        {/* Stats */}
        <div className="grid grid-cols-3 gap-2 sm:gap-4">
          <div className="bg-white/50 dark:bg-gray-800/50 rounded-xl p-2 sm:p-4 border border-white/20 dark:border-gray-700 text-center">
            <div className="text-2xl font-bold text-gray-900 dark:text-white">{followersCount}</div>
            <div className="text-xs text-gray-500 dark:text-gray-400 mt-1">Followers</div>
          </div>
          <div className="bg-white/50 dark:bg-gray-800/50 rounded-xl p-2 sm:p-4 border border-white/20 dark:border-gray-700 text-center">
            <div className="text-2xl font-bold text-gray-900 dark:text-white">{followingCount}</div>
            <div className="text-xs text-gray-500 dark:text-gray-400 mt-1">Following</div>
          </div>
          <div className="bg-white/50 dark:bg-gray-800/50 rounded-xl p-2 sm:p-4 border border-white/20 dark:border-gray-700 text-center">
            <div className="text-2xl font-bold text-gray-900 dark:text-white">{postsCount}</div>
            <div className="text-xs text-gray-500 dark:text-gray-400 mt-1">Posts</div>
          </div>
        </div>

        <GlassCard elevated>
          <div className="p-6 space-y-6">
            <section>
              <h3 className="text-lg font-bold text-gray-900 dark:text-white">Active sessions</h3>
              <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">Review recorded sign-ins. Removing a record does not remotely sign that device out.</p>
              {sessions.length ? <ul className="mt-3 divide-y divide-gray-200 dark:divide-gray-700">
                {sessions.map((session) => <li key={session.id} className="flex items-center justify-between gap-3 py-3">
                  <div className="min-w-0"><p className="truncate text-sm font-medium text-gray-900 dark:text-white">{session.deviceName || session.platform || "Unknown device"}{session.isActive ? " · Current" : ""}</p><p className="text-xs text-gray-500">Last active {session.lastActiveAt?.toLocaleString?.() || "recently"}</p></div>
                  <button onClick={() => void revokeSession(session.id)} className="shrink-0 rounded-lg px-3 py-2 text-xs font-semibold text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-900/20">Remove record</button>
                </li>)}
              </ul> : <p className="mt-3 text-sm text-gray-500">No session records found.</p>}
            </section>
            <section>
              <h3 className="text-lg font-bold text-gray-900 dark:text-white">Blocked accounts</h3>
              {blockedUsers.length ? <ul className="mt-3 divide-y divide-gray-200 dark:divide-gray-700">
                {blockedUsers.map((blocked) => <li key={blocked.id} className="flex items-center justify-between gap-3 py-3"><span className="truncate text-sm text-gray-700 dark:text-gray-300">{blocked.blockedId}</span><button onClick={() => void unblockUser(blocked.blockedId)} className="shrink-0 rounded-lg px-3 py-2 text-xs font-semibold text-violet-600 hover:bg-violet-50 dark:hover:bg-violet-900/20">Unblock</button></li>)}
              </ul> : <p className="mt-2 text-sm text-gray-500">You haven’t blocked anyone.</p>}
            </section>
          </div>
        </GlassCard>

        {/* Appearance Settings */}
        <GlassCard elevated>
          <div className="p-6">
            <h3 id="appearance" className="text-lg font-bold text-gray-900 dark:text-white mb-6">Appearance</h3>

            {/* Theme Selector */}
            <div className="mb-6">
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">Theme</label>
              <div className="flex gap-3 overflow-x-auto pb-2">
                {(["material_liquid_glass", "pure_ios_glass", "classic_bw"] as const).map((t: AppThemeId) => {
                  const info = { material_liquid_glass: { title: "Material Liquid Glass", subtitle: "Lavender, purple & pink ambient glass" }, pure_ios_glass: { title: "Pure iOS Glass", subtitle: "Deep translucent crystal" }, classic_bw: { title: "Classic Black & White", subtitle: "High-contrast minimal monochrome" } };
                  const selected = t === themeId;
                  return (
                    <button
                      key={t}
                      onClick={() => setTheme(t)}
                      className={cn(
                        "flex flex-col items-center gap-2 shrink-0 px-4 py-3 rounded-2xl border transition-all",
                        selected
                          ? "border-violet-500 bg-violet-50 dark:bg-violet-900/20 shadow-lg"
                          : "border-white/30 dark:border-gray-700 hover:border-violet-300 dark:hover:border-violet-500/50"
                      )}
                    >
                      <div className={cn("w-10 h-10 rounded-xl flex items-center justify-center border border-white/50", selected ? "ring-2 ring-violet-500" : "")}>
                        {t === "material_liquid_glass" && "🌈"}
                        {t === "pure_ios_glass" && "🔮"}
                        {t === "classic_bw" && "⚫"}
                      </div>
                      <span className={cn("text-xs font-medium", selected ? "text-violet-700 dark:text-violet-300 font-bold" : "text-gray-500 dark:text-gray-400")}>
                        {info[t].title.split(" ")[0]}
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Appearance Mode */}
            <div className="mb-6">
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">Color Mode</label>
              <div className="flex gap-2">
                {(["system", "light", "dark"] as const).map((mode: AppearanceMode) => (
                  <button
                    key={mode}
                    onClick={() => setAppearance(mode)}
                    className={cn(
                      "flex-1 py-2 rounded-lg text-xs font-medium transition-colors",
                      appearanceMode === mode
                        ? "bg-violet-600 text-white"
                        : "bg-white/30 dark:bg-gray-800/30 text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700"
                    )}
                  >
                    {mode.charAt(0).toUpperCase() + mode.slice(1)}
                  </button>
                ))}
              </div>
            </div>

            {/* Accent Color */}
            <div className="mb-6">
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">Accent Color</label>
              <input
                type="color"
                value={accentColorHex || "#7C3AED"}
                onChange={(e) => setAccentColor(e.target.value)}
                className="w-12 h-12 rounded-full border-none cursor-pointer bg-transparent"
              />
            </div>

            {/* Reduced Motion */}
            <div className="flex items-center justify-between">
              <div>
                <h4 className="text-sm font-medium text-gray-900 dark:text-white">Reduced Motion</h4>
                <p className="text-xs text-gray-500 dark:text-gray-400">Disable animations for accessibility</p>
              </div>
              <ToggleSwitch checked={reducedMotion} onChange={setReducedMotion} />
            </div>
          </div>
        </GlassCard>

        {/* Danger Zone */}
        <GlassCard elevated>
          <div className="p-6">
            <h3 className="text-lg font-bold text-gray-900 dark:text-white mb-4 text-rose-600 dark:text-rose-400">Danger Zone</h3>
            <div className="space-y-3">
              <button
                onClick={handleExportData}
                className="w-full py-3 rounded-xl text-left text-gray-900 dark:text-white flex items-center justify-between hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
              >
                <span>Export Account Data</span>
                <span className="text-gray-400">→</span>
              </button>
              <button
                onClick={handleDeleteAccount}
                className="w-full py-3 rounded-xl text-left text-rose-600 dark:text-rose-400 flex items-center justify-between hover:bg-rose-50 dark:hover:bg-rose-900/20 transition-colors"
              >
                <span>Delete Account</span>
                <span className="text-rose-400">→</span>
              </button>
            </div>
          </div>
        </GlassCard>
      </div>
    </div>
  );
}
