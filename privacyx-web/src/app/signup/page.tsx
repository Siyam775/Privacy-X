"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, Eye, EyeOff, LoaderCircle, LockKeyhole, Mail } from "lucide-react";
import { firebaseConfigured } from "@/lib/firebase";
import { AuthRepository, validateEmailAddress } from "@/services/authRepository";

const authRepo = new AuthRepository();

type AuthMode = "signup" | "signin";

function SignupCard() {
  const searchParams = useSearchParams();
  const initialMode: AuthMode = searchParams.get("mode") === "signin" ? "signin" : "signup";
  const initialNotice =
    searchParams.get("reason") === "expired" ? "Your previous session expired. Please sign in again." : "";

  const [mode, setMode] = useState<AuthMode>(initialMode);
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState(initialNotice);
  const [busy, setBusy] = useState(false);

  const switchMode = (nextMode: AuthMode) => {
    setMode(nextMode);
    setError("");
    setNotice("");
  };

  const startGoogleAuth = async () => {
    if (!firebaseConfigured) {
      setError("Authentication configuration is missing.");
      return;
    }
    setBusy(true);
    setError("");
    setNotice("");
    const result = await authRepo.signInWithGoogle();
    if (result.redirected) {
      setNotice("Redirecting to Google sign-in…");
      return;
    }
    if (result.error) {
      setError(result.error.message);
    }
    setBusy(false);
  };

  const handleEmailSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (busy) return;
    setError("");
    setNotice("");

    const trimmedEmail = email.trim();
    if (!validateEmailAddress(trimmedEmail)) {
      setError("Please enter a valid email address.");
      return;
    }

    if (mode === "signup") {
      const trimmedName = displayName.trim();
      if (trimmedName.length < 2) {
        setError("Display name must be at least 2 characters.");
        return;
      }
      if (password.length < 6) {
        setError("Password must be at least 6 characters long.");
        return;
      }
      if (password !== confirmPassword) {
        setError("Passwords do not match.");
        return;
      }

      setBusy(true);
      const result = await authRepo.registerWithEmail({
        displayName: trimmedName,
        email: trimmedEmail,
        password,
      });
      if (result.error) {
        setError(result.error);
      }
      setBusy(false);
      return;
    }

    if (!password) {
      setError("Please enter your password.");
      return;
    }

    setBusy(true);
    const result = await authRepo.signInWithEmail({
      email: trimmedEmail,
      password,
    });
    if (result.error) {
      setError(result.error);
    }
    setBusy(false);
  };

  const handleForgotPassword = async () => {
    if (busy) return;
    setError("");
    setNotice("");
    setBusy(true);
    const result = await authRepo.sendPasswordReset(email);
    setBusy(false);
    if (result.success) {
      setNotice(`Password reset email sent to ${email.trim()}. Check your inbox.`);
    } else {
      setError(result.error || "Could not send password reset email.");
    }
  };

  return (
    <main className="flex min-h-dvh items-center justify-center bg-[var(--px-bg)] px-4 py-10 text-[var(--px-text-primary)] sm:px-6">
      <section className="w-full max-w-lg rounded-3xl border border-[var(--px-border)] bg-white/75 p-6 shadow-xl backdrop-blur-xl dark:bg-white/[0.04] sm:p-9">
        <div className="text-center">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-violet-600 to-pink-500 text-white shadow-lg">
            <LockKeyhole size={25} />
          </div>
          <p className="mt-5 text-xs font-bold uppercase tracking-[.18em] text-violet-600 sm:text-sm">
            {mode === "signup" ? "Join PrivacyX" : "Welcome Back"}
          </p>
          <h1 className="mt-2 text-2xl font-bold tracking-tight sm:text-3xl">
            {mode === "signup" ? "Create your account" : "Sign in to PrivacyX"}
          </h1>
          <p className="mt-2 text-sm leading-6 text-[var(--px-text-secondary)]">
            {mode === "signup"
              ? "Continue with Google or email, then choose your unique PrivacyX @username."
              : "Sign in with Google or your email to access your private chats, calls, and communities."}
          </p>
        </div>

        <div className="mt-6 grid grid-cols-2 rounded-2xl border border-[var(--px-border)] bg-white/60 p-1 dark:bg-white/[0.03]" role="tablist" aria-label="Authentication mode">
          <button
            type="button"
            role="tab"
            aria-selected={mode === "signup"}
            onClick={() => switchMode("signup")}
            className={`min-h-11 rounded-xl px-3 py-2 text-sm font-semibold transition ${
              mode === "signup"
                ? "bg-violet-600 text-white shadow-sm"
                : "text-[var(--px-text-secondary)] hover:text-[var(--px-text-primary)]"
            }`}
          >
            Create account
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === "signin"}
            onClick={() => switchMode("signin")}
            className={`min-h-11 rounded-xl px-3 py-2 text-sm font-semibold transition ${
              mode === "signin"
                ? "bg-violet-600 text-white shadow-sm"
                : "text-[var(--px-text-secondary)] hover:text-[var(--px-text-primary)]"
            }`}
          >
            Sign in
          </button>
        </div>

        {notice && (
          <p role="status" className="mt-5 rounded-xl bg-emerald-500/10 p-3 text-left text-sm text-emerald-700 dark:text-emerald-300">
            {notice}
          </p>
        )}
        {error && (
          <p role="alert" className="mt-5 rounded-xl bg-rose-500/10 p-3 text-left text-sm text-rose-600 dark:text-rose-400">
            {error}
          </p>
        )}

        <button
          type="button"
          onClick={startGoogleAuth}
          disabled={busy || !firebaseConfigured}
          className="mt-6 flex min-h-12 w-full items-center justify-center gap-3 rounded-2xl border border-[var(--px-border)] bg-white px-5 py-3.5 text-sm font-bold text-gray-900 shadow-sm transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-white/10 dark:text-white dark:hover:bg-white/15"
        >
          {busy ? (
            <LoaderCircle size={18} className="animate-spin" />
          ) : (
            <svg className="h-5 w-5 shrink-0" viewBox="0 0 24 24" aria-hidden="true">
              <path fill="#EA4335" d="M12 10.2v3.9h5.4c-.2 1.3-1.5 3.9-5.4 3.9-3.3 0-6-2.7-6-6s2.7-6 6-6c1.9 0 3.1.8 3.8 1.5l2.6-2.5C16.8 3.5 14.6 2.5 12 2.5 6.8 2.5 2.5 6.8 2.5 12S6.8 21.5 12 21.5c6.9 0 9.1-4.8 9.1-7.3 0-.5-.1-.9-.1-1.3H12z" />
              <path fill="#34A853" d="M3.6 7.4l3.2 2.4C7.7 7.6 9.7 6 12 6c1.9 0 3.1.8 3.8 1.5l2.6-2.5C16.8 3.5 14.6 2.5 12 2.5 8.3 2.5 5.1 4.6 3.6 7.4z" />
              <path fill="#FBBC05" d="M12 21.5c2.6 0 4.8-.9 6.4-2.3l-3-2.5c-.8.6-1.9 1.1-3.4 1.1-2.6 0-4.8-1.7-5.6-4.1l-3.2 2.5c1.5 3.1 4.8 5.3 8.8 5.3z" />
              <path fill="#4285F4" d="M21.1 14.2c0-.5-.1-.9-.1-1.3H12v3.9h5.4c-.3 1.4-1.1 2.4-2 3.1l3 2.5c1.8-1.6 2.7-4 2.7-8.2z" />
            </svg>
          )}
          <span>{busy ? "Connecting to Google…" : "Continue with Google"}</span>
        </button>

        <div className="my-5 flex items-center gap-3 text-xs font-medium uppercase tracking-wider text-[var(--px-text-muted)]">
          <span className="h-px flex-1 bg-[var(--px-border)]" />
          <span>or continue with email</span>
          <span className="h-px flex-1 bg-[var(--px-border)]" />
        </div>

        <form onSubmit={handleEmailSubmit} className="space-y-3.5 text-left" noValidate>
          {mode === "signup" && (
            <label className="block text-sm font-medium text-[var(--px-text-primary)]">
              Display name
              <input
                type="text"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value.slice(0, 60))}
                placeholder="Alex Morgan"
                autoComplete="name"
                maxLength={60}
                required
                className="mt-1.5 w-full rounded-xl border border-[var(--px-border)] bg-white/80 px-4 py-3 text-sm text-[var(--px-text-primary)] outline-none transition focus:border-violet-500 focus:ring-2 focus:ring-violet-500/20 dark:bg-gray-900/70"
              />
            </label>
          )}

          <label className="block text-sm font-medium text-[var(--px-text-primary)]">
            Email address
            <div className="relative mt-1.5">
              <Mail size={17} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--px-text-muted)]" />
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
                autoComplete="email"
                required
                className="w-full rounded-xl border border-[var(--px-border)] bg-white/80 py-3 pl-10 pr-4 text-sm text-[var(--px-text-primary)] outline-none transition focus:border-violet-500 focus:ring-2 focus:ring-violet-500/20 dark:bg-gray-900/70"
              />
            </div>
          </label>

          <label className="block text-sm font-medium text-[var(--px-text-primary)]">
            Password
            <div className="relative mt-1.5">
              <input
                type={showPassword ? "text" : "password"}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder={mode === "signup" ? "At least 6 characters" : "Enter your password"}
                autoComplete={mode === "signup" ? "new-password" : "current-password"}
                minLength={mode === "signup" ? 6 : 1}
                required
                className="w-full rounded-xl border border-[var(--px-border)] bg-white/80 py-3 pl-4 pr-11 text-sm text-[var(--px-text-primary)] outline-none transition focus:border-violet-500 focus:ring-2 focus:ring-violet-500/20 dark:bg-gray-900/70"
              />
              <button
                type="button"
                aria-label={showPassword ? "Hide password" : "Show password"}
                onClick={() => setShowPassword((prev) => !prev)}
                className="absolute right-2 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-lg text-[var(--px-text-muted)] hover:text-[var(--px-text-primary)]"
              >
                {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
              </button>
            </div>
          </label>

          {mode === "signup" && (
            <label className="block text-sm font-medium text-[var(--px-text-primary)]">
              Confirm password
              <input
                type={showPassword ? "text" : "password"}
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="Re-enter your password"
                autoComplete="new-password"
                minLength={6}
                required
                className="mt-1.5 w-full rounded-xl border border-[var(--px-border)] bg-white/80 px-4 py-3 text-sm text-[var(--px-text-primary)] outline-none transition focus:border-violet-500 focus:ring-2 focus:ring-violet-500/20 dark:bg-gray-900/70"
              />
            </label>
          )}

          {mode === "signin" && (
            <div className="flex justify-end">
              <button
                type="button"
                onClick={handleForgotPassword}
                disabled={busy}
                className="text-xs font-semibold text-violet-600 hover:text-violet-700 disabled:opacity-50"
              >
                Forgot password?
              </button>
            </div>
          )}

          <button
            type="submit"
            disabled={busy || !firebaseConfigured}
            className="mt-2 flex min-h-12 w-full items-center justify-center gap-2 rounded-2xl bg-violet-600 px-6 py-3.5 text-sm font-bold text-white shadow-lg transition hover:bg-violet-700 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {busy ? (
              <>
                <LoaderCircle size={18} className="animate-spin" />
                <span>{mode === "signup" ? "Creating account…" : "Signing in…"}</span>
              </>
            ) : mode === "signup" ? (
              "Create Account"
            ) : (
              "Sign In"
            )}
          </button>
        </form>

        <div className="mt-5 rounded-2xl border border-[var(--px-border)] bg-white/60 p-4 text-left text-xs leading-5 text-[var(--px-text-secondary)] dark:bg-white/[0.03] sm:text-sm">
          <p className="font-semibold text-[var(--px-text-primary)]">Your account includes</p>
          <ul className="mt-1.5 list-inside list-disc space-y-1">
            <li>A unique @username and customizable profile</li>
            <li>Private chats, groups, calls, stories, and communities</li>
            <li>Privacy and appearance settings synced with your account</li>
          </ul>
        </div>

        <div className="mt-6 text-center">
          <Link href="/" className="inline-flex items-center gap-2 text-sm font-semibold text-violet-600 hover:text-violet-700">
            <ArrowLeft size={16} /> Back to explore
          </Link>
        </div>
      </section>
    </main>
  );
}

export default function SignupPage() {
  return (
    <Suspense
      fallback={
        <main className="flex min-h-dvh items-center justify-center bg-[var(--px-bg)]">
          <LoaderCircle className="animate-spin text-violet-600" size={32} aria-label="Loading" />
        </main>
      }
    >
      <SignupCard />
    </Suspense>
  );
}
