"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowLeft, Bookmark, Trash2 } from "lucide-react";
import { SocialRepository } from "@/services/socialRepository";
import { useAuthStore } from "@/stores/index";
import { GlassCard } from "@/components/ui";
import { formatTime } from "@/lib/utils";
import type { SavedMessage } from "@/types/index";

const socialRepo = new SocialRepository();

export default function SavedPage() {
  const router = useRouter();
  const user = useAuthStore((state) => state.user);
  const [items, setItems] = useState<SavedMessage[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!user) return;
    return socialRepo.observeSavedMessages(user.userId)((saved) => setItems(saved));
  }, [user]);

  const remove = async (id: string) => {
    const result = await socialRepo.deleteSavedMessage(id);
    if (!result.success) setError(result.error || "Could not remove saved message.");
  };

  return <main className="min-h-dvh bg-[var(--px-bg)] px-3 pb-28 pt-4 text-[var(--px-text-primary)] sm:px-4 sm:pt-6"><div className="mx-auto max-w-2xl">
    <button onClick={() => router.push("/")} className="mb-5 inline-flex items-center gap-2 text-sm font-medium text-[var(--px-text-secondary)] hover:text-violet-600"><ArrowLeft size={18} /> Chats</button>
    <div className="flex items-center gap-3"><span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-violet-500/10 text-violet-600"><Bookmark size={21} /></span><div><h1 className="text-2xl font-bold">Saved messages</h1><p className="text-sm text-[var(--px-text-secondary)]">Keep messages and links close at hand.</p></div></div>
    {error && <p role="alert" className="mt-4 rounded-xl bg-rose-500/10 p-3 text-sm text-rose-600">{error}</p>}
    <div className="mt-6 space-y-3">{items.map((item) => <GlassCard key={item.id} className="flex gap-3 p-4"><div className="min-w-0 flex-1"><p className="whitespace-pre-wrap break-words">{item.content}</p>{item.sourceSenderName && <p className="mt-2 text-xs text-[var(--px-text-muted)]">From {item.sourceSenderName} · {formatTime(item.createdAt)}</p>}</div><button aria-label="Remove saved message" title="Remove saved message" onClick={() => remove(item.id)} className="self-start rounded-lg p-2 text-[var(--px-text-muted)] hover:bg-rose-500/10 hover:text-rose-600"><Trash2 size={17} /></button></GlassCard>)}
      {items.length === 0 && <div className="py-16 text-center text-[var(--px-text-secondary)]"><Bookmark className="mx-auto mb-3 opacity-50" size={30} /><p>No saved messages yet.</p><p className="mt-1 text-sm">Save a message from any conversation to find it here.</p></div>}
    </div>
  </div></main>;
}
