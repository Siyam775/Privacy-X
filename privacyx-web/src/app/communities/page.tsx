"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowLeft, Plus, UsersRound } from "lucide-react";
import { MessagingRepository } from "@/services/messagingRepository";
import { chatHref } from "@/lib/utils";
import { useAuthStore } from "@/stores/index";
import { GlassCard, SearchBar, UserAvatar } from "@/components/ui";
import type { Conversation } from "@/types/index";

const messagingRepo = new MessagingRepository();

export default function CommunitiesPage() {
  const router = useRouter();
  const user = useAuthStore((state) => state.user);
  const [spaces, setSpaces] = useState<Conversation[]>([]);
  const [query, setQuery] = useState("");
  const [createOpen, setCreateOpen] = useState(false);
  const [type, setType] = useState<"community" | "group" | "channel">("community");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [isPublic, setIsPublic] = useState(true);
  const [busy, setBusy] = useState(false);
  const [busyId, setBusyId] = useState("");
  const [notice, setNotice] = useState("");

  useEffect(() => messagingRepo.observePublicDiscoveryConversations()(setSpaces), []);

  const filtered = useMemo(() => spaces.filter((space) => space.type !== "direct" && (`${space.title} ${space.description}`).toLowerCase().includes(query.toLowerCase())), [spaces, query]);

  const createSpace = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!title.trim() || busy) return;
    setBusy(true);
    setNotice("");
    const result = await messagingRepo.createGroupOrCommunityOrChannel({ type, title, description, isPublic });
    setBusy(false);
    if (result.success && result.conversationId) router.push(chatHref(result.conversationId));
    else setNotice(result.error || "Could not create this space.");
  };

  const joinSpace = async (space: Conversation) => {
    if (!user) return;
    if (space.memberUids.includes(user.userId)) { router.push(chatHref(space.id)); return; }
    setBusyId(space.id);
    const result = await messagingRepo.joinPublicConversation(space.id);
    setBusyId("");
    if (result.success) router.push(chatHref(space.id));
    else setNotice(result.error || "Could not join this space.");
  };

  return <main className="min-h-dvh bg-[var(--px-bg)] px-3 pb-28 pt-4 text-[var(--px-text-primary)] sm:px-4 sm:pt-6">
    <div className="mx-auto max-w-3xl">
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div><button onClick={() => router.push("/")} className="mb-3 inline-flex items-center gap-2 text-sm text-[var(--px-text-secondary)] hover:text-violet-600"><ArrowLeft size={17} /> Chats</button><h1 className="text-2xl font-bold">Spaces & communities</h1><p className="mt-1 text-sm text-[var(--px-text-secondary)]">Find a public group or create a space for your people.</p></div>
        <button onClick={() => setCreateOpen(true)} className="inline-flex min-h-11 items-center justify-center gap-2 self-start rounded-full bg-violet-600 px-4 py-2.5 text-sm font-semibold text-white shadow-md hover:bg-violet-700"><Plus size={18} /><span>Create space</span></button>
      </div>
      <SearchBar query={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search communities and groups" />
      {notice && <p role="alert" className="mt-4 rounded-xl bg-rose-500/10 p-3 text-sm text-rose-600">{notice}</p>}
      <div className="mt-5 grid gap-3 sm:grid-cols-2">
        {filtered.map((space) => <GlassCard key={space.id} className="flex items-center gap-3 p-4">
          <UserAvatar name={space.title} photoUrl={space.photoUrl} size={48} />
          <div className="min-w-0 flex-1"><p className="truncate font-semibold">{space.title}</p><p className="mt-0.5 line-clamp-2 text-sm text-[var(--px-text-secondary)]">{space.description || `${space.type} · ${space.memberUids.length} members`}</p></div>
          <button onClick={() => joinSpace(space)} disabled={!!busyId} className="rounded-full border border-violet-500/30 px-4 py-2 text-sm font-semibold text-violet-700 hover:bg-violet-500/10 disabled:opacity-50 dark:text-violet-300">{busyId === space.id ? "Joining…" : space.memberUids.includes(user?.userId || "") ? "Open" : "Join"}</button>
        </GlassCard>)}
        {filtered.length === 0 && <div className="py-16 text-center text-[var(--px-text-secondary)]"><UsersRound className="mx-auto mb-3 opacity-50" size={30} /><p>{query ? "No matching spaces found." : "No public spaces yet. Create the first one."}</p></div>}
      </div>
    </div>

    {createOpen && <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" onMouseDown={(event) => { if (event.target === event.currentTarget) setCreateOpen(false); }}>
      <form onSubmit={createSpace} className="max-h-[calc(100dvh-2rem)] w-full max-w-md overflow-y-auto rounded-3xl border border-white/30 bg-white p-4 shadow-2xl dark:bg-gray-900 sm:p-6">
        <h2 className="text-xl font-bold">Create a space</h2>
        <p className="mt-1 text-sm text-[var(--px-text-secondary)]">Start a group, channel, or community.</p>
        <label className="mt-5 block text-sm font-medium">Type<select value={type} onChange={(event) => setType(event.target.value as typeof type)} className="mt-1.5 w-full rounded-xl border border-gray-200 bg-transparent p-3 dark:border-gray-700">{["community", "group", "channel"].map((item) => <option key={item} value={item}>{item[0].toUpperCase() + item.slice(1)}</option>)}</select></label>
        <label className="mt-4 block text-sm font-medium">Name<input required maxLength={60} value={title} onChange={(event) => setTitle(event.target.value)} className="mt-1.5 w-full rounded-xl border border-gray-200 bg-transparent p-3 dark:border-gray-700" /></label>
        <label className="mt-4 block text-sm font-medium">Description<textarea maxLength={500} rows={3} value={description} onChange={(event) => setDescription(event.target.value)} className="mt-1.5 w-full resize-none rounded-xl border border-gray-200 bg-transparent p-3 dark:border-gray-700" /></label>
        <label className="mt-4 flex items-center gap-3 text-sm"><input type="checkbox" checked={isPublic} onChange={(event) => setIsPublic(event.target.checked)} /> Make this space public so others can find it</label>
        {notice && <p role="alert" className="mt-4 text-sm text-rose-600">{notice}</p>}
        <div className="mt-6 flex gap-3"><button type="button" onClick={() => setCreateOpen(false)} className="flex-1 rounded-xl border border-gray-200 py-3 dark:border-gray-700">Cancel</button><button disabled={busy} type="submit" className="flex-1 rounded-xl bg-violet-600 py-3 font-semibold text-white disabled:opacity-60">{busy ? "Creating…" : "Create"}</button></div>
      </form>
    </div>}
  </main>;
}
