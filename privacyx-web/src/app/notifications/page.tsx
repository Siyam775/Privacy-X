"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowLeft, Bell, Check } from "lucide-react";
import { SocialRepository } from "@/services/socialRepository";
import { useAuthStore } from "@/stores/index";
import { formatTime, chatHref } from "@/lib/utils";
import type { NotificationItem } from "@/types/index";

const socialRepo = new SocialRepository();

export default function NotificationsPage() {
  const router = useRouter();
  const user = useAuthStore((state) => state.user);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!user) return;
    return socialRepo.observeNotifications(user.userId)((items) => setNotifications(items));
  }, [user]);

  const openNotification = async (item: NotificationItem) => {
    await socialRepo.markNotificationRead(item.id);
    const target = item.targetId;
    if (target?.startsWith("/chat/")) {
      const legacyChatId = target.slice("/chat/".length).split(/[?#]/)[0];
      router.push(chatHref(decodeURIComponent(legacyChatId)));
    }
    else if (target?.startsWith("/")) router.push(target);
    else if (target) router.push(chatHref(target));
    else if (item.type === "post" || item.type === "follow") router.push("/social");
    else setError("This notification has no destination yet.");
  };

  const markRead = async (item: NotificationItem) => {
    setError("");
    await socialRepo.markNotificationRead(item.id);
  };

  return <main className="min-h-dvh bg-[var(--px-bg)] px-3 pb-28 text-[var(--px-text-primary)] sm:px-4">
    <header className="sticky top-0 z-10 -mx-4 mb-6 border-b border-[var(--px-border)] bg-[var(--px-surface)] px-5 py-4 backdrop-blur-xl">
      <div className="mx-auto flex max-w-2xl items-center gap-3">
        <button aria-label="Back to chats" onClick={() => router.push("/")} className="rounded-full p-2 hover:bg-violet-500/10"><ArrowLeft size={20} /></button>
        <div><h1 className="text-lg font-bold">Notifications</h1><p className="text-xs text-[var(--px-text-muted)]">Your latest activity</p></div>
      </div>
    </header>
    <section className="mx-auto max-w-2xl space-y-3">
      {error && <p role="alert" className="rounded-xl bg-rose-500/10 p-3 text-sm text-rose-600">{error}</p>}
      {notifications.length === 0 ? <div className="py-20 text-center text-[var(--px-text-secondary)]"><Bell className="mx-auto mb-3 opacity-50" /><p>No notifications yet.</p></div> : notifications.map((item) => <article key={item.id} className={`flex items-start gap-3 rounded-2xl border border-[var(--px-border)] p-4 ${item.isRead ? "bg-[var(--px-surface)]" : "bg-violet-500/10"}`}>
        <button onClick={() => void openNotification(item)} className="min-w-0 flex-1 text-left">
          <p className="font-semibold">{item.title || "Activity"}</p>
          {item.body && <p className="mt-1 text-sm text-[var(--px-text-secondary)]">{item.body}</p>}
          <time className="mt-2 block text-xs text-[var(--px-text-muted)]">{formatTime(item.createdAt)}</time>
        </button>
        {!item.isRead && <button aria-label="Mark as read" title="Mark as read" onClick={() => void markRead(item)} className="rounded-lg p-2 text-violet-600 hover:bg-violet-500/10"><Check size={18} /></button>}
      </article>)}
    </section>
  </main>;
}
