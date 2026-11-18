"use client";

import { useRef, useCallback } from "react";
import { useThemeStore } from "@/stores/index";
import { cn } from "@/lib/utils";

export type RadialAction =
  | "new_chat"
  | "voice_call"
  | "ai_assistant"
  | "create_group"
  | "create_community"
  | "create_story"
  | "share_image"
  | "send_document"
  | "video_call"
  | "change_theme"
  | "saved_messages"
  | "quick_settings";

interface RadialActionConfig {
  id: RadialAction;
  label: string;
  icon: string;
  ring: 1 | 2 | 3;
  angleDeg: number;
}

const RADIAL_ACTIONS: RadialActionConfig[] = [
  // Ring 1 - Core quick actions
  { id: "new_chat", label: "New Chat", icon: "💬", ring: 1, angleDeg: 185 },
  { id: "voice_call", label: "Voice Call", icon: "📞", ring: 1, angleDeg: 225 },
  { id: "ai_assistant", label: "AI Assist", icon: "✨", ring: 1, angleDeg: 265 },
  // Ring 2 - Creation & media
  { id: "create_group", label: "Group", icon: "👥", ring: 2, angleDeg: 182 },
  { id: "create_community", label: "Community", icon: "🏘️", ring: 2, angleDeg: 204 },
  { id: "create_story", label: "Story", icon: "📖", ring: 2, angleDeg: 226 },
  { id: "share_image", label: "Image", icon: "🖼️", ring: 2, angleDeg: 248 },
  { id: "send_document", label: "Document", icon: "📄", ring: 2, angleDeg: 270 },
  // Ring 3 - Calls & utilities
  { id: "video_call", label: "Video Call", icon: "📹", ring: 3, angleDeg: 188 },
  { id: "change_theme", label: "Theme", icon: "🎨", ring: 3, angleDeg: 212 },
  { id: "saved_messages", label: "Saved", icon: "🔖", ring: 3, angleDeg: 236 },
  { id: "quick_settings", label: "Settings", icon: "⚙️", ring: 3, angleDeg: 260 },
];

const RING_RADIUS: Record<1 | 2 | 3, number> = { 1: 90, 2: 160, 3: 230 };

interface RadialFabMenuProps {
  isExpanded: boolean;
  onToggle: () => void;
  onActionSelected: (action: RadialAction) => void;
  onDismiss?: () => void;
}

export function RadialFabMenu({
  isExpanded,
  onToggle,
  onActionSelected,
  onDismiss,
}: RadialFabMenuProps) {
  const { reducedMotion, themeId } = useThemeStore();
  const containerRef = useRef<HTMLDivElement>(null);
  const handleScrimClick = useCallback(() => {
    onDismiss?.();
    onToggle();
  }, [onDismiss, onToggle]);

  const getActionPosition = (action: RadialActionConfig, progress: number) => {
    const radius = RING_RADIUS[action.ring];
    const rad = (action.angleDeg * Math.PI) / 180;
    const x = Math.cos(rad) * radius * progress;
    const y = Math.sin(rad) * radius * progress;
    return { x, y };
  };

  const accentColors: Record<string, string> = {
    material_liquid_glass: "from-violet-600 to-pink-500",
    pure_ios_glass: "from-blue-500 to-violet-500",
    classic_bw: "from-gray-900 to-gray-600",
  };

  return (
    <div
      ref={containerRef}
      className="fixed bottom-0 right-0 w-full h-full pointer-events-none z-50"
    >
      {/* Scrim backdrop */}
      {isExpanded && (
        <div
          className={`fixed inset-0 bg-black/40 backdrop-blur-sm z-40 transition-opacity duration-300 opacity-100`}
          onClick={handleScrimClick}
        />
      )}

      {/* Anchor box at bottom-right */}
      <div
        className="absolute bottom-[calc(5.5rem+env(safe-area-inset-bottom))] right-4 flex items-end justify-end z-50 pointer-events-auto sm:bottom-8 sm:right-8"
      >
        <div className="relative">
          {/* Radial action buttons */}
          {isExpanded &&
            RADIAL_ACTIONS.map((action) => {
              const { x, y } = getActionPosition(action, 1);
              const delayIndex = action.ring - 1;
              return (
                <div
                  key={action.id}
                  className="absolute flex flex-col items-center"
                  style={{
                    transform: `translate(${x}px, ${y}px)`,
                    transition: reducedMotion
                      ? "none"
                      : `all 0.4s cubic-bezier(0.25, 0.46, 0.45, 0.94) ${delayIndex * 0.04}s`,
                    opacity: isExpanded ? 1 : 0,
                  }}
                >
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      onActionSelected(action.id);
                    }}
                    className={cn(
                      "w-14 h-14 rounded-full flex items-center justify-center border border-white/60 backdrop-blur-md shadow-lg text-lg transition-all active:scale-95",
                      accentColors[themeId] || accentColors.material_liquid_glass,
                      "text-white"
                    )}
                  >
                    <span>{action.icon}</span>
                  </button>
                  <span className="mt-1.5 px-2 py-0.5 rounded-full text-xs font-medium bg-white/90 backdrop-blur text-gray-800 whitespace-nowrap border border-gray-200/50">
                    {action.label}
                  </span>
                </div>
              );
            })}

          {/* FAB button */}
          <button
            onClick={(e) => {
              e.stopPropagation();
              onToggle();
            }}
            className={cn(
              "relative flex h-14 w-14 items-center justify-center rounded-full border border-white/60 backdrop-blur-md shadow-xl transition-transform duration-300 active:scale-95 z-10 sm:h-16 sm:w-16",
              accentColors[themeId] || accentColors.material_liquid_glass,
              "text-white",
              isExpanded ? "rotate-90" : ""
            )}
          >
            <span className="text-xl transition-transform duration-300">
              {isExpanded ? "✕" : "💬"}
            </span>
          </button>
        </div>
      </div>
    </div>
  );
}
