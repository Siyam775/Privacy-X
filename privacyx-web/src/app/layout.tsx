"use client";

import { useEffect, useState } from "react";
import { auth, firebaseConfigured, onAuthStateChanged, signOut } from "@/lib/firebase";
import { AuthRepository } from "@/services/authRepository";
import { ThemeProvider, useTheme } from "@/contexts/ThemeContext";
import { useAuthStore, useThemeStore } from "@/stores/index";
import { RadialFabMenu } from "@/components/RadialFabMenu";
import { FloatingNavBar } from "@/components/FloatingNavBar";
import { SignupOnboarding } from "@/components/SignupOnboarding";
import { LoaderCircle } from "lucide-react";
import { useRouter, usePathname } from "next/navigation";
import "./globals.css";
import type { User as FirebaseUser } from "firebase/auth";

const authRepo = new AuthRepository();

export default function RootLayout({ children }: { children: React.ReactNode }) {
  const [radialMenuOpen, setRadialMenuOpen] = useState(false);
  const [firebaseUser, setFirebaseUser] = useState<FirebaseUser | null>(null);
  const [authResolved, setAuthResolved] = useState(!firebaseConfigured || !auth);
  const [profileLoaded, setProfileLoaded] = useState(!firebaseConfigured || !auth);
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

    if (appearanceMode === "dark" || (appearanceMode === "system" && window.matchMedia("(prefers-color-scheme: dark)").matches)) {
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
      return;
    }
    let profileUnsub: (() => void) | undefined;
    let privateUnsub: (() => void) | undefined;
    let settingsUnsub: (() => void) | undefined;
    const unsubscribe = onAuthStateChanged(auth, (firebaseUser) => {
      profileUnsub?.();
      privateUnsub?.();
      settingsUnsub?.();
      profileUnsub = undefined;
      privateUnsub = undefined;
      settingsUnsub = undefined;
      setFirebaseUser(firebaseUser);
      setAuthResolved(true);

      if (firebaseUser) {
        const uid = firebaseUser.uid;
        setProfileLoaded(false);
        profileUnsub = authRepo.observeUserProfile(uid)((profile) => {
          setUser(profile);
          setProfileLoaded(true);
        });
        privateUnsub = authRepo.observePrivateAccount(uid)((account) => {
          setPrivateAccount(account);
        });
        settingsUnsub = authRepo.observeUserSettings(uid)((settings) => {
          setSettings(settings);
        });
      } else {
        setUser(null);
        setPrivateAccount(null);
        setSettings(null);
        setProfileLoaded(true);
      }
    });

    return () => {
      unsubscribe();
      profileUnsub?.();
      privateUnsub?.();
      settingsUnsub?.();
    };
  }, [setPrivateAccount, setSettings, setUser]);

  useEffect(() => {
    if (!authResolved) return;
    if (!firebaseUser && routePath !== "/" && routePath !== "/signup") {
      router.replace("/");
    } else if (firebaseUser && user && routePath === "/signup") {
      router.replace("/");
    }
  }, [authResolved, firebaseUser, routePath, router, user]);

  const handleSignOut = async () => {
    if (auth) await signOut(auth);
    setUser(null);
    setPrivateAccount(null);
    setSettings(null);
    setFirebaseUser(null);
  };

  const isSuperAdminUser = isSuperAdmin();
  const currentTab = routePath === "/" || routePath === "/new-chat" ? "chats" : routePath === "/social" ? "discover" : routePath.replace("/", "");
  const tabRoutes: Record<string, string> = {
    chats: "/",
    discover: "/social",
    stories: "/stories",
    communities: "/communities",
    calls: "/calls",
    profile: "/profile",
  };

  return (
    <html lang="en" className="h-full">
      <body className="min-h-[100dvh] flex flex-col bg-white dark:bg-gray-900 theme-transition antialiased selection:bg-violet-500/30">
        <ThemeProvider>
          <div className="min-h-[100dvh] w-full overflow-x-hidden relative bg-white dark:bg-gray-900">
            {/* App shell renders immediately */}
            <div className="relative z-10 min-h-[100dvh]">
              {children}
            </div>

            {/* Floating nav + Radial FAB (always visible on main tabs) */}
            {user && ["chats", "discover", "stories", "communities", "calls", "profile"].includes(currentTab) && (
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

            {/* First-time account setup mirrors Android: Google auth, then claim a unique username. */}
            {authResolved && firebaseUser && profileLoaded && !user && (
              <SignupOnboarding
                displayName={firebaseUser.displayName || ""}
                email={firebaseUser.email || ""}
                photoUrl={firebaseUser.photoURL || ""}
                onSignOut={handleSignOut}
              />
            )}

          </div>
        </ThemeProvider>
      </body>
    </html>
  );
}
