"use client";

import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowLeft, Download, FileUp, ShieldCheck } from "lucide-react";
import { AuthRepository } from "@/services/authRepository";
import { useAuthStore, useBackupStore } from "@/stores/index";
import { formatTime } from "@/lib/utils";
import { GlassCard } from "@/components/ui";
import { cn } from "@/lib/utils";

const authRepo = new AuthRepository();
const SCHEDULES = ["off", "daily", "weekly", "monthly"] as const;

export default function BackupPage() {
  const router = useRouter();
  const user = useAuthStore((state) => state.user);
  const { schedule, setSchedule, lastBackupAtMs, lastBackupStatus, setLastBackup, isBackupBusy, setBackupBusy } = useBackupStore();
  const inputRef = useRef<HTMLInputElement>(null);
  const [error, setError] = useState("");

  const createBackup = async () => {
    if (!user || isBackupBusy) return;
    setBackupBusy(true);
    setError("");
    try {
      const result = await authRepo.exportUserDataJson();
      if (!result.success || !result.data) throw new Error(result.error || "Could not prepare the account backup.");
      const url = URL.createObjectURL(new Blob([result.data], { type: "application/json" }));
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `privacyx-backup-${new Date().toISOString().slice(0, 10)}.json`;
      anchor.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      setLastBackup(Date.now(), "Account backup downloaded");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Backup failed.");
    } finally {
      setBackupBusy(false);
    }
  };

  const restoreBackup = async (file?: File) => {
    if (!file || isBackupBusy) return;
    setBackupBusy(true);
    setError("");
    try {
      const result = await authRepo.restoreUserDataJson(await file.text());
      if (!result.success) throw new Error(result.error || "Backup restore failed.");
      setLastBackup(Date.now(), "Account backup restored");
      alert("Your profile, settings, and saved messages have been restored.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Restore failed. Check the backup file and try again.");
    } finally {
      setBackupBusy(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  };

  return <main className="min-h-dvh overflow-y-auto bg-[var(--px-bg)] px-3 pb-28 text-[var(--px-text-primary)] sm:px-4">
    <header className="sticky top-0 z-10 -mx-4 mb-7 border-b border-[var(--px-border)] bg-[var(--px-surface)] px-5 py-4 backdrop-blur-xl">
      <div className="mx-auto flex max-w-2xl items-center gap-3"><button aria-label="Back to chats" onClick={() => router.push("/")} className="rounded-full p-2 text-[var(--px-text-secondary)] hover:bg-violet-500/10"><ArrowLeft size={20} /></button><div><h1 className="text-lg font-bold">Storage &amp; Backup</h1><p className="text-xs text-[var(--px-text-muted)]">Protect your account data</p></div></div>
    </header>

    <div className="mx-auto max-w-2xl space-y-5">
      <GlassCard elevated><div className="flex gap-4 p-5"><div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-emerald-500/10 text-emerald-600"><ShieldCheck size={22} /></div><div><h2 className="font-semibold">What’s included</h2><p className="mt-1 text-sm leading-6 text-[var(--px-text-secondary)]">Your JSON backup includes your profile, account preferences, and saved messages. Media files and chat history are not included in this web backup.</p></div></div></GlassCard>

      <section><h2 className="mb-3 text-lg font-bold">Backup reminder preference</h2><p className="mb-3 text-sm text-[var(--px-text-secondary)]">Choose a reminder interval. The website does not run backups automatically in the background.</p><div className="space-y-2">{SCHEDULES.map((item) => <button key={item} aria-pressed={schedule === item} onClick={() => setSchedule(item)} className={cn("flex w-full items-center justify-between rounded-xl px-4 py-3 text-left transition-colors", schedule === item ? "bg-violet-600 text-white shadow-md" : "glass text-[var(--px-text-secondary)] hover:bg-violet-500/10")}><span className="font-medium capitalize">{item}</span><span className="text-xs">{item === "off" ? "No reminder" : item === "daily" ? "Every day" : item === "weekly" ? "Every week" : "Every month"}</span></button>)}</div></section>

      {error && <p role="alert" className="rounded-xl bg-rose-500/10 p-3 text-sm text-rose-600 dark:text-rose-400">{error}</p>}

      <div className="space-y-3">
        <button onClick={createBackup} disabled={isBackupBusy || !user} className="inline-flex w-full items-center justify-center gap-2 rounded-xl bg-violet-600 py-4 text-lg font-bold text-white shadow-lg transition hover:bg-violet-700 disabled:cursor-wait disabled:opacity-60"><Download size={19} />{isBackupBusy ? "Working…" : "Download account backup"}</button>
        <input ref={inputRef} type="file" accept="application/json,.json" className="hidden" onChange={(event) => void restoreBackup(event.target.files?.[0])} />
        <button onClick={() => inputRef.current?.click()} disabled={isBackupBusy || !user} className="inline-flex w-full items-center justify-center gap-2 rounded-xl border border-[var(--px-border)] bg-white/50 py-4 font-bold transition hover:bg-white/80 disabled:opacity-60 dark:bg-white/[0.04] dark:hover:bg-white/[0.08]"><FileUp size={19} />Restore from JSON backup</button>
      </div>

      {lastBackupAtMs > 0 && <GlassCard><div className="p-4"><h2 className="font-semibold">Recent activity</h2><p className="mt-1 text-sm text-[var(--px-text-secondary)]">{lastBackupStatus} · {formatTime(new Date(lastBackupAtMs))}</p></div></GlassCard>}
    </div>
  </main>;
}
