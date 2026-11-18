"use client";

import { useEffect, useRef, useState } from "react";
import { auth, firebaseConfigured, onAuthStateChanged } from "@/lib/firebase";
import { AuthRepository } from "@/services/authRepository";
import { ThemeProvider, useTheme } from "@/contexts/ThemeContext";
import { useAuthStore, useChatStore, useThemeStore } from "@/stores/index";
import { RadialFabMenu } from "@/components/RadialFabMenu";
import { FloatingNavBar } from "@/components/FloatingNavBar";
import { SignupOnboarding } from "@/components/SignupOnboarding";
import { LoaderCircle, ShieldAlert } from "lucide-react";
import { useRouter, usePathname } from "next/navigation";
import type { User as FirebaseUser } from "firebase/auth";

const authRepo = new AuthRepository();
const PUBLIC_ROUTES = new Set(["/", "/signup"]);

export function AppShell({ children }: { children: React.ReactNode }) {
  const [radialMenuOpen, setRadialMenuOpen] = useState(false);
  const [firebaseUser, setFirebaseUser] = useState<FirebaseUser | null>(null);
  const [authResolved, setAuthResolved] = useState(false);
  const [profileLoaded, setProfileLoaded] = useState(false);
  const [authBannerError, setAuthBannerError] = useState("");
  const recordedSessionUidRef = useRef<string | null>(null);
  const pathname = usePathname();
  const routePath = pathname.replace(/\/+$/, "") || "/";
  const router = useRouter();

  const { user, setUser, setPrivateAccount, setSettings, isSuperAdmin } = useAuthStore();
  const { themeId, appearanceMode, accentColorHex, reducedMotion } = useThemeStore();

  const { setTheme: setThemeContext, setAppearance, setAccentColor, setReducedMotion } = useTheme();

  // Apply theme CSS vars synchronously on mount so no unstyled flash
  useEffect(() => {
    const html = document.documentElement;
    html.setAttribute("data-theme", themeId);
    html.setAttribute("data-accent", accentColorHex);

    if (
      appearanceMode === "dark" ||
      (appearanceMode === "system" && window.matchMedia("(prefers-color-scheme: dark)").matches)
    ) {
      html.classList.add("dark");
    } else {
      html.classList.remove("dark");
    }
  }, [themeId, appearanceMode, accentColorHex]);

  useEffect(() => {
    setThemeContext(themeId);
    setAppearance(appearanceMode);
    setAccentColor(accentColorHex);
    setReducedMotion(reducedMotion);
  }, [themeId, appearanceMode, accentColorHex, reducedMotion, setThemeContext, setAppearance, setAccentColor, setReducedMotion]);

  useEffect(() => {
    if (!firebaseConfigured || !auth) {
      setUser(null);
      setPrivateAccount(null);
      setSettings(null);
      const timer = window.setTimeout(() => {
        setAuthResolved(true);
        setProfileLoaded(true);
      }, 0);
      return () => window.clearTimeout(timer);
    }

    let cancelled = false;
    let profileUnsub: (() => void) | undefined;
    let privateUnsub: (() => void) | undefined;
    let settingsUnsub: (() => void) | undefined;

    void authRepo.consumeGoogleRedirectResult().then(({ error }) => {
      if (!cancelled && error) {
        setAuthBannerError(error);
      }
    });

    const unsubscribe = onAuthStateChanged(auth, (nextFirebaseUser) => {
      profileUnsub?.();
      privateUnsub?.();
      settingsUnsub?.();
      profileUnsub = undefined;
      privateUnsub = undefined;
      settingsUnsub = undefined;
      setFirebaseUser(nextFirebaseUser);
      setAuthResolved(true);

      if (nextFirebaseUser) {
        const uid = nextFirebaseUser.uid;
        setProfileLoaded(false);
        setAuthBannerError("");
        profileUnsub = authRepo.observeUserProfile(uid)(
          (profile) => {
            setUser(profile);
            setProfileLoaded(true);
            if (profile && recordedSessionUidRef.current !== uid) {
              recordedSessionUidRef.current = uid;
              void authRepo.registerCurrentSession(uid);
            }
          },
          (err) => {
            setAuthBannerError(err.message);
            setProfileLoaded(true);
          }
        );
        privateUnsub = authRepo.observePrivateAccount(uid)((account) => {
          setPrivateAccount(account);
        });
        settingsUnsub = authRepo.observeUserSettings(uid)((settings) => {
          setSettings(settings);
        });
      } else {
        recordedSessionUidRef.current = null;
        setUser(null);
        setPrivateAccount(null);
        setSettings(null);
        useChatStore.getState().clearChat();
        setProfileLoaded(true);
      }
    });

    return () => {
      cancelled = true;
      unsubscribe();
      profileUnsub?.();
      privateUnsub?.();
      settingsUnsub?.();
    };
  }, [setPrivateAccount, setSettings, setUser]);

  useEffect(() => {
    if (!authResolved) return;
    if (!firebaseUser && !PUBLIC_ROUTES.has(routePath)) {
      router.replace("/signup?mode=signin");
    } else if (firebaseUser && profileLoaded && user && routePath === "/signup") {
      router.replace("/");
    }
  }, [authResolved, firebaseUser, profileLoaded, routePath, router, user]);

  const handleSignOut = async () => {
    await authRepo.signOut();
    recordedSessionUidRef.current = null;
    setUser(null);
    setPrivateAccount(null);
    setSettings(null);
    useChatStore.getState().clearChat();
    setFirebaseUser(null);
    if (!PUBLIC_ROUTES.has(routePath)) {
      router.replace("/signup?mode=signin");
    }
  };

  const isPublicRoute = PUBLIC_ROUTES.has(routePath);
  const canRenderChildren =
    isPublicRoute || (authResolved && Boolean(firebaseUser) && profileLoaded && Boolean(user) && !user?.isSuspended);
  const isSuperAdminUser = isSuperAdmin();
  const currentTab =
    routePath === "/" || routePath === "/new-chat"
      ? "chats"
      : routePath === "/social"
        ? "discover"
        : routePath.replace("/", "");
  const tabRoutes: Record<string, string> = {
    chats: "/",
    discover: "/social",
    stories: "/stories",
    communities: "/communities",
    calls: "/calls",
    profile: "/profile",
  };

  return (
    <ThemeProvider>
      <div className="min-h-[100dvh] w-full overflow-x-hidden relative bg-white dark:bg-gray-900">
        {authBannerError && (
          <div role="alert" className="sticky top-0 z-50 flex items-center justify-between gap-3 bg-rose-600 px-4 py-2.5 text-sm text-white shadow">
            <span>{authBannerError}</span>
            <button type="button" onClick={() => setAuthBannerError("")} className="rounded px-2 py-0.5 text-xs font-semibold hover:bg-white/15">
              Dismiss
            </button>
          </div>
        )}

        {/* Gate private routes until live auth & profile are verified */}
        <div className="relative z-10 min-h-[100dvh]">
          {canRenderChildren ? (
            children
          ) : (
            <div className="flex min-h-[100dvh] items-center justify-center bg-gradient-to-br from-violet-500/10 via-transparent to-pink-500/10">
              <LoaderCircle className="animate-spin text-violet-600" size={34} aria-label="Checking session" />
            </div>
          )}
        </div>

        {/* Floating nav + Radial FAB (always visible on main tabs when signed in) */}
        {firebaseUser && user && !user.isSuspended && ["chats", "discover", "stories", "communities", "calls", "profile"].includes(currentTab) && (
          <>
            <FloatingNavBar
              selectedTab={currentTab}
              unreadCount={0}
              onSelectTab={(tab) => router.push(tabRoutes[tab] || "/")}
              onSignOut={handleSignOut}
              onOpenAdmin={() => router.push("/admin")}
              onOpenBackup={() => router.push("/backup")}
              isSuperAdmin={isSuperAdminUser}
            />

            <RadialFabMenu
              isExpanded={radialMenuOpen}
              onToggle={() => setRadialMenuOpen(!radialMenuOpen)}
              onActionSelected={(action) => {
                setRadialMenuOpen(false);
                switch (action) {
                  case "new_chat":
                    router.push("/new-chat");
                    break;
                  case "ai_assistant":
                    router.push("/ai");
                    break;
                  case "create_story":
                    router.push("/stories");
                    break;
                  case "voice_call":
                  case "video_call":
                    router.push("/calls");
                    break;
                  case "create_group":
                  case "create_community":
                    router.push("/communities");
                    break;
                  case "share_image":
                  case "send_document":
                    router.push("/new-chat");
                    break;
                  case "change_theme":
                    router.push("/profile#appearance");
                    break;
                  case "saved_messages":
                    router.push("/saved");
                    break;
                  case "quick_settings":
                    router.push("/profile");
                    break;
                  default:
                    break;
                }
              }}
              onDismiss={() => setRadialMenuOpen(false)}
            />
          </>
        )}

        {routePath === "/signup" && (!authResolved || (firebaseUser && !profileLoaded)) ? (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-gradient-to-br from-violet-500 via-pink-500 to-rose-500">
            <LoaderCircle className="animate-spin text-white" size={36} aria-label="Loading account" />
          </div>
        ) : null}

        {/* First-time account setup: authenticated user without profile must claim a unique @username. */}
        {authResolved && firebaseUser && profileLoaded && !user && (
          <SignupOnboarding
            displayName={firebaseUser.displayName || ""}
            email={firebaseUser.email || ""}
            photoUrl={firebaseUser.photoURL || ""}
            onSignOut={handleSignOut}
          />
        )}

        {authResolved && firebaseUser && profileLoaded && user?.isSuspended && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-md">
            <section className="w-full max-w-md rounded-3xl border border-rose-500/30 bg-white p-6 text-center shadow-2xl dark:bg-gray-900">
              <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-2xl bg-rose-500/10 text-rose-600">
                <ShieldAlert size={24} />
              </div>
              <h2 className="text-xl font-bold text-gray-900 dark:text-white">Account Suspended</h2>
              <p className="mt-2 text-sm text-gray-600 dark:text-gray-300">
                This account has been suspended by an administrator. Please sign out or contact support.
              </p>
              <button
                type="button"
                onClick={handleSignOut}
                className="mt-5 w-full rounded-xl bg-rose-600 px-4 py-3 font-semibold text-white hover:bg-rose-700"
              >
                Sign Out
              </button>
            </section>
          </div>
        )}
      </div>
    </ThemeProvider>
  );
}
