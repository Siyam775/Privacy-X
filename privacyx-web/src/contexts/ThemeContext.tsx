"use client";

import { createContext, useContext, useEffect, useState, ReactNode } from "react";
import { AppThemeId, AppearanceMode } from "@/types/index";

interface ThemeContextType {
  themeId: AppThemeId;
  appearanceMode: AppearanceMode;
  accentColorHex: string;
  reducedMotion: boolean;
  setTheme: (id: AppThemeId) => void;
  setAppearance: (mode: AppearanceMode) => void;
  setAccentColor: (hex: string) => void;
  setReducedMotion: (val: boolean) => void;
  isDark: boolean;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [themeId, setThemeId] = useState<AppThemeId>("material_liquid_glass");
  const [appearanceMode, setAppearanceMode] = useState<AppearanceMode>("system");
  const [accentColorHex, setAccentColorHex] = useState<string>("#7C3AED");
  const [reducedMotion, setReducedMotion] = useState(false);

  useEffect(() => {
    const frame = window.requestAnimationFrame(() => {
      const saved = localStorage.getItem("privacyx_theme");
      if (!saved) return;
      try {
        const parsed = JSON.parse(saved) as Partial<ThemeContextType>;
        if (parsed.themeId && ["material_liquid_glass", "pure_ios_glass", "classic_bw"].includes(parsed.themeId)) setThemeId(parsed.themeId);
        if (parsed.appearanceMode && ["system", "light", "dark"].includes(parsed.appearanceMode)) setAppearanceMode(parsed.appearanceMode);
        if (typeof parsed.accentColorHex === "string" && /^#[0-9a-f]{6}$/i.test(parsed.accentColorHex)) setAccentColorHex(parsed.accentColorHex);
        if (typeof parsed.reducedMotion === "boolean") setReducedMotion(parsed.reducedMotion);
      } catch {
        localStorage.removeItem("privacyx_theme");
      }
    });
    return () => window.cancelAnimationFrame(frame);
  }, []);

  useEffect(() => {
    const html = document.documentElement;
    html.setAttribute("data-theme", themeId);
    html.setAttribute("data-accent", accentColorHex);

    if (appearanceMode === "dark" || (appearanceMode === "system" && window.matchMedia("(prefers-color-scheme: dark)").matches)) {
      html.classList.add("dark");
    } else {
      html.classList.remove("dark");
    }

    // Persist
    localStorage.setItem(
      "privacyx_theme",
      JSON.stringify({ themeId, appearanceMode, accentColorHex, reducedMotion })
    );
  }, [themeId, appearanceMode, accentColorHex, reducedMotion]);

  const isDark =
    appearanceMode === "dark" ||
    (appearanceMode === "system" &&
      typeof window !== "undefined" &&
      window.matchMedia("(prefers-color-scheme: dark)").matches);

  return (
    <ThemeContext.Provider
      value={{
        themeId,
        appearanceMode,
        accentColorHex,
        reducedMotion,
        setTheme: setThemeId,
        setAppearance: setAppearanceMode,
        setAccentColor: setAccentColorHex,
        setReducedMotion,
        isDark,
      }}
    >
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    return {
      themeId: "material_liquid_glass" as AppThemeId,
      appearanceMode: "system" as AppearanceMode,
      accentColorHex: "#7C3AED",
      reducedMotion: false,
      setTheme: () => {},
      setAppearance: () => {},
      setAccentColor: () => {},
      setReducedMotion: () => {},
      isDark: false,
    };
  }
  return ctx;
}
