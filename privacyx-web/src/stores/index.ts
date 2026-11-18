import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";
import {
  AppThemeId,
  AppearanceMode,
  UserProfile,
  UserPrivateAccount,
  UserSettings,
  Conversation,
  ChatMessage,
  Story,
  SocialPost,
  CallSession,
  AiConversation,
  AiMessage,
  SUPER_ADMIN_UID,
} from "@/types/index";

export interface ThemeState {
  themeId: AppThemeId;
  appearanceMode: AppearanceMode;
  accentColorHex: string;
  reducedMotion: boolean;
  setTheme: (id: AppThemeId) => void;
  setAppearance: (mode: AppearanceMode) => void;
  setAccentColor: (hex: string) => void;
  setReducedMotion: (val: boolean) => void;
}

export const useThemeStore = create<ThemeState>()(
  persist(
    (set) => ({
      themeId: "material_liquid_glass",
      appearanceMode: "system",
      accentColorHex: "#7C3AED",
      reducedMotion: false,
      setTheme: (themeId) => set({ themeId }),
      setAppearance: (appearanceMode) => set({ appearanceMode }),
      setAccentColor: (accentColorHex) => set({ accentColorHex }),
      setReducedMotion: (reducedMotion) => set({ reducedMotion }),
    }),
    {
      name: "privacyx_theme_prefs",
      storage: createJSONStorage(() => localStorage),
    }
  )
);

export interface AuthState {
  user: UserProfile | null;
  privateAccount: UserPrivateAccount | null;
  settings: UserSettings | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  setUser: (user: UserProfile | null) => void;
  setPrivateAccount: (account: UserPrivateAccount | null) => void;
  setSettings: (settings: UserSettings | null) => void;
  setLoading: (loading: boolean) => void;
  signOut: () => void;
  isSuperAdmin: () => boolean;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      privateAccount: null,
      settings: null,
      isAuthenticated: false,
      isLoading: true,
      setUser: (user) => set({ user, isAuthenticated: !!user, isLoading: false }),
      setPrivateAccount: (privateAccount) => set({ privateAccount }),
      setSettings: (settings) => set({ settings }),
      setLoading: (isLoading) => set({ isLoading }),
      signOut: () => set({ user: null, privateAccount: null, settings: null, isAuthenticated: false, isLoading: false }),
      isSuperAdmin: () => get().user?.userId === SUPER_ADMIN_UID,
    }),
    {
      name: "privacyx_auth",
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({ settings: state.settings }),
    }
  )
);

export interface ChatState {
  conversations: Conversation[];
  activeConversation: Conversation | null;
  messages: ChatMessage[];
  isOffline: boolean;
  draftText: string;
  setConversations: (convs: Conversation[]) => void;
  setActiveConversation: (conv: Conversation | null) => void;
  setMessages: (msgs: ChatMessage[]) => void;
  addMessage: (msg: ChatMessage) => void;
  updateMessage: (id: string, updates: Partial<ChatMessage>) => void;
  setIsOffline: (offline: boolean) => void;
  setDraft: (text: string) => void;
  clearChat: () => void;
}

export const useChatStore = create<ChatState>((set) => ({
  conversations: [],
  activeConversation: null,
  messages: [],
  isOffline: false,
  draftText: "",
  setConversations: (conversations) => set({ conversations }),
  setActiveConversation: (activeConversation) => set({ activeConversation }),
  setMessages: (messages) => set({ messages }),
  addMessage: (msg) => set((state) => ({ messages: [...state.messages, msg] })),
  updateMessage: (id, updates) =>
    set((state) => ({
      messages: state.messages.map((m) => (m.id === id ? { ...m, ...updates } : m)),
    })),
  setIsOffline: (isOffline) => set({ isOffline }),
  setDraft: (draftText) => set({ draftText }),
  clearChat: () => set({ messages: [], activeConversation: null, draftText: "" }),
}));

export interface SocialState {
  stories: Story[];
  posts: SocialPost[];
  following: string[];
  followers: string[];
  blocked: string[];
  setStories: (stories: Story[]) => void;
  setPosts: (posts: SocialPost[]) => void;
  addPost: (post: SocialPost) => void;
  setFollowing: (ids: string[]) => void;
  setFollowers: (ids: string[]) => void;
  setBlocked: (ids: string[]) => void;
}

export const useSocialStore = create<SocialState>((set) => ({
  stories: [],
  posts: [],
  following: [],
  followers: [],
  blocked: [],
  setStories: (stories) => set({ stories }),
  setPosts: (posts) => set({ posts }),
  addPost: (post) => set((state) => ({ posts: [post, ...state.posts] })),
  setFollowing: (following) => set({ following }),
  setFollowers: (followers) => set({ followers }),
  setBlocked: (blocked) => set({ blocked }),
}));

export interface CallState {
  activeCall: CallSession | null;
  incomingCall: CallSession | null;
  callHistory: CallSession[];
  isCallActive: boolean;
  setActiveCall: (call: CallSession | null) => void;
  setIncomingCall: (call: CallSession | null) => void;
  setCallHistory: (calls: CallSession[]) => void;
  endCall: () => void;
}

export const useCallStore = create<CallState>((set) => ({
  activeCall: null,
  incomingCall: null,
  callHistory: [],
  isCallActive: false,
  setActiveCall: (activeCall) => set({ activeCall, isCallActive: !!activeCall }),
  setIncomingCall: (incomingCall) => set({ incomingCall }),
  setCallHistory: (callHistory) => set({ callHistory }),
  endCall: () => set({ activeCall: null, incomingCall: null, isCallActive: false }),
}));

export interface AIState {
  sessions: AiConversation[];
  activeSessionId: string | null;
  messages: AiMessage[];
  streamingText: string;
  isStreaming: boolean;
  errorMessage: string | null;
  setSessions: (sessions: AiConversation[]) => void;
  setActiveSession: (id: string | null) => void;
  setMessages: (messages: AiMessage[]) => void;
  addMessage: (msg: AiMessage) => void;
  setStreaming: (text: string, isStreaming: boolean) => void;
  setError: (error: string | null) => void;
  clearSession: () => void;
}

export const useAIStore = create<AIState>((set) => ({
  sessions: [],
  activeSessionId: null,
  messages: [],
  streamingText: "",
  isStreaming: false,
  errorMessage: null,
  setSessions: (sessions) => set({ sessions }),
  setActiveSession: (activeSessionId) => set({ activeSessionId }),
  setMessages: (messages) => set({ messages }),
  addMessage: (msg) => set((state) => ({ messages: [...state.messages, msg] })),
  setStreaming: (streamingText, isStreaming) => set({ streamingText, isStreaming }),
  setError: (errorMessage) => set({ errorMessage }),
  clearSession: () => set({ messages: [], streamingText: "", isStreaming: false, errorMessage: null }),
}));

export interface BackupState {
  schedule: "off" | "daily" | "weekly" | "monthly";
  includeMedia: boolean;
  driveConnected: boolean;
  lastBackupAtMs: number;
  lastBackupStatus: string;
  isBackupBusy: boolean;
  setSchedule: (schedule: "off" | "daily" | "weekly" | "monthly") => void;
  setIncludeMedia: (include: boolean) => void;
  setDriveConnected: (connected: boolean) => void;
  setLastBackup: (ms: number, status: string) => void;
  setBackupBusy: (busy: boolean) => void;
}

export const useBackupStore = create<BackupState>()(
  persist(
    (set) => ({
      schedule: "off",
      includeMedia: true,
      driveConnected: false,
      lastBackupAtMs: 0,
      lastBackupStatus: "No backup created yet",
      isBackupBusy: false,
      setSchedule: (schedule) => set({ schedule }),
      setIncludeMedia: (includeMedia) => set({ includeMedia }),
      setDriveConnected: (driveConnected) => set({ driveConnected }),
      setLastBackup: (lastBackupAtMs, lastBackupStatus) => set({ lastBackupAtMs, lastBackupStatus }),
      setBackupBusy: (isBackupBusy) => set({ isBackupBusy }),
    }),
    {
      name: "privacyx_backup_preferences",
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({
        schedule: state.schedule,
        includeMedia: state.includeMedia,
        driveConnected: state.driveConnected,
        lastBackupAtMs: state.lastBackupAtMs,
        lastBackupStatus: state.lastBackupStatus,
      }),
    }
  )
);
