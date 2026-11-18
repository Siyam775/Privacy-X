"use client";

import { useState } from "react";
import Link from "next/link";
import { ArrowLeft, LockKeyhole } from "lucide-react";
import { auth, firebaseConfigured, GoogleAuthProvider, signInWithPopup } from "@/lib/firebase";

export default function SignupPage() {
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const startSignup = async () => {
    if (!firebaseConfigured || !auth) {
      setError("Sign up is not configured yet. Add the Firebase web app settings to the deployment environment and rebuild.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      await signInWithPopup(auth, new GoogleAuthProvider());
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Google signup failed. Please try again.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="flex min-h-screen items-center justify-center bg-[var(--px-bg)] px-5 py-12 text-[var(--px-text-primary)]">
      <section className="w-full max-w-lg rounded-3xl border border-[var(--px-border)] bg-white/65 p-8 text-center shadow-xl backdrop-blur-xl dark:bg-white/[0.04] sm:p-10">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-violet-600 to-pink-500 text-white shadow-lg"><LockKeyhole size={25} /></div>
        <p className="mt-6 text-sm font-bold uppercase tracking-[.18em] text-violet-600">Join PrivacyX</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight">Create your account</h1>
        <p className="mt-3 leading-7 text-[var(--px-text-secondary)]">Continue with Google, then choose your PrivacyX username. You can look around the app before signing up.</p>
        <div className="mt-7 rounded-2xl border border-[var(--px-border)] bg-white/60 p-4 text-left text-sm leading-6 text-[var(--px-text-secondary)] dark:bg-white/[0.03]">
          <p className="font-semibold text-[var(--px-text-primary)]">Your signup includes</p>
          <ul className="mt-2 list-inside list-disc space-y-1">
            <li>A unique @username and profile</li>
            <li>Access to chats, calls, stories, and communities</li>
            <li>Privacy and appearance settings you can change later</li>
          </ul>
        </div>
        {error && <p role="alert" className="mt-5 rounded-xl bg-rose-500/10 p-3 text-left text-sm text-rose-600 dark:text-rose-400">{error}</p>}
        <button onClick={startSignup} disabled={busy || !firebaseConfigured} className="mt-7 w-full rounded-2xl bg-violet-600 px-6 py-4 font-bold text-white shadow-lg transition hover:bg-violet-700 disabled:cursor-not-allowed disabled:opacity-60">{!firebaseConfigured ? "Sign up setup pending" : busy ? "Connecting to Google…" : "Continue with Google"}</button>
        <p className="mt-3 text-xs text-[var(--px-text-muted)]">New to PrivacyX? Your profile setup comes right after Google.</p>
        <Link href="/" className="mt-7 inline-flex items-center gap-2 text-sm font-semibold text-violet-600 hover:text-violet-700"><ArrowLeft size={16} /> Back to explore</Link>
      </section>
    </main>
  );
}
