"use client";

import { useState } from "react";
import { useThemeStore } from "@/stores/index";
import { MessageCircle, Compass, BookOpen, Users, Phone, User, Settings, Palette } from "lucide-react";
import { cn } from "@/lib/utils";
import { AppThemeId, AppearanceMode } from "@/types/index";

const NAV_TABS = [
  { id: "chats", label: "Chats", icon: MessageCircle },
  { id: "discover", label: "Discover", icon: Compass },
  { id: "stories", label: "Stories", icon: BookOpen },
  { id: "communities", label: "Spaces", icon: Users },
  { id: "calls", label: "Calls", icon: Phone },
  { id: "profile", label: "Profile", icon: User },
] as const;

interface FloatingNavBarProps {
  selectedTab: string;
  unreadCount: number;
  onSelectTab: (tab: string) => void;
  onSignOut: () => void;
  onOpenAdmin?: () => void;
  onOpenBackup?: () => void;
  isSuperAdmin?: boolean;
}

export function FloatingNavBar({
  selectedTab,
  unreadCount,
  onSelectTab,
  onSignOut,
  onOpenAdmin,
  onOpenBackup,
  isSuperAdmin,
}: FloatingNavBarProps) {
  const { themeId, setTheme, setAppearance, setAccentColor, setReducedMotion, appearanceMode, accentColorHex, reducedMotion } =
    useThemeStore();
  const [showThemeMenu, setShowThemeMenu] = useState(false);
  const [showSettingsMenu, setShowSettingsMenu] = useState(false);

  return (
    <div className="fixed bottom-[calc(0.5rem+env(safe-area-inset-bottom))] left-1/2 z-40 w-[calc(100%-1rem)] max-w-[calc(100vw-1rem)] -translate-x-1/2 sm:bottom-[calc(1rem+env(safe-area-inset-bottom))] sm:w-auto">
      <div className="flex max-w-full items-center justify-center gap-1 overflow-x-auto rounded-full border border-white/80 bg-white/90 px-2 py-2 shadow-xl backdrop-blur-xl dark:border-gray-700/80 dark:bg-gray-900/90 sm:gap-2 sm:px-3 max-[360px]:gap-0 max-[360px]:px-1">
        {NAV_TABS.map((tab) => {
          const Icon = tab.icon;
          const selected = selectedTab === tab.id;
          return (
            <button
              key={tab.id}
              type="button"
              aria-label={tab.label}
              aria-current={selected ? "page" : undefined}
              title={tab.label}
              onClick={() => onSelectTab(tab.id)}
              className={cn(
                "flex h-10 w-10 shrink-0 items-center justify-center rounded-full transition-all max-[360px]:h-8 max-[360px]:w-8",
                selected
                  ? "bg-violet-600 text-white shadow-lg"
                  : "text-gray-500 hover:bg-white/30 dark:hover:bg-gray-800 text-gray-600 dark:text-gray-300 transition-colors"
              )}
            >
              <Icon size={20} />
              {tab.id === "chats" && unreadCount > 0 && (
                <span className="absolute -right-1 -top-1 w-4 h-4 rounded-full bg-rose-500 text-xs text-white flex items-center justify-center">
                  {unreadCount > 9 ? "9+" : unreadCount}
                </span>
              )}
            </button>
          );
        })}
        <button
          type="button"
          aria-label="Appearance settings"
          title="Appearance"
          onClick={() => { setShowThemeMenu((open) => !open); setShowSettingsMenu(false); }}
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-gray-500 transition-colors hover:bg-violet-500/10 hover:text-violet-600 dark:text-gray-300"
        ><Palette size={19} /></button>
        <button
          type="button"
          aria-label="Settings"
          title="Settings"
          onClick={() => { setShowSettingsMenu((open) => !open); setShowThemeMenu(false); }}
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-gray-500 transition-colors hover:bg-violet-500/10 hover:text-violet-600 dark:text-gray-300"
        ><Settings size={19} /></button>
      </div>

      {showThemeMenu && (
        <ThemeMenu
          themeId={themeId}
          appearanceMode={appearanceMode}
          accentColorHex={accentColorHex}
          reducedMotion={reducedMotion}
          onSetTheme={setTheme}
          onSetAppearance={setAppearance}
          onSetAccentColor={setAccentColor}
          onSetReducedMotion={setReducedMotion}
          onClose={() => setShowThemeMenu(false)}
        />
      )}

      {showSettingsMenu && (
        <SettingsMenu
          onSignOut={onSignOut}
          onOpenAdmin={onOpenAdmin}
          onOpenBackup={onOpenBackup}
          isSuperAdmin={isSuperAdmin}
          onClose={() => setShowSettingsMenu(false)}
        />
      )}
    </div>
  );
}

import { THEME_TITLES } from "@/lib/theme";

function ThemeMenu({
  themeId,
  appearanceMode,
  accentColorHex,
  reducedMotion,
  onSetTheme,
  onSetAppearance,
  onSetAccentColor,
  onSetReducedMotion,
  onClose,
}: {
  themeId: AppThemeId;
  appearanceMode: AppearanceMode;
  accentColorHex: string;
  reducedMotion: boolean;
  onSetTheme: (id: AppThemeId) => void;
  onSetAppearance: (mode: AppearanceMode) => void;
  onSetAccentColor: (hex: string) => void;
  onSetReducedMotion: (val: boolean) => void;
  onClose: () => void;
}) {
  const themes: AppThemeId[] = ["material_liquid_glass", "pure_ios_glass", "classic_bw"];
  const modes: AppearanceMode[] = ["system", "light", "dark"];

  return (
    <div
      className="fixed bottom-20 left-1/2 -translate-x-1/2 w-80 bg-white/95 backdrop-blur-xl border border-white/80 rounded-2xl shadow-2xl dark:bg-gray-900/95 dark:border-gray-700 p-4 z-50"
      onClick={(e) => e.stopPropagation()}
    >
      <h3 className="text-sm font-semibold text-gray-900 mb-3 dark:text-white">Appearance</h3>

      <div className="space-y-3 mb-4">
        {themes.map((t) => {
          const info = THEME_TITLES[t as keyof typeof THEME_TITLES];
          const selected = t === themeId;
          return (
            <button
              key={t}
              onClick={() => onSetTheme(t)}
              className={cn(
                "flex items-center gap-3 px-3 py-2 rounded-xl transition-colors",
                selected
                  ? "bg-violet-100 border border-violet-200 dark:bg-violet-900/30 dark:border-violet-500/30"
                  : "hover:bg-gray-100 dark:hover:bg-gray-800/50"
              )}
            >
              <div className="w-8 h-8 rounded-full flex items-center justify-center shrink-0 border border-gray-300/50">
                {t === "material_liquid_glass" && "🌈"}
                {t === "pure_ios_glass" && "🔮"}
                {t === "classic_bw" && "⚫"}
              </div>
              <div>
                <div className="text-sm font-medium text-gray-900 dark:text-white">{info?.title || t}</div>
                <div className="text-xs text-gray-500 dark:text-gray-400">{info?.subtitle || ""}</div>
              </div>
              {selected && (
                <svg className="w-4 h-4 text-violet-600 ml-auto" fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                </svg>
              )}
            </button>
          );
        })}
      </div>

      <div className="space-y-3 mb-4">
        <div className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">Mode</div>
        <div className="flex gap-2">
          {modes.map((mode) => (
            <button
              key={mode}
              onClick={() => onSetAppearance(mode)}
              className={cn(
                "flex-1 py-1.5 rounded-lg text-xs font-medium transition-colors",
                appearanceMode === mode
                  ? "bg-violet-600 text-white"
                  : "bg-gray-100 text-gray-700 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700"
              )}
            >
              {mode.charAt(0).toUpperCase() + mode.slice(1)}
            </button>
          ))}
        </div>
      </div>

      <div className="space-y-3 mb-4">
        <div className="flex items-center justify-between">
          <span className="text-sm text-gray-600 dark:text-gray-300">Custom Accent</span>
          <input
            type="color"
            value={accentColorHex || "#7C3AED"}
            onChange={(e) => onSetAccentColor(e.target.value)}
            className="w-10 h-10 rounded-full border-none cursor-pointer bg-transparent"
          />
        </div>
      </div>

      <div className="flex items-center justify-between pt-3 border-t border-gray-100/50">
        <span className="text-sm text-gray-600 dark:text-gray-300">Reduced Motion</span>
        <button
          onClick={() => onSetReducedMotion(!reducedMotion)}
          className={cn(
            "relative w-10 h-5 rounded-full transition-colors",
            reducedMotion ? "bg-violet-600" : "bg-gray-300"
          )}
        >
          <span
            className={cn(
              "block w-3 h-3 rounded-full bg-white absolute transition-transform",
              reducedMotion ? "translate-x-4" : "translate-x-1"
            )}
          />
        </button>
      </div>

      <div className="mt-4">
        <button
          onClick={onClose}
          className="w-full py-2 rounded-lg text-sm font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800"
        >
          Done
        </button>
      </div>
    </div>
  );
}

function SettingsMenu({
  onSignOut,
  onOpenAdmin,
  onOpenBackup,
  isSuperAdmin,
  onClose,
}: {
  onSignOut: () => void;
  onOpenAdmin?: () => void;
  onOpenBackup?: () => void;
  isSuperAdmin?: boolean;
  onClose: () => void;
}) {
  return (
    <div
      className="fixed bottom-20 left-1/2 -translate-x-1/2 w-64 bg-white/95 backdrop-blur-xl border border-white/80 rounded-2xl shadow-2xl dark:bg-gray-900/95 dark:border-gray-700 p-4 z-50"
      onClick={(e) => e.stopPropagation()}
    >
      <h3 className="text-sm font-semibold text-gray-900 mb-3 dark:text-white">Settings</h3>

      <button onClick={() => { onClose(); onOpenBackup?.(); }} className="mb-2 w-full rounded-lg px-3 py-2 text-left text-sm text-gray-700 hover:bg-gray-100 dark:text-gray-200 dark:hover:bg-gray-800">Storage &amp; Backup</button>

      <button
        onClick={() => {
          onClose();
          onSignOut();
        }}
        className="w-full flex items-center justify-between px-3 py-2 rounded-lg text-sm text-gray-900 hover:bg-gray-100 dark:text-white dark:hover:bg-gray-800 transition-colors"
      >
        <span>Sign Out</span>
        <span className="text-gray-400">→</span>
      </button>

      {isSuperAdmin && onOpenAdmin && (
        <button
          onClick={() => {
            onClose();
            onOpenAdmin();
          }}
          className="w-full flex items-center justify-between px-3 py-2 rounded-lg text-sm text-violet-600 hover:bg-violet-50 dark:text-violet-400 dark:hover:bg-violet-900/20 transition-colors mt-2"
        >
          <span>Super Admin Dashboard</span>
          <span className="text-violet-400">→</span>
        </button>
      )}
      <button onClick={onClose} className="mt-2 w-full rounded-lg px-3 py-2 text-left text-sm text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800">Done</button>
    </div>
  );
}
