"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowLeft, Search } from "lucide-react";
import { AuthRepository } from "@/services/authRepository";
import { chatHref } from "@/lib/utils";
import { MessagingRepository } from "@/services/messagingRepository";
import { SearchBar, UserAvatar, GlassCard } from "@/components/ui";
import type { UsernameReservation } from "@/types/index";

const authRepo = new AuthRepository();
const messagingRepo = new MessagingRepository();

export default function NewChatPage() {
  const router = useRouter();
  const [query, setQuery] = useState("");
  const [people, setPeople] = useState<UsernameReservation[]>([]);
  const [busyId, setBusyId] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    const timer = window.setTimeout(async () => {
      const results = await authRepo.searchUsers(query);
      if (!cancelled) setPeople(results);
    }, 250);
    return () => { cancelled = true; window.clearTimeout(timer); };
  }, [query]);

  const visiblePeople = useMemo(() => {
    const needle = query.trim().replace(/^@/, "").toLowerCase();
    if (!needle) return people.slice(0, 12);
    return people.filter((person) => person.username.toLowerCase().includes(needle) || person.displayName.toLowerCase().includes(needle)).slice(0, 20);
  }, [people, query]);

  const startChat = async (person: UsernameReservation) => {
    setBusyId(person.userId);
    setError("");
    const result = await messagingRepo.createOrGetDirectConversation({ otherUserId: person.userId, otherDisplayName: person.displayName, otherPhotoUrl: person.photoUrl });
    if (result.success && result.conversationId) router.push(chatHref(result.conversationId));
    else { setError(result.error || "Could not start this conversation."); setBusyId(""); }
  };

  return <main className="min-h-dvh bg-[var(--px-bg)] px-3 pb-28 pt-4 text-[var(--px-text-primary)] sm:px-4 sm:pt-6">
    <div className="mx-auto max-w-2xl">
      <button onClick={() => router.push("/")} className="mb-5 inline-flex items-center gap-2 text-sm font-medium text-[var(--px-text-secondary)] hover:text-violet-600"><ArrowLeft size={18} /> Back to chats</button>
      <h1 className="text-2xl font-bold">Start a conversation</h1>
      <p className="mt-1 text-sm text-[var(--px-text-secondary)]">Find someone by display name or @username.</p>
      <SearchBar query={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search @username or name" className="mt-5" />
      {error && <p role="alert" className="mt-4 rounded-xl bg-rose-500/10 p-3 text-sm text-rose-600">{error}</p>}
      <div className="mt-5 space-y-2">
        {visiblePeople.map((person) => <GlassCard key={person.userId} className="flex items-center gap-3 p-3">
          <UserAvatar name={person.displayName} photoUrl={person.photoUrl} size={44} />
          <div className="min-w-0 flex-1"><p className="truncate font-semibold">{person.displayName}</p><p className="text-sm text-[var(--px-text-secondary)]">@{person.username}</p></div>
          <button onClick={() => startChat(person)} disabled={!!busyId} className="rounded-full bg-violet-600 px-4 py-2 text-sm font-semibold text-white hover:bg-violet-700 disabled:opacity-60">{busyId === person.userId ? "Opening…" : "Message"}</button>
        </GlassCard>)}
        {visiblePeople.length === 0 && <div className="py-16 text-center text-[var(--px-text-secondary)]"><Search className="mx-auto mb-3 opacity-50" /><p>{query ? "No people found. Check the username and try again." : "No people to show yet."}</p></div>}
      </div>
    </div>
  </main>;
}
