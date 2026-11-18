"use client";

import { useEffect, useState } from "react";
import { AdminRepository } from "@/services/adminRepository";
import { useAuthStore } from "@/stores/index";
import { UserAvatar } from "@/components/ui";
import { formatTime } from "@/lib/utils";
import { cn } from "@/lib/utils";
import { useRouter } from "next/navigation";
import type { AbuseReport, AdminLog, Conversation, PlatformAnnouncement, PlatformSettings, UserProfile } from "@/types/index";

const adminRepo = new AdminRepository();

export default function AdminPage() {
  const router = useRouter();
  const { user } = useAuthStore();
  const [users, setUsers] = useState<UserProfile[]>([]);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [reports, setReports] = useState<AbuseReport[]>([]);
  const [logs, setLogs] = useState<AdminLog[]>([]);
  const [announcements, setAnnouncements] = useState<PlatformAnnouncement[]>([]);
  const [settings, setSettings] = useState<PlatformSettings | null>(null);
  const [activeTab, setActiveTab] = useState("users");

  useEffect(() => {
    if (!user || !user.userId) return;
    if (user.userId !== "2bL4lJTTt9WMILpCclHeKNYOdz62") {
      return;
    }

    const unsubUsers = adminRepo.observeAllUsersForAdmin()((us) => setUsers(us));
    const unsubConvs = adminRepo.observeAllConversationsForAdmin()((cs) => setConversations(cs));
    const unsubReports = adminRepo.observeReportsForAdmin()((rs) => setReports(rs));
    const unsubLogs = adminRepo.observeAdminLogs()((ls) => setLogs(ls));
    const unsubAnnouncements = adminRepo.observePlatformAnnouncements()((items) => setAnnouncements(items));
    const unsubSettings = adminRepo.observePlatformSettings()((value) => setSettings(value));

    return () => {
      unsubUsers();
      unsubConvs();
      unsubReports();
      unsubLogs();
      unsubAnnouncements();
      unsubSettings();
    };
  }, [user]);

  const isSuperAdmin = user?.userId === "2bL4lJTTt9WMILpCclHeKNYOdz62";

  if (!isSuperAdmin) {
    return (
      <div className="flex h-dvh min-h-0 flex-col bg-white dark:bg-gray-900 theme-transition">
        <div className="border-b px-6 py-4 bg-white/70 dark:bg-gray-900/70 backdrop-blur">
          <h1 className="text-xl font-bold text-gray-900 dark:text-white">Super Admin Dashboard</h1>
        </div>
        <div className="flex-1 flex items-center justify-center p-8">
          <div className="text-center">
            <div className="w-16 h-16 rounded-full bg-rose-100 dark:bg-rose-900/20 flex items-center justify-center mb-4">
              <span className="text-3xl">🔒</span>
            </div>
            <h2 className="text-lg font-medium text-gray-900 dark:text-white">Access Denied</h2>
            <p className="text-gray-500 dark:text-gray-400 mt-2">
              Super Admin access is restricted to the designated UID only.
            </p>
          </div>
        </div>
      </div>
    );
  }

  const isDark = typeof window !== "undefined" && window.matchMedia("(prefers-color-scheme: dark)").matches;

  const suspendUser = async (uid: string, currentlySuspended: boolean) => {
    await adminRepo.setUserSuspended(uid, !currentlySuspended);
  };

  const updateReportStatus = async (reportId: string, status: string) => {
    await adminRepo.updateReportStatus(reportId, status);
  };

  const publishAnnouncement = async () => {
    const title = prompt("Announcement title:");
    if (!title) return;
    const body = prompt("Announcement body:");
    if (!body) return;
    const priority = prompt("Priority (normal/high/urgent):", "normal");
    if (!priority) return;
    await adminRepo.publishAnnouncement(title, body, priority);
  };

  return (
    <div className={`flex h-dvh min-h-0 flex-col ${isDark ? "dark" : ""} theme-transition bg-white dark:bg-gray-900`}>
      {/* Header */}
      <div className="border-b border-white/30 dark:border-gray-700 bg-white/70 dark:bg-gray-900/70 backdrop-blur shrink-0 px-3 py-3 z-10 sticky top-0 sm:px-6 sm:py-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3 cursor-pointer">
            <button aria-label="Back to chats" onClick={() => router.push("/")} className="p-2 rounded-full hover:bg-white/30 dark:hover:bg-gray-800 text-gray-600 dark:text-gray-300">
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7" />
              </svg>
            </button>
            <h1 className="text-xl font-bold text-gray-900 dark:text-white">Super Admin</h1>
          </div>
          <div className="flex items-center gap-2">
            <UserAvatar name={user?.displayName || "Admin"} photoUrl={user?.photoUrl} size={32} />
            <span className="text-sm font-medium text-gray-900 dark:text-white">Super Admin</span>
          </div>
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto p-3 pb-8 max-w-7xl mx-auto space-y-6 sm:p-6">
        {/* Nav Tabs */}
        <div className="mb-6 flex gap-1 overflow-x-auto border-b border-white/20 dark:border-gray-700">
          {["users", "conversations", "reports", "logs", "announcements", "settings"].map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={cn(
                "mb-[-2px] shrink-0 border-b-2 px-3 py-2 text-xs font-medium transition-colors sm:px-4 sm:text-sm",
                activeTab === tab
                  ? "border-violet-500 text-violet-600 dark:text-violet-400"
                  : "border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300"
              )}
            >
              {tab.charAt(0).toUpperCase() + tab.slice(1)}
            </button>
          ))}
        </div>

        {/* Users Tab */}
        {activeTab === "users" && (
          <div className="GlassCard elevated p-6">
            <h2 className="text-lg font-bold text-gray-900 dark:text-white mb-4">User Management</h2>
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-white/20 dark:border-gray-700">
                    <th className="py-3 px-4 text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase">User</th>
                    <th className="py-3 px-4 text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase">Username</th>
                    <th className="py-3 px-4 text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase">Status</th>
                    <th className="py-3 px-4 text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {users.map((u) => (
                    <tr key={u.id} className="border-b border-white/10 dark:border-gray-800 hover:bg-white/30 dark:hover:bg-gray-800/30 transition-colors">
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-3">
                          <UserAvatar name={u.displayName || "User"} photoUrl={u.photoUrl} size={32} />
                          <div>
                            <div className="text-sm font-medium text-gray-900 dark:text-white">{u.displayName || "Unknown"}</div>
                            <div className="text-xs text-gray-500 dark:text-gray-400">{u.userId}</div>
                          </div>
                        </div>
                      </td>
                      <td className="py-3 px-4 text-sm text-gray-900 dark:text-white">
                        @{u.username || "n/a"}
                      </td>
                      <td className="py-3 px-4">
                        <span className={cn(
                          "px-2 py-1 rounded-full text-xs font-medium",
                          u.isSuspended ? "bg-rose-100 text-rose-700 dark:bg-rose-900/20 dark:text-rose-400" : "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/20 dark:text-emerald-400"
                        )}>
                          {u.isSuspended ? "Suspended" : "Active"}
                        </span>
                      </td>
                      <td className="py-3 px-4">
                        <button
                          onClick={() => suspendUser(u.userId, u.isSuspended)}
                          className="px-3 py-1.5 rounded-lg text-sm font-medium transition-colors text-white"
                          style={{ backgroundColor: u.isSuspended ? "#10B981" : "#EF4444" }}
                        >
                          {u.isSuspended ? "Restore" : "Suspend"}
                        </button>
                      </td>
                    </tr>
                  ))}
                  {users.length === 0 && (
                    <tr>
                      <td colSpan={4} className="py-8 text-center text-gray-500 dark:text-gray-400">No users found</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Conversations Tab */}
        {activeTab === "conversations" && (
          <div className="GlassCard elevated p-6">
            <h2 className="text-lg font-bold text-gray-900 dark:text-white mb-4">Conversation Management</h2>
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-white/20 dark:border-gray-700">
                    <th className="py-3 px-4 text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase">Title</th>
                    <th className="py-3 px-4 text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase">Type</th>
                    <th className="py-3 px-4 text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase">Members</th>
                    <th className="py-3 px-4 text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {conversations.map((c) => (
                    <tr key={c.id} className="border-b border-white/10 dark:border-gray-800 hover:bg-white/30 dark:hover:bg-gray-800/30 transition-colors">
                      <td className="py-3 px-4 text-sm font-medium text-gray-900 dark:text-white">{c.title || "Untitled"}</td>
                      <td className="py-3 px-4 text-sm text-gray-500 dark:text-gray-400">{c.type}</td>
                      <td className="py-3 px-4 text-sm text-gray-500 dark:text-gray-400">{c.memberUids?.length || 0}</td>
                      <td className="py-3 px-4">
                        <button
                          onClick={() => adminRepo.deleteConversationAsAdmin(c.id, c.title || "Untitled")}
                          className="px-3 py-1.5 rounded-lg text-sm font-medium text-rose-600 dark:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-900/20 transition-colors"
                        >
                          Remove
                        </button>
                      </td>
                    </tr>
                  ))}
                  {conversations.length === 0 && (
                    <tr>
                      <td colSpan={4} className="py-8 text-center text-gray-500 dark:text-gray-400">No conversations found</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Reports Tab */}
        {activeTab === "reports" && (
          <div className="GlassCard elevated p-6">
            <h2 className="text-lg font-bold text-gray-900 dark:text-white mb-4">Report Moderation</h2>
            <div className="space-y-3">
              {reports.map((r) => (
                <div key={r.id} className="p-4 rounded-xl border border-white/20 dark:border-gray-700 bg-white/40 dark:bg-gray-800/40">
                  <div className="flex justify-between mb-2">
                    <span className="text-sm font-medium text-gray-900 dark:text-white">{r.targetType} Report</span>
                    <span className={cn(
                      "px-2 py-0.5 rounded text-xs font-medium",
                      r.status === "open" ? "bg-amber-100 text-amber-700 dark:bg-amber-900/20 dark:text-amber-400" : "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/20 dark:text-emerald-400"
                    )}>
                      {r.status}
                    </span>
                  </div>
                  <p className="text-sm text-gray-600 dark:text-gray-300 mb-3">{r.reason}</p>
                  <p className="text-xs text-gray-400 dark:text-gray-500 mb-3">{r.details}</p>
                  <div className="flex gap-2">
                    <button
                      onClick={() => updateReportStatus(r.id, "reviewed")}
                      className="flex-1 py-2 rounded-lg text-sm font-medium text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 transition-colors"
                    >
                      Review
                    </button>
                    <button
                      onClick={() => updateReportStatus(r.id, "dismissed")}
                      className="flex-1 py-2 rounded-lg text-sm font-medium text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
                    >
                      Dismiss
                    </button>
                  </div>
                </div>
              ))}
              {reports.length === 0 && (
                <div className="text-center py-8 text-gray-500 dark:text-gray-400">No reports</div>
              )}
            </div>
          </div>
        )}

        {/* Logs Tab */}
        {activeTab === "logs" && (
          <div className="GlassCard elevated p-6">
            <h2 className="text-lg font-bold text-gray-900 dark:text-white mb-4">Audit Logs</h2>
            <div className="space-y-2">
              {logs.map((l) => (
                <div key={l.id} className="p-3 rounded-xl border border-white/10 dark:border-gray-800 bg-white/30 dark:bg-gray-800/30">
                  <div className="flex justify-between mb-1">
                    <span className="text-xs font-semibold text-gray-900 dark:text-white">{l.action}</span>
                    <span className="text-xs text-gray-500 dark:text-gray-400">{formatTime(l.createdAt)}</span>
                  </div>
                  <p className="text-sm text-gray-600 dark:text-gray-300">{l.details}</p>
                  <p className="text-xs text-gray-400 dark:text-gray-500 mt-1">By: {l.actorId}</p>
                </div>
              ))}
              {logs.length === 0 && (
                <div className="text-center py-8 text-gray-500 dark:text-gray-400">No audit logs</div>
              )}
            </div>
          </div>
        )}

        {/* Announcements Tab */}
        {activeTab === "announcements" && (
          <div className="GlassCard elevated p-6">
            <div className="flex justify-between items-center mb-6">
              <h2 className="text-lg font-bold text-gray-900 dark:text-white">Platform Announcements</h2>
              <button
                onClick={publishAnnouncement}
                className="px-4 py-2 rounded-lg bg-violet-600 text-white text-sm font-bold hover:bg-violet-700 transition-colors shadow-lg"
              >
                + Publish
              </button>
            </div>
            <div className="space-y-3">
              {announcements.map((a) => (
                <div key={a.id} className="p-4 rounded-xl border border-white/20 dark:border-gray-700 bg-white/40 dark:bg-gray-800/40">
                  <div className="flex justify-between mb-2">
                    <h3 className="text-sm font-medium text-gray-900 dark:text-white">{a.title}</h3>
                    <span className={cn(
                      "px-2 py-0.5 rounded text-xs font-medium",
                      a.priority === "urgent" ? "bg-rose-100 text-rose-700 dark:bg-rose-900/20 dark:text-rose-400" :
                      a.priority === "high" ? "bg-amber-100 text-amber-700 dark:bg-amber-900/20 dark:text-amber-400" :
                      "bg-blue-100 text-blue-700 dark:bg-blue-900/20 dark:text-blue-400"
                    )}>
                      {a.priority}
                    </span>
                  </div>
                  <p className="text-sm text-gray-600 dark:text-gray-300">{a.body}</p>
                  <p className="text-xs text-gray-400 dark:text-gray-500 mt-1">By: {a.createdBy} • {formatTime(a.createdAt)}</p>
                </div>
              ))}
              {announcements.length === 0 && (
                <div className="text-center py-8 text-gray-500 dark:text-gray-400">No announcements</div>
              )}
            </div>
          </div>
        )}

        {/* Settings Tab */}
        {activeTab === "settings" && (
          <div className="GlassCard elevated p-6">
            <h2 className="text-lg font-bold text-gray-900 dark:text-white mb-4">Platform Settings</h2>
            {settings && (
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Max Upload Size (MB)</label>
                  <input
                    type="number"
                    defaultValue={settings.maxUploadMb || 25}
                    className="w-full p-2 rounded-lg border border-white/30 dark:border-gray-700 bg-transparent text-gray-900 dark:text-white"
                  />
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sm text-gray-600 dark:text-gray-300">Allow New Registrations</span>
                  <button className={cn(
                    "relative w-10 h-5 rounded-full transition-colors",
                    settings.allowNewRegistrations ? "bg-violet-600" : "bg-gray-300"
                  )}>
                    <span className={cn(
                      "block w-3 h-3 rounded-full bg-white absolute transition-transform",
                      settings.allowNewRegistrations ? "translate-x-4" : "translate-x-1"
                    )} />
                  </button>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sm text-gray-600 dark:text-gray-300">Maintenance Mode</span>
                  <button className={cn(
                    "relative w-10 h-5 rounded-full transition-colors",
                    settings.maintenanceMode ? "bg-violet-600" : "bg-gray-300"
                  )}>
                    <span className={cn(
                      "block w-3 h-3 rounded-full bg-white absolute transition-transform",
                      settings.maintenanceMode ? "translate-x-4" : "translate-x-1"
                    )} />
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
