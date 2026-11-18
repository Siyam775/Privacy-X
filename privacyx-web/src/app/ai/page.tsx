"use client";

import { useEffect, useState, useRef, useCallback } from "react";
import Image from "next/image";
import { AiAssistantRepository } from "@/services/aiAssistantRepository";
import { useAuthStore, useAIStore } from "@/stores/index";
import { formatMessageTime, getErrorMessage } from "@/lib/utils";

const aiRepo = new AiAssistantRepository();

export default function AIPage() {
  const { user } = useAuthStore();
  const {
    sessions,
    activeSessionId,
    messages,
    streamingText,
    isStreaming,
    errorMessage,
    setSessions,
    setActiveSession,
    setMessages,
    addMessage,
    setStreaming,
    setError,
  } = useAIStore();

  const [inputText, setInputText] = useState("");
  const [newSessionTitle, setNewSessionTitle] = useState("");
  const [attachedImage, setAttachedImage] = useState<string | null>(null);
  const [isCreatingSession, setIsCreatingSession] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const loadMessages = useCallback((sessionId: string) => {
    if (!user) return;
    const unsub = aiRepo.observeAiMessages(user.userId, sessionId)((msgs) => {
      setMessages(msgs);
      setTimeout(() => messagesEndRef.current?.scrollIntoView({ behavior: "smooth" }), 100);
    });
    return unsub;
  }, [user, setMessages]);

  useEffect(() => {
    if (!user) return;
    return aiRepo.observeAiSessions(user.userId)((nextSessions) => {
      setSessions(nextSessions);
      if (nextSessions.length > 0 && !activeSessionId) setActiveSession(nextSessions[0].id);
    });
  }, [user, activeSessionId, setSessions, setActiveSession]);

  useEffect(() => {
    if (!activeSessionId || !user) return;
    const unsub = loadMessages(activeSessionId);
    return () => { unsub?.(); };
  }, [activeSessionId, user, loadMessages]);

  const createSession = async () => {
    if (!user) return;
    setIsCreatingSession(true);
    try {
      const sessionId = await aiRepo.createAiSession(newSessionTitle || "New AI Chat");
      setActiveSession(sessionId);
      setNewSessionTitle("");
      setIsCreatingSession(false);
    } catch (err) {
      setIsCreatingSession(false);
      alert(getErrorMessage(err, "Failed to create session"));
    }
  };

  const sendMessage = async () => {
    if (!inputText.trim() && !attachedImage) return;
    if (!user || !activeSessionId) return;

    const userMsg = {
      id: `msg_${Date.now()}`,
      userId: user.userId,
      role: "user" as const,
      text: inputText,
      createdAt: new Date(),
    };

    addMessage(userMsg);
    setInputText("");
    setAttachedImage(null);
    setStreaming("", true);
    setError(null);

    await aiRepo.saveAiTurn(activeSessionId, "user", inputText);

    try {
      await aiRepo.streamPrompt({
        prompt: inputText,
        history: messages,
        attachedImage,
        onChunk: (text) => {
          setStreaming(text, true);
        },
        onComplete: (fullText) => {
          const assistantMsg = {
            id: `msg_${Date.now()}_2`,
            userId: user.userId,
            role: "assistant" as const,
            text: fullText,
            createdAt: new Date(),
          };
          addMessage(assistantMsg);
          void aiRepo.saveAiTurn(activeSessionId, "assistant", fullText);
          setStreaming("", false);
        },
        onError: (err) => {
          setError(err.message);
          setStreaming("", false);
        },
      });
    } catch (err) {
      setError(getErrorMessage(err, "AI request failed"));
      setStreaming("", false);
    }
  };

  const handleImageSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = () => setAttachedImage(reader.result as string);
      reader.readAsDataURL(file);
    }
  };

  return (
    <div className="flex h-dvh min-h-0 flex-col theme-transition">
      {/* Header */}
      <div className="h-16 border-b border-white/30 dark:border-gray-700 bg-white/70 dark:bg-gray-900/70 backdrop-blur shrink-0 flex items-center justify-between px-4 z-10 sticky top-0">
        <div className="flex items-center gap-3 cursor-pointer">
          <div className="w-10 h-10 rounded-full bg-violet-500/20 flex items-center justify-center text-violet-600 dark:text-violet-400 font-bold">
            ✨
          </div>
          <div>
            <h1 className="font-semibold text-gray-900 dark:text-white">AI Assistant</h1>
            <p className="text-xs text-gray-500 dark:text-gray-400">Powered by Gemini</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {isCreatingSession ? (
            <button className="p-2 rounded-full bg-gray-200 dark:bg-gray-700 disabled:opacity-50">
              <svg className="w-5 h-5 animate-spin" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
            </button>
          ) : (
            <button
              onClick={() => setIsCreatingSession(true)}
              className="p-2 rounded-full hover:bg-white/30 dark:hover:bg-gray-800 text-gray-600 dark:text-gray-300 transition-colors"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 4v16m8-8H4" />
              </svg>
            </button>
          )}
        </div>
      </div>

      {/* Create Session Modal */}
      {isCreatingSession && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur flex items-center justify-center p-4">
          <div className="max-h-[calc(100dvh-2rem)] w-full max-w-md overflow-y-auto rounded-2xl border border-white/80 bg-white/95 p-4 backdrop-blur dark:border-gray-700 dark:bg-gray-900/95 sm:p-6">
            <h3 className="text-lg font-bold text-gray-900 dark:text-white mb-4">New AI Session</h3>
            <input
              value={newSessionTitle}
              onChange={(e) => setNewSessionTitle(e.target.value)}
              placeholder="Session title..."
              className="w-full p-3 rounded-xl border border-white/30 dark:border-gray-700 bg-transparent text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-violet-500 mb-4"
            />
            <div className="flex gap-3">
              <button
                onClick={() => setIsCreatingSession(false)}
                className="flex-1 py-3 rounded-xl text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={createSession}
                className="flex-1 py-3 rounded-xl bg-violet-600 text-white font-bold hover:bg-violet-700 transition-colors shadow-lg"
              >
                Create
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="flex min-h-0 flex-1 flex-col sm:flex-row">
        {/* Sessions Sidebar */}
        <div className="w-full shrink-0 border-b border-white/20 bg-white/60 backdrop-blur dark:border-gray-700 dark:bg-gray-900/60 sm:w-72 sm:border-b-0 sm:border-r">
          <div className="p-3 sm:p-4">
            <h2 className="mb-2 text-sm font-semibold text-gray-900 dark:text-white sm:mb-4">Sessions</h2>
            <div className="flex gap-2 overflow-x-auto pb-1 sm:flex-col sm:overflow-visible sm:pb-0">
              {sessions.map((session) => (
                <button
                  key={session.id}
                  onClick={() => {
                    setActiveSession(session.id);
                  }}
                  className={cn(
                    "flex min-w-0 max-w-56 shrink-0 items-center gap-3 rounded-xl px-3 py-2 text-left transition-colors sm:max-w-none",
                    activeSessionId === session.id
                      ? "bg-violet-600 text-white"
                      : "text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700"
                  )}
                >
                  <div className="w-6 h-6 rounded-full flex items-center justify-center shrink-0 text-xs">
                    #{(sessions.indexOf(session) + 1) % 6 === 0 ? "⚫" : "🔵"}
                  </div>
                  <span className="text-sm truncate">{session.title}</span>
                </button>
              ))}
              {sessions.length === 0 && (
                <div className="text-xs text-gray-400 text-center py-4">No sessions yet</div>
              )}
            </div>
            <div className="mt-2 sm:mt-4">
              <button
                onClick={() => setIsCreatingSession(true)}
                className="w-full py-2 rounded-lg text-xs font-medium text-violet-600 dark:text-violet-400 hover:bg-violet-50 dark:hover:bg-violet-900/20"
              >
                + New Session
              </button>
            </div>
          </div>
        </div>

        {/* Chat Area */}
        <div className="flex min-h-0 min-w-0 flex-1 flex-col bg-gradient-to-br from-[var(--px-bg)] via-[var(--px-bg-secondary)] to-[var(--px-bg)]">
          {/* Messages */}
          <div className="min-h-0 flex-1 overflow-y-auto p-3 sm:p-6" ref={messagesEndRef}>
            {messages.length === 0 && (
              <div className="flex flex-col items-center justify-center h-full text-center">
                <div className="w-20 h-20 rounded-full bg-white/40 dark:bg-gray-800/40 mb-6 flex items-center justify-center">
                  <span className="text-4xl">✨</span>
                </div>
                <h3 className="text-lg font-medium text-gray-900 dark:text-white">Ask me anything</h3>
                <p className="text-gray-500 dark:text-gray-400 mt-2 max-w-xs mx-auto">
                  I can help you with writing, analysis, coding, and more. I can also analyze images you attach.
                </p>
              </div>
            )}

            {messages.map((msg) => (
              <div key={msg.id} className={`mb-6 ${msg.role === "user" ? "justify-end" : "justify-start"} flex`}>
                {msg.role === "user" ? null : (
                  <div className="flex items-start gap-2">
                    <div className="w-8 h-8 rounded-full bg-violet-500/20 flex items-center justify-center text-violet-600 dark:text-violet-400 font-bold text-xs">
                      ✨
                    </div>
                  </div>
                )}
                <div className={cn(
                  "max-w-[80%] rounded-2xl p-4 shadow-sm border break-words",
                  msg.role === "user"
                    ? "bg-gradient-to-br from-violet-600 to-pink-500 text-white text-right"
                    : "bg-white/70 dark:bg-gray-800/60 border border-white/30 dark:border-gray-700 text-gray-900 dark:text-white"
                )}>
                  {msg.role === "assistant" && isStreaming && streamingText ? (
                    <span className="animate-pulse">{streamingText}<span className="text-xs">█</span></span>
                  ) : (
                    <span className="text-sm leading-relaxed">{msg.text}</span>
                  )}
                </div>
                {msg.role === "user" ? (
                  <div className="flex items-end gap-2">
                    <span className="text-[10px] text-gray-400 dark:text-gray-500 mt-1">{formatMessageTime(msg.createdAt)}</span>
                  </div>
                ) : null}
              </div>
            ))}
            {isStreaming && <div className="mb-6 flex justify-start"><div className="max-w-[80%] rounded-2xl border border-white/30 bg-white/70 p-4 text-sm leading-relaxed text-gray-900 shadow-sm dark:border-gray-700 dark:bg-gray-800/60 dark:text-white">{streamingText || "Thinking…"}<span className="ml-1 animate-pulse">▌</span></div></div>}
            {errorMessage && (
              <div className="mb-6 flex justify-start">
                <div className="bg-rose-50 dark:bg-rose-900/20 border border-rose-200 dark:border-rose-800 text-rose-700 dark:text-rose-300 rounded-xl p-3 text-sm">
                  {errorMessage}
                </div>
              </div>
            )}
          </div>

          {/* Input */}
          <div className="shrink-0 border-t border-white/30 bg-white/80 p-3 pb-[calc(0.75rem+env(safe-area-inset-bottom))] backdrop-blur dark:border-gray-700 dark:bg-gray-900/80 sm:p-4">
            <div className="flex items-center gap-3">
              {attachedImage && (
                <div className="relative">
                  <Image src={attachedImage} alt="Attached image preview" width={48} height={48} unoptimized className="h-12 w-12 rounded-lg object-cover" />
                  <button
                    onClick={() => setAttachedImage(null)}
                    className="absolute -right-1 -top-1 w-5 h-5 rounded-full bg-red-500 text-white flex items-center justify-center text-xs"
                  >
                    ✕
                  </button>
                </div>
              )}
              <label className="cursor-pointer p-2 rounded-full hover:bg-white/30 dark:hover:bg-gray-800 text-gray-600 dark:text-gray-300">
                <input type="file" accept="image/*" onChange={handleImageSelect} className="hidden" />
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                </svg>
              </label>
              <div className="relative min-w-0 flex-1">
                <textarea
                  value={inputText}
                  onChange={(e) => setInputText(e.target.value)}
                  placeholder="Ask AI anything..."
                  rows={1}
                  maxLength={4000}
                  className="w-full resize-none py-2 px-4 rounded-full border border-white/30 dark:border-gray-700 bg-transparent text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-violet-500/50 placeholder-gray-500 dark:placeholder-gray-400"
                  style={{ minHeight: "44px", fontSize: "15px" }}
                />
              </div>
              <button
                onClick={sendMessage}
                disabled={(!inputText.trim() && !attachedImage) || isStreaming || !activeSessionId}
                className={cn(
                  "p-2.5 rounded-full transition-all active:scale-95 flex items-center justify-center",
                  (!inputText.trim() && !attachedImage) || isStreaming || !activeSessionId
                    ? "bg-gray-100 dark:bg-gray-800 text-gray-400 dark:text-gray-500 cursor-not-allowed"
                    : "bg-violet-600 text-white hover:bg-violet-700 shadow-lg"
                )}
              >
                {isStreaming ? (
                  <svg className="w-5 h-5 animate-spin" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                ) : (
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 4v16m8-8H4" />
                  </svg>
                )}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function cn(...classes: (string | undefined | null | boolean)[]) {
  return classes.filter(Boolean).join(" ");
}
