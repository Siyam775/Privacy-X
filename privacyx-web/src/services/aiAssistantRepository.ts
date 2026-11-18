"use client";
import { normalizeFirestoreValue } from "@/lib/utils";

import {
  db,
  auth,
  doc,
  collection,
  query,
  orderBy,
  limit,
  onSnapshot,
  setDoc,
  serverTimestamp,
  batch,
} from "@/lib/firebase";
import { AiConversation, AiMessage } from "@/types/index";
import type { DocumentData } from "firebase/firestore";

export class AiAssistantRepository {
  observeAiSessions(userId: string): (callback: (sessions: AiConversation[]) => void) => () => void {
    return (callback) => {
      const q = query(
        collection(db, "users", userId, "aiConversations"),
        orderBy("updatedAt", "desc"),
        limit(30)
      );
      const unsubscribe = onSnapshot(q, (snap) => {
        callback(snap.docs.map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as AiConversation));
      });
      return unsubscribe;
    };
  }

  observeAiMessages(userId: string, sessionId: string): (callback: (messages: AiMessage[]) => void) => () => void {
    return (callback) => {
      const q = query(
        collection(db, "users", userId, "aiConversations", sessionId, "messages"),
        orderBy("createdAt", "asc"),
        limit(100)
      );
      const unsubscribe = onSnapshot(q, (snap) => {
        callback(snap.docs.map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as AiMessage));
      });
      return unsubscribe;
    };
  }

  async createAiSession(title: string = "New AI Chat"): Promise<string> {
    const uid = authCurrentUserId();
    if (!uid) throw new Error("User must be signed in.");
    const sessionId = `ai_${Math.random().toString(36).slice(2, 18)}`;
    await setDoc(doc(db, "users", uid, "aiConversations", sessionId), {
      userId: uid,
      title: title.trim() || "AI Assistant",
      lastPrompt: "",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
    return sessionId;
  }

  async saveAiTurn(sessionId: string, role: string, text: string): Promise<void> {
    const uid = authCurrentUserId();
    if (!uid) return;
    const sessionRef = doc(db, "users", uid, "aiConversations", sessionId);
    const msgId = `aim_${Math.random().toString(36).slice(2, 18)}`;
    const msgRef = doc(db, "users", uid, "aiConversations", sessionId, "messages", msgId);
    try {
      const writeBatch = batch(db);
      writeBatch.set(msgRef, {
        userId: uid,
        role,
        text: text.slice(0, 20000),
        createdAt: serverTimestamp(),
      });
      const sessionUpdates: DocumentData = {
        userId: uid,
        updatedAt: serverTimestamp(),
      };
      if (role === "user") {
        sessionUpdates.title = text.slice(0, 48) || "AI Assistant";
        sessionUpdates.lastPrompt = text.slice(0, 500);
      }
      writeBatch.set(sessionRef, sessionUpdates, { merge: true });
      await writeBatch.commit();
    } catch (e) {
      console.error("Save AI turn failed:", e);
    }
  }

  async streamPrompt(params: {
    prompt: string;
    history: AiMessage[];
    onChunk: (text: string) => void;
    onComplete: (fullText: string) => void;
    onError: (error: Error) => void;
    attachedImage?: string | null;
  }): Promise<void> {
    let timeout: ReturnType<typeof setTimeout> | undefined;
    try {
      const aiApiUrl = getAiApiUrl();
      const controller = new AbortController();
      timeout = setTimeout(() => controller.abort(), 60000);

      const contextText = params.history
        .slice(-8)
        .map((m) => `${m.role === "user" ? "User" : "Assistant"}: ${m.text}`)
        .join("\n");

      const fullPrompt = [
        contextText ? `Conversation context:\n${contextText}\n\nCurrent request:` : "",
        params.attachedImage ? "[Image attached]" : "",
        params.prompt,
      ]
        .filter(Boolean)
        .join("\n\n");

      const response = await fetch(`${aiApiUrl}/stream`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          prompt: fullPrompt,
          image: params.attachedImage || null,
        }),
        signal: controller.signal,
      });

      if (!response.ok || !response.body) {
        throw new Error(`AI request failed: ${response.statusText}`);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let sb = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        sb += decoder.decode(value, { stream: true });
        params.onChunk(sb);
      }

      if (sb.trim() === "") {
        throw new Error("AI model returned an empty response.");
      }
      params.onComplete(sb);
    } catch (e) {
      if (e instanceof Error && e.name === "AbortError") {
        params.onError(new Error("AI request timed out after 60 seconds."));
      } else {
        params.onError(e instanceof Error ? e : new Error("AI request failed."));
      }
    } finally {
      if (timeout) clearTimeout(timeout);
    }
  }

  async generateQuickText(prompt: string): Promise<string> {
    const response = await fetch(`${getAiApiUrl()}/generate`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ prompt }),
    });
    if (!response.ok) throw new Error("AI request failed");
    const data = await response.json();
    return data.text || "";
  }
}

function getAiApiUrl(): string {
  const baseUrl = process.env.NEXT_PUBLIC_AI_API_URL?.trim().replace(/\/+$/, "");
  if (!baseUrl) {
    throw new Error("AI is not configured for this static site. Set NEXT_PUBLIC_AI_API_URL to your secured AI backend.");
  }
  return baseUrl;
}

function authCurrentUserId(): string | null {
  return auth.currentUser?.uid ?? null;
}
