"use client";

import { useEffect, useRef, useState } from "react";
import { CallRepository } from "@/services/callRepository";
import { AuthRepository } from "@/services/authRepository";
import { useAuthStore, useCallStore } from "@/stores/index";
import { useRouter } from "next/navigation";
import { Camera, CameraOff, Mic, MicOff } from "lucide-react";
import { getErrorMessage } from "@/lib/utils";

const callRepo = new CallRepository();
const authRepo = new AuthRepository();

export default function CallsPage() {
  const router = useRouter();
  const { user } = useAuthStore();
  const { callHistory, setCallHistory, activeCall, setActiveCall, incomingCall, setIncomingCall, isCallActive, endCall } = useCallStore();
  const [dialerOpen, setDialerOpen] = useState(false);
  const [dialerNumber, setDialerNumber] = useState("");
  const [dialerVideo, setDialerVideo] = useState(false);
  const [isMuted, setIsMuted] = useState(false);
  const [cameraEnabled, setCameraEnabled] = useState(true);
  const [remoteStream, setRemoteStream] = useState<MediaStream | null>(null);
  const [callDuration, setCallDuration] = useState(0);
  const peerConnectionRef = useRef<RTCPeerConnection | null>(null);
  const localStreamRef = useRef<MediaStream | null>(null);
  const signalUnsubsRef = useRef<Array<() => void>>([]);
  const localVideoRef = useRef<HTMLVideoElement>(null);
  const remoteVideoRef = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    if (!user) return;
    const unsub = callRepo.observeMyCalls(user.userId)((calls) => {
      setCallHistory(calls);
      const incoming = calls.find((call) => call.callerId !== user.userId && call.status === "ringing");
      setIncomingCall(incoming || null);
      const live = calls.find((call) => call.status === "active" || (call.callerId === user.userId && call.status === "ringing"));
      if (live) setActiveCall(live);
      else if (useCallStore.getState().activeCall) {
        signalUnsubsRef.current.forEach((stop) => stop());
        signalUnsubsRef.current = [];
        peerConnectionRef.current?.close();
        peerConnectionRef.current = null;
        localStreamRef.current?.getTracks().forEach((track) => track.stop());
        localStreamRef.current = null;
        setRemoteStream(null);
        endCall();
      }
    });
    return () => {
      unsub();
      signalUnsubsRef.current.forEach((stop) => stop());
      peerConnectionRef.current?.close();
      localStreamRef.current?.getTracks().forEach((track) => track.stop());
    };
  }, [user, setCallHistory, setIncomingCall, setActiveCall, endCall]);

  useEffect(() => {
    if (localVideoRef.current) localVideoRef.current.srcObject = localStreamRef.current;
  }, [isCallActive]);
  useEffect(() => {
    if (remoteVideoRef.current) remoteVideoRef.current.srcObject = remoteStream;
  }, [remoteStream]);
  useEffect(() => {
    if (!isCallActive) return;
    const startedAt = Date.now();
    const timer = window.setInterval(() => setCallDuration(Math.floor((Date.now() - startedAt) / 1000)), 1000);
    return () => window.clearInterval(timer);
  }, [isCallActive, activeCall?.id]);

  const connectPeer = (callId: string, pc: RTCPeerConnection, isCaller: boolean, participantIds: string[]) => {
    peerConnectionRef.current = pc;
    let queuedCandidates: RTCIceCandidateInit[] = [];
    pc.onicecandidate = (event) => {
      if (!event.candidate || !user) return;
      void callRepo.addIceCandidate({
        callId,
        participantIds,
        sdpMid: event.candidate.sdpMid || "0",
        sdpMLineIndex: event.candidate.sdpMLineIndex ?? 0,
        candidate: event.candidate.candidate,
      });
    };
    pc.ontrack = (event) => setRemoteStream(event.streams[0] || null);
    signalUnsubsRef.current.forEach((stop) => stop());
    signalUnsubsRef.current = [];
    signalUnsubsRef.current.push(callRepo.observeCall(callId)((call) => {
      if (!call) return;
      if (isCaller && call.answerSdp && !pc.remoteDescription) {
        void pc.setRemoteDescription({ type: "answer", sdp: call.answerSdp }).then(async () => {
          for (const candidate of queuedCandidates) await pc.addIceCandidate(candidate).catch(() => undefined);
          queuedCandidates = [];
        }).catch((error) => console.error("Could not apply call answer", error));
      }
      if (["ended", "missed", "rejected"].includes(call.status)) {
        pc.close();
        localStreamRef.current?.getTracks().forEach((track) => track.stop());
        localStreamRef.current = null;
        setRemoteStream(null);
        endCall();
      }
    }));
    signalUnsubsRef.current.push(callRepo.observeIceCandidates(callId)((candidates) => {
      for (const candidate of candidates) {
        if (candidate.senderId === user?.userId) continue;
        const init = { candidate: candidate.candidate, sdpMid: candidate.sdpMid, sdpMLineIndex: candidate.sdpMLineIndex };
        if (!pc.remoteDescription) queuedCandidates.push(init);
        else void pc.addIceCandidate(init).catch((error) => console.error("Could not add call candidate", error));
      }
    }));
  };

  const startOutgoingCall = async (targetUserId: string, targetName: string, isVideo: boolean) => {
    try {
      const target = (await authRepo.searchUsers(targetUserId)).find((person) => person.userId === targetUserId || person.username === targetUserId.replace(/^@/, "").toLowerCase());
      if (!target) throw new Error("No PrivacyX user found with that username.");
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: true,
        video: isVideo,
      });
      setIsMuted(false);
      setCameraEnabled(true);
      localStreamRef.current = stream;

      const pc = new RTCPeerConnection({
        iceServers: [
          { urls: "stun:stun.l.google.com:19302" },
          { urls: "stun:stun1.l.google.com:19302" },
        ],
      });

      stream.getTracks().forEach((track) => pc.addTrack(track, stream));

      const offer = await pc.createOffer();
      await pc.setLocalDescription(offer);
      await waitForIceGathering(pc);

      const result = await callRepo.createOutgoingCall({
        callerName: user!.displayName || "User",
        participantIds: [target.userId],
        conversationId: "direct_call",
        callType: isVideo ? "video" : "voice",
        offerSdp: pc.localDescription?.sdp || offer.sdp || "",
      });

      if (result.success) {
        setCallDuration(0);
        const outgoingCall = {
          id: result.callId!, callerId: user!.userId, callerName: user!.displayName || "User",
          conversationId: "direct_call", participantIds: [user!.userId, target.userId],
          callType: isVideo ? "video" as const : "voice" as const, status: "ringing" as const,
          offerSdp: pc.localDescription?.sdp || offer.sdp || "", answerSdp: "", durationSeconds: 0,
        };
        setActiveCall({
          ...outgoingCall,
        });
        connectPeer(result.callId!, pc, true, outgoingCall.participantIds);
        setDialerOpen(false);
      } else {
        pc.close();
        stream.getTracks().forEach((track) => track.stop());
        throw new Error(result.error || "Could not start this call.");
      }
    } catch (err) {
      alert(getErrorMessage(err, "Failed to start call"));
    }
  };

  const answerIncomingCall = async () => {
    if (!incomingCall) return;
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: incomingCall.callType === "video" });
      setIsMuted(false);
      setCameraEnabled(true);
      localStreamRef.current = stream;
      const pc = new RTCPeerConnection({
        iceServers: [{ urls: "stun:stun.l.google.com:19302" }],
      });
      stream.getTracks().forEach((track) => pc.addTrack(track, stream));

      await pc.setRemoteDescription({ type: "offer", sdp: incomingCall.offerSdp });
      const answer = await pc.createAnswer();
      await pc.setLocalDescription(answer);
      await waitForIceGathering(pc);

      const result = await callRepo.answerCall(incomingCall.id, pc.localDescription?.sdp || answer.sdp || "");
      if (!result.success) throw new Error(result.error || "Could not answer the call.");
      setCallDuration(0);
      setActiveCall({ ...incomingCall, status: "active" });
      connectPeer(incomingCall.id, pc, false, incomingCall.participantIds);
      setIncomingCall(null);
    } catch (err) {
      alert(getErrorMessage(err, "Failed to answer call"));
    }
  };

  const rejectIncomingCall = async () => {
    if (!incomingCall) return;
    await callRepo.updateCallStatus(incomingCall.id, "rejected");
    setIncomingCall(null);
  };

  const finishCall = async () => {
    if (activeCall) await callRepo.updateCallStatus(activeCall.id, "ended", callDuration);
    signalUnsubsRef.current.forEach((stop) => stop());
    signalUnsubsRef.current = [];
    peerConnectionRef.current?.close();
    peerConnectionRef.current = null;
    localStreamRef.current?.getTracks().forEach((track) => track.stop());
    localStreamRef.current = null;
    setRemoteStream(null);
    endCall();
  };

  return (
    <div className="flex h-dvh min-h-0 flex-col theme-transition bg-orbs relative overflow-hidden">
      <div className="absolute inset-0 bg-gradient-to-b from-[var(--px-bg)] to-[var(--px-bg-secondary)] pointer-events-none" />
      {/* Header */}
      <div className="h-16 border-b border-[var(--px-border)] bg-[var(--px-surface)] backdrop-blur-lg shrink-0 flex items-center justify-between px-4 z-10 sticky top-0 relative z-20">
        <div className="flex items-center gap-3 cursor-pointer">
          <button onClick={() => router.push("/")} aria-label="Back to chats" className="p-2 rounded-xl hover:bg-[var(--px-accent-glass)] text-[var(--px-text-secondary)] transition-all border border-transparent hover:border-white/30 dark:hover:border-gray-600/50">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <div>
            <h1 className="font-semibold text-[var(--px-text-primary)]">Calls</h1>
            <p className="text-xs text-[var(--px-text-muted)]">Voice & video calls</p>
          </div>
        </div>
        <button
          onClick={() => setDialerOpen(true)}
          className="p-2 rounded-xl bg-violet-600 text-white shadow-lg hover:bg-violet-700 transition-colors"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 4v16m8-8H4" />
          </svg>
        </button>
      </div>

      {/* Incoming Call Overlay */}
      {incomingCall && (
        <div className="fixed inset-0 z-50 bg-gradient-to-br from-violet-600 to-rose-600 flex flex-col items-center justify-center text-white p-6">
          <div className="w-24 h-24 rounded-full bg-white/20 backdrop-blur mb-6 flex items-center justify-center border border-white/30 animate-pulse">
            <span className="text-4xl">📞</span>
          </div>
          <h2 className="text-3xl font-bold mb-2">Incoming Call</h2>
          <p className="text-xl text-white/90 mb-8">{incomingCall.callerName} is calling</p>
          <p className="text-white/70 mb-8">{incomingCall.callType === "video" ? "Video Call" : "Voice Call"}</p>
          <div className="flex gap-4">
            <button
              onClick={rejectIncomingCall}
              className="w-14 h-14 rounded-full bg-white/20 backdrop-blur text-white flex items-center justify-center hover:bg-white/30 transition-colors border border-white/30"
            >
              <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24"><path d="M24 20.188l-8-3.199-8 3.199A6 6 0 012 16.188v-4.838A6 6 0 016 6h12v10.188A6 6 0 0124 20.188z"/></svg>
            </button>
            <button
              onClick={answerIncomingCall}
              className="w-14 h-14 rounded-full bg-emerald-500 text-white flex items-center justify-center hover:bg-emerald-600 transition-colors shadow-xl"
            >
              <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
            </button>
          </div>
        </div>
      )}

      {/* Active Call UI */}
      {activeCall && isCallActive && !incomingCall && (
        <div className="fixed inset-0 z-50 bg-black flex flex-col items-center justify-center text-white p-4">
          <div className="relative w-full max-w-md">
            <video
              ref={remoteVideoRef}
              autoPlay
              playsInline
              className="h-[min(45dvh,300px)] w-full rounded-2xl bg-gray-900 object-cover sm:h-[300px]"
            />
            <video ref={localVideoRef} autoPlay muted playsInline className="absolute bottom-3 right-3 h-20 w-28 rounded-xl border border-white/50 bg-gray-800 object-cover" />
            <div className="absolute inset-0 flex items-center justify-center">
              <div className="text-6xl font-bold bg-black/40 px-8 py-4 rounded-xl backdrop-blur">{String(Math.floor(callDuration / 60)).padStart(2, "0")}:{String(callDuration % 60).padStart(2, "0")}</div>
            </div>
          </div>
          <div className="mt-6 flex items-center justify-center gap-2">
            <span className="text-lg font-medium">{activeCall.callerName}</span>
            <span className="text-sm text-gray-400">{activeCall.callType === "video" ? "Video" : "Voice"} • Active</span>
          </div>
          <div className="mt-5 flex flex-wrap justify-center gap-3 sm:mt-8 sm:gap-4">
            <button
              className="w-12 h-12 rounded-full bg-gray-700 text-white flex items-center justify-center hover:bg-gray-600"
              aria-label={isMuted ? "Unmute microphone" : "Mute microphone"}
              onClick={() => {
                const nextMuted = !isMuted;
                localStreamRef.current?.getAudioTracks().forEach((track) => { track.enabled = !nextMuted; });
                setIsMuted(nextMuted);
              }}
            >
              {isMuted ? <MicOff size={22} /> : <Mic size={22} />}
            </button>
            {activeCall.callType === "video" && <button aria-label={cameraEnabled ? "Turn camera off" : "Turn camera on"} className="w-12 h-12 rounded-full bg-gray-700 text-white flex items-center justify-center hover:bg-gray-600" onClick={() => { const nextEnabled = !cameraEnabled; localStreamRef.current?.getVideoTracks().forEach((track) => { track.enabled = nextEnabled; }); setCameraEnabled(nextEnabled); }}>{cameraEnabled ? <Camera size={21} /> : <CameraOff size={21} />}</button>}
            <button
              className="w-12 h-12 rounded-full bg-red-500 text-white flex items-center justify-center hover:bg-red-600 shadow-lg"
              onClick={() => {
                void finishCall();
              }}
            >
              <svg className="w-6 h-6" fill="currentColor" viewBox="0 0 24 24"><path d="M21 4H3a1 1 0 00-1 1v16a1 1 0 001 1h18a1 1 0 001-1V5a1 1 0 00-1-1zM9 9l6 6M15 15l-6-6"/></svg>
            </button>
          </div>
        </div>
      )}

      {/* Dialer Modal */}
      {dialerOpen && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur flex items-center justify-center p-4">
          <div className="max-h-[calc(100dvh-2rem)] w-full max-w-sm overflow-y-auto rounded-2xl border border-white/80 bg-white/95 p-4 backdrop-blur dark:border-gray-700 dark:bg-gray-900/95 sm:p-6">
            <h3 className="text-lg font-bold text-[var(--px-text-primary)] mb-4">Start Call</h3>
            <div className="space-y-4 mb-6">
              <input
                type="text"
                value={dialerNumber}
                onChange={(e) => setDialerNumber(e.target.value)}
                placeholder="Username or @username"
                className="w-full p-3 rounded-xl border border-white/30 dark:border-gray-700 bg-transparent text-[var(--px-text-primary)] focus:outline-none focus:ring-2 focus:ring-violet-500"
              />
              <label className="flex items-center justify-between p-3 rounded-xl border border-white/30 dark:border-gray-700 cursor-pointer">
                <span className="text-sm text-[var(--px-text-secondary)]">Video Call</span>
                <input type="checkbox" checked={dialerVideo} onChange={(e) => setDialerVideo(e.target.checked)} className="accent-violet-600" />
              </label>
            </div>
            <div className="flex gap-3">
              <button
                onClick={() => setDialerOpen(false)}
                className="flex-1 py-3 rounded-xl text-[var(--px-text-secondary)] hover:bg-white/100 dark:hover:bg-gray-800 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  if (!dialerNumber) return;
                  startOutgoingCall(dialerNumber, dialerNumber, dialerVideo);
                }}
                className="flex-1 py-3 rounded-xl bg-violet-600 text-white font-bold hover:bg-violet-700 transition-colors shadow-lg"
              >
                {dialerVideo ? "Video Call" : "Voice Call"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Call History */}
      <div className="min-h-0 flex-1 overflow-y-auto p-3 pb-28 sm:p-4 sm:pb-28 relative z-10">
        <h2 className="text-lg font-bold text-[var(--px-text-primary)] mb-4">Call History</h2>
        {callHistory.length === 0 ? (
          <div className="text-center py-20">
            <div className="w-16 h-16 rounded-full bg-white/40 dark:bg-gray-800/40 mx-auto mb-4 flex items-center justify-center">
              <span className="text-3xl">📞</span>
            </div>
            <h3 className="text-lg font-medium text-[var(--px-text-primary)]">No call history</h3>
            <p className="text-[var(--px-text-muted)] mt-2">Your calls will appear here.</p>
          </div>
        ) : (
          <div className="space-y-3">
            {callHistory.map((call) => (
              <div key={call.id} className="flex items-center justify-between p-3 rounded-xl bg-[var(--px-surface)] border border-[var(--px-border)]">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-full bg-white/20 dark:bg-gray-800 flex items-center justify-center">
                    <svg className="w-5 h-5 text-[var(--px-text-secondary)]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2-2v8a2 2 0 002 2z" />
                    </svg>
                  </div>
                  <div>
                    <h4 className="font-medium text-[var(--px-text-primary)]">{call.callerName}</h4>
                    <p className="text-xs text-[var(--px-text-muted)]">
                      {call.callType === "video" ? "Video Call" : "Voice Call"} • {call.status}
                    </p>
                  </div>
                </div>
                <span className="text-xs font-medium text-[var(--px-text-muted)]">
                  {call.durationSeconds > 0
                    ? `${Math.floor(call.durationSeconds / 60)}:${String(call.durationSeconds % 60).padStart(2, "0")}`
                    : "--:--"}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function waitForIceGathering(peer: RTCPeerConnection): Promise<void> {
  if (peer.iceGatheringState === "complete") return Promise.resolve();
  return new Promise((resolve) => {
    const onStateChange = () => {
      if (peer.iceGatheringState === "complete") {
        peer.removeEventListener("icegatheringstatechange", onStateChange);
        resolve();
      }
    };
    peer.addEventListener("icegatheringstatechange", onStateChange);
    window.setTimeout(() => {
      peer.removeEventListener("icegatheringstatechange", onStateChange);
      resolve();
    }, 8000);
  });
}
