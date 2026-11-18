"use client";
import { getErrorMessage, normalizeFirestoreValue } from "@/lib/utils";

import {
  db,
  auth,
  doc,
  collection,
  query,
  where,
  onSnapshot,
  setDoc,
  updateDoc,
  serverTimestamp,
} from "@/lib/firebase";
import { CallSession, IceCandidateDoc } from "@/types/index";

export class CallRepository {
  observeMyCalls(userId: string): (callback: (calls: CallSession[]) => void) => () => void {
    return (callback) => {
      const q = query(collection(db, "calls"), where("participantIds", "array-contains", userId));
      const unsubscribe = onSnapshot(q, (snap) => {
        callback(
          snap.docs
            .map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as CallSession)
            .sort((a, b) => (b.createdAt?.getTime() ?? 0) - (a.createdAt?.getTime() ?? 0))
        );
      });
      return unsubscribe;
    };
  }

  observeCall(callId: string): (callback: (call: CallSession | null) => void) => () => void {
    return (callback) => {
      const unsubscribe = onSnapshot(doc(db, "calls", callId), (snap) => {
        callback(snap.exists() ? (normalizeFirestoreValue({ ...snap.data(), id: snap.id }) as CallSession) : null);
      });
      return unsubscribe;
    };
  }

  observeIceCandidates(callId: string): (callback: (candidates: IceCandidateDoc[]) => void) => () => void {
    return (callback) => {
      const uid = authCurrentUserId();
      const q = query(
        collection(db, "calls", callId, "candidates"),
        where("participantIds", "array-contains", uid)
      );
      const unsubscribe = onSnapshot(q, (snap) => {
        callback(snap.docs.map((d) => normalizeFirestoreValue({ ...d.data(), id: d.id }) as IceCandidateDoc));
      });
      return unsubscribe;
    };
  }

  async createOutgoingCall(params: {
    callerName: string;
    participantIds: string[];
    conversationId: string;
    callType: string;
    offerSdp: string;
  }): Promise<{ success: boolean; callId: string | null; error: string | null }> {
    const uid = authCurrentUserId();
    if (!uid) return { success: false, callId: null, error: "User must be signed in." };
    const callId = `call_${Math.random().toString(36).slice(2, 18)}`;
    const participants = [...new Set([uid, ...params.participantIds])].slice(0, 16);
    try {
      await setDoc(doc(db, "calls", callId), {
        callerId: uid,
        callerName: params.callerName || "User",
        conversationId: params.conversationId.slice(0, 128),
        participantIds: participants,
        callType: params.callType,
        status: "ringing",
        offerSdp: params.offerSdp.slice(0, 32000),
        answerSdp: "",
        durationSeconds: 0,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      return { success: true, callId, error: null };
    } catch (e) {
      return { success: false, callId: null, error: getErrorMessage(e) };
    }
  }

  async answerCall(callId: string, answerSdp: string): Promise<{ success: boolean; error: string | null }> {
    try {
      await updateDoc(doc(db, "calls", callId), {
        status: "active",
        answerSdp: answerSdp.slice(0, 32000),
        updatedAt: serverTimestamp(),
      });
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  async updateCallStatus(callId: string, status: string, durationSeconds: number = 0): Promise<{ success: boolean; error: string | null }> {
    try {
      await updateDoc(doc(db, "calls", callId), {
        status,
        durationSeconds,
        updatedAt: serverTimestamp(),
      });
      return { success: true, error: null };
    } catch (e) {
      return { success: false, error: getErrorMessage(e) };
    }
  }

  async addIceCandidate(params: {
    callId: string;
    participantIds: string[];
    sdpMid: string;
    sdpMLineIndex: number;
    candidate: string;
  }): Promise<void> {
    const uid = authCurrentUserId();
    if (!uid) return;
    const candId = `ice_${Math.random().toString(36).slice(2, 18)}`;
    const ref = doc(db, "calls", params.callId, "candidates", candId);
    try {
      await setDoc(ref, {
        senderId: uid,
        participantIds: params.participantIds.slice(0, 16),
        sdpMid: params.sdpMid.slice(0, 64),
        sdpMLineIndex: params.sdpMLineIndex,
        candidate: params.candidate.slice(0, 4000),
        createdAt: serverTimestamp(),
      });
    } catch (e) {
      console.error("Add ICE candidate failed:", e);
    }
  }
}

function authCurrentUserId(): string | null {
  return auth.currentUser?.uid ?? null;
}
