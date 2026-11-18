"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { CheckCircle2, CircleAlert, LoaderCircle, Shield } from "lucide-react";
import { AuthRepository, isValidUsernameFormat, normalizeUsername } from "@/services/authRepository";

const authRepo = new AuthRepository();

interface SignupOnboardingProps {
  displayName: string;
  email: string;
  photoUrl: string;
  onSignOut: () => void;
}

export function SignupOnboarding({ displayName: suggestedName, email, photoUrl, onSignOut }: SignupOnboardingProps) {
  const router = useRouter();
  const [displayName, setDisplayName] = useState(suggestedName || email.split("@")[0] || "PrivacyX User");
  const [usernameInput, setUsernameInput] = useState(() => normalizeUsername(suggestedName || email.split("@")[0]).slice(0, 30));
  const [bio, setBio] = useState("");
  const [availabilityState, setAvailabilityState] = useState<{
    username: string;
    available: boolean | null;
    error: string | null;
  } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const username = useMemo(() => normalizeUsername(usernameInput), [usernameInput]);
  const validUsername = isValidUsernameFormat(username);
  const matchedCheck = availabilityState?.username === username ? availabilityState : null;
  const available = matchedCheck ? matchedCheck.available : null;
  const checkError = matchedCheck?.error ?? null;
  const checking = validUsername && matchedCheck === null;

  useEffect(() => {
    let cancelled = false;
    if (!validUsername) return;
    const timer = window.setTimeout(async () => {
      const result = await authRepo.checkUsernameAvailability(username);
      if (!cancelled) {
        setAvailabilityState({
          username: result.username,
          available: result.available,
          error: result.error,
        });
      }
    }, 350);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [username, validUsername]);

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!validUsername || available !== true || submitting || !displayName.trim()) return;
    setSubmitting(true);
    setError("");
    const result = await authRepo.claimUsernameAndCreateProfile({
      displayName,
      username,
      bio,
      photoUrl,
      email,
    });
    if (!result.success) {
      setError(result.error || "We couldn't create your profile. Please try again.");
      setSubmitting(false);
      return;
    }
    router.replace("/");
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-gradient-to-br from-violet-500 via-pink-500 to-rose-500 p-4">
      <section className="my-auto w-full max-w-lg rounded-3xl border border-white/40 bg-white/95 p-6 shadow-2xl backdrop-blur-xl dark:border-white/15 dark:bg-gray-950/95 sm:p-8">
        <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-violet-600 to-pink-500 text-white shadow-lg">
          <Shield size={28} />
        </div>
        <h1 className="text-center text-2xl font-bold text-gray-900 dark:text-white">Claim Your Identity</h1>
        <p className="mt-2 text-center text-sm text-gray-600 dark:text-gray-300">
          Choose a unique @username so friends can discover and message you privately.
        </p>
        {email && (
          <p className="mt-2 text-center text-xs text-gray-500 dark:text-gray-400">
            Signed in as <span className="font-semibold text-gray-700 dark:text-gray-200">{email}</span>
          </p>
        )}

        <form onSubmit={submit} className="mt-6 space-y-4">
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-200">
            Display name
            <input
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value.slice(0, 60))}
              maxLength={60}
              required
              className="mt-1.5 w-full rounded-xl border border-gray-200 bg-white px-4 py-3 text-gray-900 outline-none transition focus:border-violet-500 focus:ring-2 focus:ring-violet-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-white"
            />
          </label>

          <label className="block text-sm font-medium text-gray-700 dark:text-gray-200">
            Unique username
            <div className="relative mt-1.5">
              <span className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400">@</span>
              <input
                value={usernameInput}
                onChange={(e) => {
                  setUsernameInput(e.target.value.slice(0, 30));
                  setError("");
                }}
                maxLength={30}
                autoComplete="username"
                required
                className="w-full rounded-xl border border-gray-200 bg-white py-3 pl-9 pr-11 text-gray-900 outline-none transition focus:border-violet-500 focus:ring-2 focus:ring-violet-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-white"
              />
              <span className="absolute right-4 top-1/2 -translate-y-1/2" aria-live="polite">
                {checking ? (
                  <LoaderCircle size={18} className="animate-spin text-gray-400" />
                ) : available === true ? (
                  <CheckCircle2 size={18} className="text-emerald-500" />
                ) : available === false || checkError || (!!usernameInput && !validUsername) ? (
                  <CircleAlert size={18} className="text-rose-500" />
                ) : null}
              </span>
            </div>
            <span className="mt-1 block text-xs font-normal text-gray-500 dark:text-gray-400">
              {!validUsername
                ? "Use 3–30 lowercase letters, numbers, or underscores."
                : checking
                  ? `Checking @${username}…`
                  : checkError
                    ? checkError
                    : available === true
                      ? `@${username} is available.`
                      : available === false
                        ? `@${username} is already taken.`
                        : `@${username}`}
            </span>
          </label>

          <label className="block text-sm font-medium text-gray-700 dark:text-gray-200">
            Bio <span className="font-normal text-gray-400">(optional)</span>
            <textarea
              value={bio}
              onChange={(e) => setBio(e.target.value.slice(0, 300))}
              maxLength={300}
              rows={3}
              className="mt-1.5 w-full resize-none rounded-xl border border-gray-200 bg-white px-4 py-3 text-gray-900 outline-none transition focus:border-violet-500 focus:ring-2 focus:ring-violet-500/20 dark:border-gray-700 dark:bg-gray-900 dark:text-white"
            />
          </label>

          {error && <p role="alert" className="rounded-xl bg-rose-500/10 p-3 text-sm text-rose-600 dark:text-rose-400">{error}</p>}

          <div className="flex gap-3 pt-1">
            <button
              type="button"
              onClick={onSignOut}
              disabled={submitting}
              className="min-h-12 flex-1 rounded-xl border border-gray-200 px-4 py-3 font-semibold text-gray-700 transition hover:bg-gray-100 disabled:opacity-50 dark:border-gray-700 dark:text-gray-200 dark:hover:bg-gray-800"
            >
              Switch account
            </button>
            <button
              type="submit"
              disabled={submitting || checking || available !== true || !validUsername || !displayName.trim()}
              className="min-h-12 flex-[1.4] rounded-xl bg-violet-600 px-4 py-3 font-semibold text-white shadow-lg transition hover:bg-violet-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {submitting ? (
                <span className="inline-flex items-center gap-2">
                  <LoaderCircle size={18} className="animate-spin" />
                  Creating profile…
                </span>
              ) : (
                "Enter PrivacyX"
              )}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
