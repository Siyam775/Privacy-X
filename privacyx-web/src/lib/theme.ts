import { AppThemeId, AppearanceMode } from "@/types/index";

export type { AppThemeId, AppearanceMode } from "@/types/index";

export const THEME_COLORS: Record<AppThemeId, Record<string, Record<string, string>>> = {
  material_liquid_glass: {
    light: {
      bg: "bg-gradient-to-br from-lavender-mist via-lavender-cloud to-blush-petal",
      surface: "bg-white/76 backdrop-blur-md border-white/92",
      surfaceElevated: "bg-white/90 backdrop-blur-md border-white/92",
      navBar: "bg-white/84 backdrop-blur-md border-white/92",
      sentBubble: "bg-gradient-to-br from-royal-violet to-electric-lavender",
      sentText: "text-white",
      receivedBubble: "bg-white/88 backdrop-blur-md border-white/92",
      receivedText: "text-deep-plum-ink",
      accent: "text-royal-violet",
      accentBg: "bg-royal-violet",
      radialOverlay: "bg-[#E9E2FF]/62 backdrop-blur-md",
      radialFab: "bg-gradient-to-br from-royal-violet to-electric-lavender",
      onlineGreen: "bg-emerald-500",
      dangerRed: "bg-rose-500",
      warningAmber: "bg-amber-500",
    },
    dark: {
      bg: "bg-gradient-to-br from-midnight-lavender via-midnight-plum to-midnight-violet",
      surface: "bg-[#231D3F]/78 backdrop-blur-md border-[#473A7A]/65",
      surfaceElevated: "bg-[#2D2552]/88 backdrop-blur-md border-[#473A7A]/65",
      navBar: "bg-[#1F193B]/88 backdrop-blur-md border-[#473A7A]/65",
      sentBubble: "bg-gradient-to-br from-royal-violet to-electric-lavender",
      sentText: "text-white",
      receivedBubble: "bg-[#2B234A]/85 backdrop-blur-md border-[#473A7A]/65",
      receivedText: "text-lavender-glow",
      accent: "text-royal-violet",
      accentBg: "bg-royal-violet",
      radialOverlay: "bg-[#0C0818]/62 backdrop-blur-md",
      radialFab: "bg-gradient-to-br from-royal-violet to-soft-orchid-pink",
      onlineGreen: "bg-emerald-500",
      dangerRed: "bg-rose-500",
      warningAmber: "bg-amber-500",
    },
  },
  pure_ios_glass: {
    light: {
      bg: "bg-gradient-to-b from-ios-ice-top via-ios-ice-mid to-ios-ice-bottom",
      surface: "bg-white/58 backdrop-blur-xl border-white/95",
      surfaceElevated: "bg-white/76 backdrop-blur-xl border-white/95",
      navBar: "bg-white/66 backdrop-blur-xl border-white/95",
      sentBubble: "bg-gradient-to-br from-ios-crystal-blue to-royal-violet",
      sentText: "text-white",
      receivedBubble: "bg-white/76 backdrop-blur-xl border-white/95",
      receivedText: "text-[#111322]",
      accent: "text-ios-crystal-violet",
      accentBg: "bg-ios-crystal-violet",
      radialOverlay: "bg-[#DDE7FF]/58 backdrop-blur-xl",
      radialFab: "bg-gradient-to-br from-ios-crystal-blue to-royal-violet",
      onlineGreen: "bg-emerald-500",
      dangerRed: "bg-rose-500",
      warningAmber: "bg-amber-500",
    },
    dark: {
      bg: "bg-gradient-to-b from-ios-dark-top via-ios-dark-mid to-ios-dark-bottom",
      surface: "bg-[#1B1E36]/62 backdrop-blur-xl border-white/24",
      surfaceElevated: "bg-[#272B4A]/78 backdrop-blur-xl border-white/24",
      navBar: "bg-[#171A30]/72 backdrop-blur-xl border-white/24",
      sentBubble: "bg-gradient-to-br from-ios-crystal-blue to-royal-violet",
      sentText: "text-white",
      receivedBubble: "bg-white/14 backdrop-blur-xl border-white/24",
      receivedText: "text-white",
      accent: "text-ios-crystal-violet",
      accentBg: "bg-ios-crystal-violet",
      radialOverlay: "bg-[#060914]/68 backdrop-blur-xl",
      radialFab: "bg-gradient-to-br from-ios-crystal-blue to-royal-violet",
      onlineGreen: "bg-emerald-500",
      dangerRed: "bg-rose-500",
      warningAmber: "bg-amber-500",
    },
  },
  classic_bw: {
    light: {
      bg: "bg-white",
      surface: "bg-white border-neutral-hairline-light",
      surfaceElevated: "bg-off-white border-neutral-hairline-light",
      navBar: "bg-white border-neutral-hairline-light",
      sentBubble: "bg-black",
      sentText: "text-white",
      receivedBubble: "bg-[#ECECEF]",
      receivedText: "text-black",
      accent: "text-black",
      accentBg: "bg-black",
      radialOverlay: "bg-black/45",
      radialFab: "bg-gradient-to-br from-black to-[#27272A]",
      onlineGreen: "bg-emerald-500",
      dangerRed: "bg-rose-500",
      warningAmber: "bg-amber-500",
    },
    dark: {
      bg: "bg-black",
      surface: "bg-charcoal-surface border-neutral-hairline-dark",
      surfaceElevated: "bg-[#1E1E22] border-neutral-hairline-dark",
      navBar: "bg-[#0E0E10] border-neutral-hairline-dark",
      sentBubble: "bg-white",
      sentText: "text-black",
      receivedBubble: "bg-[#1C1C1F]",
      receivedText: "text-white",
      accent: "text-white",
      accentBg: "bg-white",
      radialOverlay: "bg-black/78",
      radialFab: "bg-gradient-to-br from-white to-[#E4E4E7]",
      onlineGreen: "bg-emerald-500",
      dangerRed: "bg-rose-500",
      warningAmber: "bg-amber-500",
    },
  },
};

export const THEME_TITLES: Record<AppThemeId, { title: string; subtitle: string }> = {
  material_liquid_glass: {
    title: "Material Liquid Glass",
    subtitle: "Lavender, purple & pink ambient glass",
  },
  pure_ios_glass: {
    title: "Pure iOS Glass",
    subtitle: "Deep translucent crystal & specular highlights",
  },
  classic_bw: {
    title: "Classic Black & White",
    subtitle: "High-contrast minimal monochrome",
  },
};

export function getThemeColors(themeId: AppThemeId, isDark: boolean) {
  return THEME_COLORS[themeId][isDark ? "dark" : "light"];
}

export function resolveIsDark(appearanceMode: AppearanceMode): boolean {
  if (appearanceMode === "light") return false;
  if (appearanceMode === "dark") return true;
  return typeof window !== "undefined" && window.matchMedia("(prefers-color-scheme: dark)").matches;
}
