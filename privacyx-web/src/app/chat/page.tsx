"use client";

import { useEffect, useRef, useState, useCallback } from "react";
import { Suspense } from "react";
import Image from "next/image";
import { useSearchParams, useRouter } from "next/navigation";
import { MessagingRepository } from "@/services/messagingRepository";
import { SocialRepository } from "@/services/socialRepository";
import { supabaseMediaService } from "@/services/supabaseMediaService";
import { localMediaRepository } from "@/services/localMediaRepository";
import { useAuthStore, useChatStore } from "@/stores/index";
import { UserAvatar, formatMessageTime } from "@/components/ui";
import { Mic, Image as ImageIcon, Send, MoreVertical, Play, Volume2, VolumeX } from "lucide-react";
import { cn, getErrorMessage } from "@/lib/utils";
import type { ChatMessage } from "@/types/index";

const messagingRepo = new MessagingRepository();
const socialRepo = new SocialRepository();

export default function ChatPage() {
  return <Suspense fallback={<div className="min-h-screen bg-[var(--px-bg)]" aria-label="Loading conversation" />}><ChatDetailPageContent /></Suspense>;
}

function ChatDetailPageContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const { user } = useAuthStore();
  const {
    activeConversation,
    setActiveConversation,
    messages,
    setMessages,
    addMessage,
    updateMessage,
  } = useChatStore();

  const [inputText, setInputText] = useState("");
  const [isRecording, setIsRecording] = useState(false);
  const [recordingTime, setRecordingTime] = useState(0);
  const [currentTime, setCurrentTime] = useState(0);
  const [replyTo, setReplyTo] = useState<{ id: string; text: string; senderName: string } | null>(null);
  const [infoOpen, setInfoOpen] = useState(false);
  const [audioMuted, setAudioMuted] = useState(false);
  const [localMediaUrls, setLocalMediaUrls] = useState<Record<string, string>>({});
  const scrollEndRef = useRef<HTMLDivElement>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);
  const playingAudioRef = useRef<HTMLAudioElement | null>(null);

  const conversationId = searchParams.get("conversationId") || "";

  useEffect(() => {
    const refreshTime = () => setCurrentTime(Date.now());
    const initialRefresh = window.setTimeout(refreshTime, 0);
    const refreshInterval = window.setInterval(refreshTime, 30_000);
    return () => { window.clearTimeout(initialRefresh); window.clearInterval(refreshInterval); };
  }, []);

  useEffect(() => {
    if (!conversationId || !user) return;
    const userId = user.userId;
    setActiveConversation(null);
    setMessages([]);
    let cancelled = false;
    let hasLiveMessages = false;
    void localMediaRepository.getCachedChatMessages(conversationId).then((cached) => {
      if (!cancelled && !hasLiveMessages && cached.length) setMessages(cached);
    }).catch(() => {});
    const unsubConversation = messagingRepo.observeConversation(conversationId)((conv) => {
      if (conv) setActiveConversation(conv);
    });

    const unsubMessages = messagingRepo.observeMessages(conversationId, 40)((msgs) => {
      hasLiveMessages = true;
      setMessages(msgs);
      void localMediaRepository.cacheChatMessages(msgs).catch(() => {});
      void messagingRepo.markMessagesRead(conversationId, msgs);
      setTimeout(() => scrollEndRef.current?.scrollIntoView({ behavior: "smooth" }), 100);
    });

    const unsubMember = messagingRepo.observeMemberState(conversationId, userId)((member) => {
      setInputText(member?.draftText || "");
    });

    return () => {
      cancelled = true;
      unsubMessages();
      unsubMember();
      unsubConversation();
    };
  }, [conversationId, user, setActiveConversation, setMessages]);

  useEffect(() => {
    let cancelled = false;
    const createdUrls: string[] = [];
    const loadLocalImages = async () => {
      const pairs = await Promise.all(messages
        .filter((message) => message.mediaType === "image" && message.mediaId)
        .map(async (message) => {
          try {
            let blob = await localMediaRepository.getMediaFile(message.mediaId);
            if (!blob && message.mediaUrl) {
              const response = await fetch(message.mediaUrl);
              if (!response.ok) return null;
              blob = await response.blob();
              await localMediaRepository.saveMediaFile(message.mediaId, blob, message.mimeType, message.fileName);
            }
            if (!blob || cancelled) return null;
            const url = URL.createObjectURL(blob);
            createdUrls.push(url);
            return [message.id, url] as const;
          } catch {
            return null;
          }
        }));
      if (!cancelled) setLocalMediaUrls(Object.fromEntries(pairs.filter((item): item is readonly [string, string] => item !== null)));
    };
    void loadLocalImages();
    return () => {
      cancelled = true;
      createdUrls.forEach((url) => URL.revokeObjectURL(url));
    };
  }, [messages]);

  const handleSendText = async () => {
    if (!inputText.trim() && !replyTo) return;
    if (!activeConversation || !user) return;

    const clientMsgId = `msg_${Math.random().toString(36).slice(2, 20)}`;
    const optimisticMsg = {
      id: clientMsgId,
      conversationId,
      senderId: user.userId,
      senderName: user.displayName || "User",
      senderAvatarUrl: user.photoUrl || "",
      memberUids: activeConversation.memberUids,
      isPublic: activeConversation.isPublic,
      text: inputText.trim(),
      mediaUrl: "",
      mediaId: "",
      mediaThumbnailUrl: "",
      mediaExpiresAt: 0,
      mediaExpired: false,
      mediaType: "text" as const,
      fileName: "",
      fileSizeBytes: 0,
      mimeType: "",
      durationMs: 0,
      replyToId: replyTo?.id || "",
      replyToPreview: replyTo?.text || "",
      forwardedFrom: "",
      topicId: "",
      isPinned: false,
      isDeleted: false,
      pollQuestion: "",
      pollOptions: [],
      pollVotes: {},
      reactions: {},
      readBy: [user.userId],
      deliveredTo: [user.userId],
      disappearsAt: null,
      scheduledFor: null,
      editedAt: null,
      createdAt: new Date(),
      updatedAt: new Date(),
    };

    addMessage(optimisticMsg);
    void localMediaRepository.cacheChatMessages([optimisticMsg]).catch(() => {});
    messagingRepo.updateMemberState({
      conversationId,
      memberUids: activeConversation.memberUids,
      draftText: "",
      typingNow: false,
    });
    setInputText("");
    setReplyTo(null);

    const result = await messagingRepo.sendMessage({
      conversation: activeConversation,
      senderName: user!.displayName || "User",
      senderAvatarUrl: user!.photoUrl || "",
      text: inputText.trim(),
      customMessageId: clientMsgId,
      replyToId: replyTo?.id || "",
      replyToPreview: replyTo?.text || "",
    });

    if (!result.success) {
      updateMessage(clientMsgId, { text: `[Failed to send: ${result.error || "unknown error"}]` });
      alert(result.error);
    }
  };

  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    try {
      const result = await supabaseMediaService.uploadMedia(file, conversationId, true);
      await localMediaRepository.saveMediaFile(result.mediaId, file, file.type, file.name).catch(() => {});

      if (activeConversation) {
        const clientMsgId = `msg_${Math.random().toString(36).slice(2, 20)}`;
        const mediaType: ChatMessage["mediaType"] = result.category === "image" ? "image" : result.category === "voice" ? "voice" : "document";

        await messagingRepo.sendMessage({
          conversation: activeConversation,
          senderName: user!.displayName || "User",
          senderAvatarUrl: user!.photoUrl || "",
          text: "",
          mediaType,
          mediaUrl: result.secureUrl,
          mediaId: result.mediaId,
          mediaThumbnailUrl: result.thumbnailUrl,
          mediaExpiresAt: result.expiresAtMs,
          fileName: result.fileName,
          mimeType: result.mimeType,
          customMessageId: clientMsgId,
        });

        await localMediaRepository.saveLocalMedia({
          mediaId: result.mediaId,
          messageId: clientMsgId,
          conversationId,
          mediaType: result.category,
          localFilePath: "",
          localThumbPath: "",
          remotePath: result.remotePath,
          mimeType: result.mimeType,
          fileName: result.fileName,
          sizeBytes: result.sizeBytes,
          durationMs: 0,
          createdAtMs: Date.now(),
          expiresAtMs: result.expiresAtMs,
          cloudDeleted: false,
          isRestoredFromBackup: false,
          downloadedToDevice: false,
          downloadedAtMs: 0,
        });
      }
    } catch (err) {
      alert(getErrorMessage(err, "Upload failed"));
    }
  };

  const startVoiceRecording = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      mediaRecorderRef.current = new MediaRecorder(stream);
      audioChunksRef.current = [];

      mediaRecorderRef.current.ondataavailable = (e) => {
        if (e.data.size > 0) audioChunksRef.current.push(e.data);
      };

      mediaRecorderRef.current.onstop = async () => {
        stream.getTracks().forEach((t) => t.stop());
        const audioBlob = new Blob(audioChunksRef.current, { type: "audio/wav" });
        const audioFile = new File([audioBlob], `voice_${Date.now()}.wav`, { type: "audio/wav" });

        setIsRecording(false);
        try {
          const result = await supabaseMediaService.uploadMedia(audioFile, conversationId, false);

          if (activeConversation) {
            const clientMsgId = `msg_${Math.random().toString(36).slice(2, 20)}`;
            await messagingRepo.sendMessage({
              conversation: activeConversation,
              senderName: user!.displayName || "User",
              senderAvatarUrl: user!.photoUrl || "",
              text: "",
              mediaType: "voice",
              mediaUrl: result.secureUrl,
              mediaId: result.mediaId,
              durationMs: recordingTime,
              fileName: result.fileName,
              mimeType: result.mimeType,
              customMessageId: clientMsgId,
            });
          }
        } catch (err) {
          alert(getErrorMessage(err, "Voice upload failed"));
        }
      };

      mediaRecorderRef.current.start();
      setIsRecording(true);
      const startTime = Date.now();
      const timer = setInterval(() => {
        setRecordingTime(Math.floor((Date.now() - startTime) / 1000));
      }, 1000);

      setTimeout(() => {
        if (mediaRecorderRef.current?.state === "recording") {
          mediaRecorderRef.current?.stop();
        }
        clearInterval(timer);
      }, 300000);

      return () => clearInterval(timer);
    } catch {
      alert("Microphone access required for voice messages.");
    }
  };

  const stopVoiceRecording = () => {
    if (mediaRecorderRef.current?.state === "recording") {
      mediaRecorderRef.current.stop();
    }
    setIsRecording(false);
    setRecordingTime(0);
  };

  const cancelVoiceRecording = () => {
    if (mediaRecorderRef.current?.state === "recording") {
      mediaRecorderRef.current.stop();
      mediaRecorderRef.current.stream?.getTracks?.().forEach((t) => t.stop());
    }
    setIsRecording(false);
    setRecordingTime(0);
  };

  const toggleReaction = async (msgId: string, emoji: string) => {
    await messagingRepo.toggleReaction(conversationId, msgId, emoji);
  };

  const togglePin = async (msgId: string, previewText: string, isPinned: boolean) => {
    await messagingRepo.togglePinMessage(conversationId, msgId, previewText, !isPinned);
  };

  const deleteMessage = async (msgId: string) => {
    const result = await messagingRepo.deleteMessage(conversationId, msgId);
    if (!result.success) alert(result.error || "Could not delete message.");
  };

  const editMessage = async (msgId: string, currentText: string) => {
    const nextText = window.prompt("Edit message", currentText);
    if (nextText === null || nextText.trim() === currentText.trim()) return;
    if (!nextText.trim()) { alert("A message cannot be empty."); return; }
    const result = await messagingRepo.editMessage(conversationId, msgId, nextText.trim());
    if (!result.success) alert(result.error || "Could not edit message.");
  };

  const saveMessage = async (msg: (typeof messages)[number]) => {
    const result = await socialRepo.saveMessageBookmark({
      content: msg.text || msg.fileName || (msg.mediaType === "image" ? "Photo" : "Voice message"),
      sourceConversationId: conversationId,
      sourceSenderName: msg.senderName,
      mediaUrl: msg.mediaUrl,
      mediaType: msg.mediaType,
    });
    if (!result.success) alert(result.error || "Could not save message.");
    else alert("Message saved.");
  };

  const createPoll = async () => {
    if (!activeConversation || !user) return;
    const question = window.prompt("Poll question");
    if (!question?.trim()) return;
    const rawOptions = window.prompt("Enter 2–10 options separated by commas");
    if (!rawOptions) return;
    const options = [...new Set(rawOptions.split(",").map((option) => option.trim()).filter(Boolean))].slice(0, 10);
    if (options.length < 2) { alert("A poll needs at least two different options."); return; }
    const result = await messagingRepo.sendMessage({
      conversation: activeConversation,
      senderName: user.displayName || "User",
      senderAvatarUrl: user.photoUrl || "",
      text: "",
      mediaType: "poll",
      pollQuestion: question.trim().slice(0, 240),
      pollOptions: options.map((option) => option.slice(0, 100)),
    });
    if (!result.success) alert(result.error || "Could not create poll.");
  };

  const forwardMessage = async (msg: (typeof messages)[number]) => {
    if (!activeConversation || !user || !msg.text || msg.isDeleted) return;
    const result = await messagingRepo.sendMessage({
      conversation: activeConversation,
      senderName: user.displayName || "User",
      senderAvatarUrl: user.photoUrl || "",
      text: msg.text,
      forwardedFrom: msg.senderName,
    });
    if (!result.success) alert(result.error || "Could not forward message.");
  };

  const handleTyping = useCallback((e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const nextText = e.target.value;
    setInputText(nextText);
    messagingRepo.updateMemberState({
      conversationId,
      memberUids: activeConversation?.memberUids || [],
      draftText: nextText,
      typingNow: Boolean(nextText.trim()),
    });
  }, [conversationId, activeConversation]);

  if (!activeConversation) {
    return (
    <div className="flex h-dvh min-h-0 flex-col bg-white dark:bg-gray-900">
        <div className="h-16 border-b flex items-center justify-between px-6 bg-white/80 dark:bg-gray-900/80 backdrop-blur">
          <h1 className="text-lg font-bold text-gray-900 dark:text-white">Loading chat...</h1>
        </div>
        <div className="flex-1 flex items-center justify-center">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-violet-600"></div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-dvh min-h-0 flex-col theme-transition bg-orbs relative overflow-hidden">
      <div className="absolute inset-0 bg-gradient-to-b from-[var(--px-bg)] to-[var(--px-bg-secondary)] pointer-events-none" />
      {/* Header */}
      <div className="h-16 border-b border-[var(--px-border)] bg-[var(--px-surface)] backdrop-blur-lg shrink-0 flex items-center justify-between px-4 z-10 sticky top-0 relative z-20">
        <div className="flex items-center gap-3 cursor-pointer" onClick={() => router.push("/")}>
          <UserAvatar name={activeConversation.title} photoUrl={activeConversation.photoUrl} size={40} />
          <div className="flex-1 min-w-0">
            <h1 className="font-semibold text-[var(--px-text-primary)] truncate">{activeConversation.title}</h1>
            <p className="text-xs text-[var(--px-text-muted)] truncate">
              {activeConversation.memberUids.length} members • {activeConversation.type}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button aria-label="Conversation details" onClick={() => setInfoOpen(true)} className="p-2 rounded-xl hover:bg-[var(--px-accent-glass)] text-[var(--px-text-secondary)] transition-all border border-transparent hover:border-white/30 dark:hover:border-gray-600/50">
            <MoreVertical className="w-5 h-5" />
          </button>
          <button aria-label="Conversation details" onClick={() => setInfoOpen(true)} className="p-2 rounded-xl hover:bg-[var(--px-accent-glass)] text-[var(--px-text-secondary)] transition-all border border-transparent hover:border-white/30 dark:hover:border-gray-600/50">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </button>
        </div>
      </div>

      {infoOpen && <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-4" onMouseDown={(event) => { if (event.target === event.currentTarget) setInfoOpen(false); }}>
        <section role="dialog" aria-modal="true" aria-label="Conversation details" className="w-full max-w-sm rounded-2xl border border-white/40 bg-white p-5 shadow-2xl dark:bg-gray-900">
          <div className="flex items-start justify-between gap-3"><div><h2 className="text-lg font-bold">{activeConversation.title}</h2><p className="mt-1 text-sm text-[var(--px-text-secondary)]">{activeConversation.type} · {activeConversation.memberUids.length} members</p></div><button onClick={() => setInfoOpen(false)} className="rounded-lg px-3 py-1 text-sm hover:bg-gray-100 dark:hover:bg-gray-800">Close</button></div>
          {activeConversation.description && <p className="mt-4 text-sm leading-6 text-[var(--px-text-secondary)]">{activeConversation.description}</p>}
        </section>
      </div>}

      {/* Messages */}
      <div className="min-h-0 flex-1 overflow-y-auto p-3 pb-6 sm:p-4 sm:pb-6 space-y-4 relative z-10" ref={scrollEndRef}>
        {messages.length === 0 && (
          <div className="flex flex-col items-center justify-center h-full text-center">
            <div className="w-16 h-16 rounded-full bg-white/40 dark:bg-gray-800/40 mb-4 flex items-center justify-center">
              <span className="text-3xl">💬</span>
            </div>
            <h3 className="text-lg font-medium text-[var(--px-text-primary)]">Start the conversation</h3>
            <p className="text-[var(--px-text-muted)]">Say hello to {activeConversation.title}</p>
          </div>
        )}

        {messages.map((msg) => {
          const isMine = msg.senderId === user?.userId;
          const isDeleted = msg.isDeleted;
          const isVoice = msg.mediaType === "voice";
          const hasMedia = msg.mediaType === "image" || msg.mediaType === "voice" || msg.mediaType === "document";
          const isExpired = msg.mediaExpired || (msg.mediaExpiresAt > 0 && currentTime > 0 && msg.mediaExpiresAt <= currentTime);

          return (
            <div
              key={msg.id}
              className={`flex ${isMine ? "justify-end" : "justify-start"} animate-[fadeIn_0.3s_ease-out]`}
            >
              {isMine ? null : (
                <div className="flex items-end gap-2">
                  <UserAvatar name={msg.senderName} photoUrl={msg.senderAvatarUrl} size={28} />
                  <span className="text-[10px] text-[var(--px-text-muted)] mt-1">{msg.senderName}</span>
                </div>
              )}
              <div className={cn(
                "max-w-[80%] rounded-2xl p-3 shadow-sm border break-words relative group",
                isMine
                  ? "bg-gradient-to-br from-violet-600 via-violet-600 to-pink-500 text-white border-br-violet-400/30"
                  : "bg-[var(--px-surface)] border-[var(--px-border)] text-[var(--px-text-primary)]"
              )}>
                {replyTo && (
                  <div className="absolute -top-4 left-2 right-2 flex gap-2 px-2 py-1 bg-white/90 dark:bg-gray-800/90 rounded-lg border border-white/30 dark:border-gray-700 text-[10px]">
                    <span className="font-semibold text-[var(--px-text-secondary)]">Replying to {replyTo.senderName}</span>
                    <span className="text-[var(--px-text-muted)] truncate max-w-[200px]">{replyTo.text}</span>
                    <button
                      onClick={() => setReplyTo(null)}
                      className="text-[var(--px-text-muted)] hover:text-[var(--px-text-primary)]"
                    >
                      ✕
                    </button>
                  </div>
                )}

                {msg.forwardedFrom && <p className="mb-1 text-[10px] italic opacity-70">Forwarded from {msg.forwardedFrom}</p>}
                {msg.mediaType === "poll" && !isDeleted ? (
                  <div className="min-w-56 space-y-2">
                    <p className="font-semibold">{msg.pollQuestion || "Poll"}</p>
                    {msg.pollOptions.map((option) => {
                      const voters = msg.pollVotes?.[option] || [];
                      const voted = Boolean(user && voters.includes(user.userId));
                      return <button key={option} onClick={() => void messagingRepo.votePoll(conversationId, msg.id, option)} className={cn("flex w-full items-center justify-between rounded-lg border px-3 py-2 text-left text-sm", voted ? "border-violet-400 bg-violet-500/15" : "border-current/20 hover:bg-violet-500/10")}><span>{option}</span><span className="text-xs opacity-70">{voters.length}</span></button>;
                    })}
                  </div>
                ) : isDeleted ? (
                  <span className="text-xs italic text-[var(--px-text-muted)]">This message was deleted</span>
                ) : hasMedia ? (
                  <>
                    {msg.mediaType === "image" && (localMediaUrls[msg.id] || (!isExpired && msg.mediaUrl)) ? (
                      <Image
                        src={localMediaUrls[msg.id] || msg.mediaUrl}
                        alt="Shared image"
                        width={200}
                        height={200}
                        unoptimized
                        className="max-w-[200px] max-h-[200px] rounded-xl object-cover cursor-pointer"
                        onClick={() => window.open(localMediaUrls[msg.id] || msg.mediaUrl, "_blank")}
                      />
                    ) : msg.mediaType === "image" ? (
                      <div className="flex flex-col items-center justify-center p-4 rounded-xl border-2 border-dashed border-white/30 dark:border-gray-600 bg-gray-50 dark:bg-gray-800/50">
                        <span className="text-lg">🖼️</span>
                        <span className="text-xs text-[var(--px-text-muted)] mt-1">Media unavailable</span>
                      </div>
                    ) : null}

                    {isVoice && msg.mediaUrl ? (
                      <div className="flex items-center gap-3 mt-2 bg-white/20 dark:bg-gray-800/50 rounded-lg px-3 py-2 border border-white/30 dark:border-gray-600">
                        <button
                          onClick={() => {
                            playingAudioRef.current?.pause();
                            const audio = new Audio(msg.mediaUrl);
                            audio.muted = audioMuted;
                            playingAudioRef.current = audio;
                            void audio.play().catch((error) => alert(`Could not play audio: ${error.message}`));
                          }}
                          className="p-1.5 rounded-full hover:bg-white/30 dark:hover:bg-gray-700"
                        >
                          <Play className="w-4 h-4 text-[var(--px-text-secondary)]" />
                        </button>
                        <div className="flex-1">
                          <div className="flex justify-between text-[10px] text-[var(--px-text-muted)] mb-1">
                            <span>Voice message</span>
                            <span>{recordingTime}s</span>
                          </div>
                          <div className="w-full h-1 bg-white/30 dark:bg-gray-600 rounded-full overflow-hidden">
                            <div className="h-full bg-violet-600 w-1/3" />
                          </div>
                        </div>
                        <button
                          aria-label={audioMuted ? "Unmute voice message" : "Mute voice message"}
                          onClick={() => {
                            const nextMuted = !audioMuted;
                            if (playingAudioRef.current) playingAudioRef.current.muted = nextMuted;
                            setAudioMuted(nextMuted);
                          }}
                          className="text-[var(--px-text-muted)] hover:text-[var(--px-text-primary)]"
                        >
                          {audioMuted ? <VolumeX className="w-4 h-4" /> : <Volume2 className="w-4 h-4" />}
                        </button>
                      </div>
                    ) : null}

                    {msg.mediaType === "document" && !isExpired ? (
                      <div className="flex items-center gap-3 mt-2 bg-blue-500/10 dark:bg-blue-900/20 rounded-lg px-3 py-2 border border-blue-500/30 dark:border-blue-800 text-blue-900 dark:text-blue-100">
                        <ImageIcon className="w-5 h-5" />
                        <div className="text-sm truncate">{msg.fileName || "Document"}</div>
                        <button
                          onClick={() => window.open(msg.mediaUrl, "_blank")}
                          className="text-xs underline hover:text-blue-700"
                        >
                          Download
                        </button>
                      </div>
                    ) : null}

                    {!isVoice && msg.mediaType !== "image" && msg.mediaType !== "document" && msg.text ? (
                      <p className="text-sm leading-relaxed">{msg.text}</p>
                    ) : null}

                    {msg.text && !isVoice && !hasMedia ? (
                      <p className="text-sm leading-relaxed">{msg.text}</p>
                    ) : null}
                  </>
                ) : (
                  msg.text ? (
                    <p className="text-sm leading-relaxed">{msg.text}</p>
                  ) : null
                )}

                <div className="flex items-center justify-between mt-2 pt-2 border-t border-current/20">
                  <span className="text-[10px] text-current/60">{formatMessageTime(msg.createdAt)}</span>
                  <div className="flex items-center gap-1">
                    {msg.readBy.includes(user!.userId) ? (
                      <span className="text-[10px] text-blue-500">✓✓</span>
                    ) : (
                      <span className="text-[10px] text-[var(--px-text-muted)]">✓</span>
                    )}
                      <div className="relative group flex flex-wrap justify-end gap-0.5">
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          setReplyTo({
                            id: msg.id,
                            text: msg.text.slice(0, 100),
                            senderName: msg.senderName,
                          });
                        }}
                        className="min-h-8 min-w-8 p-1 opacity-100 transition-opacity text-[var(--px-text-muted)] hover:text-violet-600 dark:hover:text-violet-400"
                      >
                        ↩
                      </button>
                      <button
                        onClick={() => toggleReaction(msg.id, "❤️")}
                        className="min-h-8 min-w-8 p-1 opacity-100 transition-opacity text-[var(--px-text-muted)] hover:text-rose-500 dark:hover:text-rose-400"
                      >
                        ❤️
                      </button>
                      <button
                        onClick={() => togglePin(msg.id, msg.text.slice(0, 50), msg.isPinned)}
                        className="min-h-8 min-w-8 p-1 opacity-100 transition-opacity text-[var(--px-text-muted)] hover:text-amber-500 dark:hover:text-amber-400"
                      >
                        📌
                      </button>
                      <button aria-label="Save message" title="Save message" onClick={() => void saveMessage(msg)} className="min-h-8 min-w-8 p-1 opacity-100 transition-opacity text-[var(--px-text-muted)] hover:text-violet-500">☆</button>
                      {msg.text && <button aria-label="Copy message" title="Copy message" onClick={() => void navigator.clipboard.writeText(msg.text).catch(() => alert("Clipboard access is unavailable."))} className="min-h-8 min-w-8 p-1 opacity-100 transition-opacity text-[var(--px-text-muted)] hover:text-violet-500">⧉</button>}
                      {msg.text && !isDeleted ? <button aria-label="Forward message" title="Forward message in this chat" onClick={() => void forwardMessage(msg)} className="min-h-8 min-w-8 p-1 opacity-100 transition-opacity text-[var(--px-text-muted)] hover:text-violet-500">↗</button> : null}
                      {isMine && !isDeleted && msg.mediaType === "text" ? <button aria-label="Edit message" title="Edit message" onClick={() => void editMessage(msg.id, msg.text)} className="min-h-8 min-w-8 p-1 opacity-100 transition-opacity text-[var(--px-text-muted)] hover:text-violet-500">✎</button> : null}
                      {isMine && !isDeleted ? (
                        <button
                          onClick={() => deleteMessage(msg.id)}
                          aria-label="Delete your message"
                          title="Delete your message"
                          className="min-h-8 min-w-8 p-1 opacity-100 transition-opacity text-[var(--px-text-muted)] hover:text-rose-500 dark:hover:text-rose-400"
                        >
                          🗑️
                        </button>
                      ) : null}
                    </div>
                  </div>
                </div>
              </div>
              {isMine ? (
                <div className="flex items-end gap-2">
                  <span className="text-[10px] text-[var(--px-text-muted)] mt-1">{msg.readBy.length > 1 ? "✓✓ Read" : "✓ Sent"}</span>
                </div>
              ) : null}
            </div>
          );
        })}
      </div>

      {/* Voice recording overlay */}
      {isRecording && (
        <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur flex flex-col items-center justify-center">
          <div className="flex flex-col items-center gap-4 bg-white/90 dark:bg-gray-800/90 p-6 rounded-3xl border border-white/30 dark:border-gray-600 shadow-2xl">
            <div className="w-16 h-16 rounded-full bg-rose-500 flex items-center justify-center animate-pulse">
              <Mic className="w-8 h-8 text-white" />
            </div>
            <div className="text-xl font-mono font-bold text-gray-900 dark:text-white">{recordingTime}s</div>
            <button
              onClick={stopVoiceRecording}
              className="w-14 h-14 rounded-full bg-violet-600 text-white flex items-center justify-center shadow-xl active:scale-95"
            >
              <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24"><path d="M6 19h12v2H6v-2zM8 9h8v1H8V9zM12 4a9 9 0 00-9 9c0 2.4 1.0 4.6 2.6 6.2l-2.3 4.1c-.2.4-.2.9 0 1.3l2.5 4.5c.3.4.7.6 1.1.6h10c.4 0 .8-.2 1.1-.6l2.5-4.5c.2-.4.2-.9 0-1.3l-2.3-4.1C21.1 14.6 22 12.4 22 10c0-4.9-4-8.9-9-8.9z"/></svg>
            </button>
            <button
              onClick={cancelVoiceRecording}
              className="text-sm text-gray-500 dark:text-gray-400 hover:text-rose-500 dark:hover:text-rose-400"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Composer */}
      <div className="shrink-0 border-t border-[var(--px-border)] bg-[var(--px-surface)] p-3 pb-[calc(0.75rem+env(safe-area-inset-bottom))] backdrop-blur-lg sm:p-4 relative z-20">
        <div className="flex items-center gap-2 sm:gap-3">
          {isRecording ? null : (
            <>
              <button type="button" onClick={() => void createPoll()} title="Create poll" aria-label="Create poll" className="p-2 rounded-xl hover:bg-[var(--px-accent-glass)] text-[var(--px-text-secondary)]">▤</button>
              <label className="cursor-pointer p-2 rounded-xl hover:bg-[var(--px-accent-glass)] text-[var(--px-text-secondary)] transition-all border border-transparent hover:border-white/30 dark:hover:border-gray-600/50">
                <input type="file" accept="image/*,.pdf,.doc,.docx,.txt,.zip" onChange={handleFileSelect} className="hidden" />
                <ImageIcon className="w-5 h-5" />
              </label>
              <button
                onClick={startVoiceRecording}
                className="p-2.5 rounded-full bg-rose-500 text-white hover:bg-rose-600 shadow-lg transition-all active:scale-95"
              >
                <Mic className="w-5 h-5" />
              </button>
            </>
          )}

          <div className="relative min-w-0 flex-1">
            <textarea
              value={inputText}
              onChange={handleTyping}
              placeholder={replyTo ? `Replying to ${replyTo.senderName}...` : "Type a message..."}
              rows={1}
              maxLength={4000}
              className="w-full resize-none py-2 px-4 rounded-full border border-[var(--px-border)] bg-transparent text-[var(--px-text-primary)] focus:outline-none focus:ring-2 focus:ring-violet-500/50 placeholder-[var(--px-text-muted)]"
              style={{ minHeight: "44px", fontSize: "15px" }}
            />
            {replyTo && (
              <button
                onClick={() => setReplyTo(null)}
                className="absolute right-3 top-2 text-[var(--px-text-muted)] hover:text-[var(--px-text-primary)]"
              >
                ✕
              </button>
            )}
          </div>

          <button
            onClick={handleSendText}
            disabled={!inputText.trim() && !replyTo}
            className={cn(
              "p-2.5 rounded-full transition-all active:scale-95 flex items-center justify-center",
              !inputText.trim() && !replyTo
                ? "bg-white/30 dark:bg-gray-800/30 text-[var(--px-text-muted)] cursor-not-allowed"
                : "bg-violet-600 text-white hover:bg-violet-700 shadow-lg"
            )}
          >
            <Send className="w-5 h-5" />
          </button>
        </div>
      </div>
    </div>
  );
}
