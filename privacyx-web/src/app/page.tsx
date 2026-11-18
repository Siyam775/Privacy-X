"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Archive, Bell, Sparkles, ArchiveRestore } from "lucide-react";
import { MessagingRepository } from "@/services/messagingRepository";
import { useAuthStore, useChatStore } from "@/stores/index";
import { SearchBar, UserAvatar, GlassCard, EmptyState } from "@/components/ui";
import { formatTime, cn, chatHref } from "@/lib/utils";
import type { Conversation } from "@/types/index";
import { PublicLanding } from "@/components/PublicLanding";

const messagingRepo = new MessagingRepository();
const FILTERS = ["Chats", "Groups", "Channels", "Archived", "Unread"] as const;

export default function ChatsPage() {
  const router = useRouter();
  const { user } = useAuthStore();
  const { setConversations, conversations } = useChatStore();
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<(typeof FILTERS)[number]>("Chats");
  const [actionError, setActionError] = useState("");

  useEffect(() => {
    if (!user) return;
    return messagingRepo.observeMyConversations(user.userId)((convs) => setConversations(convs));
  }, [user, setConversations]);

  const filtered = useMemo(() => conversations.filter((conv) => {
    const matchesQuery = !query || `${conv.title} ${conv.lastMessageText}`.toLowerCase().includes(query.toLowerCase());
    const matchesFilter = filter === "Archived" ? !!conv.isArchived : !conv.isArchived && (filter === "Chats" ||
      (filter === "Groups" && (conv.type === "group" || conv.type === "community")) ||
      (filter === "Channels" && conv.type === "channel") ||
      (filter === "Unread" && !!conv.lastMessageSenderId && conv.lastMessageSenderId !== user?.userId));
    return matchesQuery && matchesFilter;
  }), [conversations, filter, query, user?.userId]);
  const groups = filtered.filter((conv) => conv.type !== "direct");
  const directs = filtered.filter((conv) => conv.type === "direct");

  if (!user) return <PublicLanding />;

  return (
    <main className="relative min-h-dvh overflow-hidden bg-orbs bg-[var(--px-bg)] text-[var(--px-text-primary)] theme-transition">
      <div className="pointer-events-none absolute inset-0 bg-gradient-to-b from-[var(--px-bg)] to-[var(--px-bg-secondary)]" />
      <div className="relative z-10 mx-auto w-full max-w-2xl px-4 pb-28 pt-5">
        <GlassCard elevated onClick={() => router.push("/profile")} className="mb-3 cursor-pointer">
          <div className="flex items-center gap-3 p-3">
            <UserAvatar name={user?.displayName || "User"} photoUrl={user?.photoUrl} size={46} isOnline />
            <div className="min-w-0 flex-1">
              <h1 className="truncate font-semibold">{user?.displayName || "Chats"}</h1>
              <p className="truncate text-sm text-[var(--px-text-muted)]">{user?.username ? `@${user.username}` : "Your conversations"}</p>
            </div>
            <button aria-label="AI Assistant" className="rounded-xl p-2 text-violet-600 hover:bg-violet-500/10" onClick={(e) => { e.stopPropagation(); router.push("/ai"); }}><Sparkles size={20} /></button>
            <button aria-label="Notifications" onClick={(e) => { e.stopPropagation(); router.push("/notifications"); }} className="rounded-xl p-2 text-[var(--px-text-secondary)] hover:bg-violet-500/10"><Bell size={20} /></button>
          </div>
        </GlassCard>

        <SearchBar query={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search your conversations..." className="mb-3" />
        {actionError && <p role="alert" className="mb-3 rounded-xl bg-rose-500/10 p-3 text-sm text-rose-600">{actionError}</p>}
        <div className="mb-4 flex gap-2 overflow-x-auto pb-1" role="tablist" aria-label="Chat filters">
          {FILTERS.map((item) => <button key={item} role="tab" aria-selected={filter === item} onClick={() => setFilter(item)} className={cn("shrink-0 rounded-full px-[18px] py-[9px] text-sm font-medium transition-colors", filter === item ? "bg-violet-600 text-white" : "glass text-[var(--px-text-secondary)] hover:bg-violet-500/10")}>{item}</button>)}
        </div>

        {groups.length > 0 && <ConversationSection title="Groups & Community" conversations={groups} userId={user?.userId} onOpen={(id) => router.push(chatHref(id))} onArchive={async (conv) => { const result = await messagingRepo.setConversationArchived(conv.id, !conv.isArchived); setActionError(result.error || ""); }} />}
        {directs.length > 0 && <ConversationSection title="Direct Chats" conversations={directs} userId={user?.userId} onOpen={(id) => router.push(chatHref(id))} onArchive={async (conv) => { const result = await messagingRepo.setConversationArchived(conv.id, !conv.isArchived); setActionError(result.error || ""); }} />}
        {filtered.length === 0 && <>
          <EmptyState icon="💬" title={query ? "No matching conversations" : "No conversations yet"} subtitle={query ? "Try another name or message." : "Tap + below or search by @username to start an encrypted chat, group, or channel."} />
          {!query && <>
            <p className="mb-2 mt-5 px-1 text-xs font-semibold uppercase tracking-wider text-[var(--px-text-muted)]">Discover</p>
            <GlassCard className="cursor-pointer" onClick={() => router.push("/communities")}>
              <div className="flex items-center gap-3 p-3"><span className="flex h-11 w-11 items-center justify-center rounded-full bg-violet-600 text-2xl text-white">＋</span><div><p className="font-medium">Create a group or channel</p><p className="text-sm text-[var(--px-text-secondary)]">You become the admin with topics &amp; slow mode</p></div></div>
            </GlassCard>
          </>}
        </>}
      </div>
    </main>
  );
}

function ConversationSection({ conversations, title, userId, onOpen, onArchive }: {
  conversations: Conversation[];
  title: string;
  userId?: string;
  onOpen: (id: string) => void;
  onArchive: (conversation: Conversation) => void;
}) {
  return <section className="mb-5">
    <p className="mb-2 px-1 text-xs font-semibold uppercase tracking-wider text-[var(--px-text-muted)]">{title}</p>
    <div className="space-y-2">{conversations.map((conv) => <GlassCard key={conv.id} className="cursor-pointer transition hover:border-violet-400/60" onClick={() => onOpen(conv.id)}>
      <div className="flex items-center gap-3 p-3">
        <UserAvatar name={conv.title} photoUrl={conv.photoUrl || undefined} size={50} isOnline={conv.memberUids.includes(userId || "")} />
        <div className="min-w-0 flex-1">
          <div className="flex items-center justify-between gap-2"><h3 className="truncate font-semibold">{conv.title}</h3><span className="shrink-0 text-xs text-[var(--px-text-muted)]">{formatTime(conv.lastMessageAt)}</span></div>
          <p className="mt-0.5 truncate text-sm text-[var(--px-text-secondary)]">{conv.lastMessageText || "Say hello!"}</p>
        </div>
        <button aria-label={conv.isArchived ? "Restore conversation" : "Archive conversation"} title={conv.isArchived ? "Restore" : "Archive"} onClick={(event) => { event.stopPropagation(); void onArchive(conv); }} className="rounded-lg p-2 text-[var(--px-text-muted)] hover:bg-violet-500/10 hover:text-violet-600">
          {conv.isArchived ? <ArchiveRestore size={18} /> : <Archive size={18} />}
        </button>
      </div>
    </GlassCard>)}</div>
  </section>;
}
