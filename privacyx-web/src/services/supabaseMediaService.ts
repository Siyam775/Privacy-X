"use client";

import { supabaseUrl, supabaseAnonKey, supabaseMediaBridgeUrl, DEFAULT_MAX_UPLOAD_MB, MEDIA_RETENTION_MS } from "@/lib/firebase";
import { auth } from "@/lib/firebase";
import {
  MediaValidationResult,
  UploadedMediaResult,
  ChatMessage,
} from "@/types/index";

const allowedMimePrefixes = ["image/", "audio/"];
const bannedVideoExtensions = new Set(["mp4", "mov", "avi", "mkv", "webm", "flv", "m4v", "3g2"]);
const allowedDocMimes = new Set([
  "application/pdf",
  "application/msword",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "application/vnd.ms-excel",
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  "application/vnd.ms-powerpoint",
  "application/vnd.openxmlformats-officedocument.presentationml.presentation",
  "text/plain",
  "application/zip",
  "application/x-zip-compressed",
]);

const extensionMimeMap: Record<string, string> = {
  m4a: "audio/mp4",
  mp4a: "audio/mp4",
  "3gp": "audio/3gpp",
  "3gpp": "audio/3gpp",
  amr: "audio/amr",
  aac: "audio/aac",
  mp3: "audio/mpeg",
  ogg: "audio/ogg",
  opus: "audio/ogg",
  wav: "audio/wav",
  jpg: "image/jpeg",
  jpeg: "image/jpeg",
  png: "image/png",
  webp: "image/webp",
  gif: "image/gif",
  heic: "image/heic",
  pdf: "application/pdf",
  txt: "text/plain",
  zip: "application/zip",
};

export class SupabaseMediaService {
  isConfigured(): boolean {
    return !!supabaseMediaBridgeUrl || (!!supabaseUrl && !!supabaseAnonKey);
  }

  validateUri(file: File, maxUploadMb: number = DEFAULT_MAX_UPLOAD_MB): MediaValidationResult {
    const name = file.name;
    const size = file.size;
    const extFromName = name.substring(name.lastIndexOf(".") + 1).toLowerCase();
    const rawMime = file.type || extensionMimeMap[extFromName] || "application/octet-stream";

    if (rawMime.startsWith("video/") || bannedVideoExtensions.has(extFromName)) {
      throw new Error("Video messaging is disabled. Use photos, voice notes, documents, or WebRTC video calls.");
    }

    const maxBytes = maxUploadMb * 1024 * 1024;
    if (size > maxBytes) {
      throw new Error(`File exceeds ${maxUploadMb}MB limit.`);
    }

    const isAllowed = allowedMimePrefixes.some((p) => rawMime.startsWith(p)) || allowedDocMimes.has(rawMime);
    if (!isAllowed) {
      throw new Error(`Unsupported MIME type: ${rawMime}`);
    }

    const category = rawMime.startsWith("image/") ? "image" : rawMime.startsWith("audio/") ? "voice" : "document";

    return { fileName: name, mimeType: rawMime, sizeBytes: size, category };
  }

  async uploadMedia(file: File, conversationId: string = "personal", compressImage: boolean = true): Promise<UploadedMediaResult> {
    const validation = this.validateUri(file);
    const mediaId = `m_${Math.random().toString(36).slice(2, 20)}`;
    const expiresAtMs = Date.now() + MEDIA_RETENTION_MS;

    // Optimize image client-side (Canvas WebP)
    let preparedFile: File = file;
    let mimeType = validation.mimeType;
    let fileName = validation.fileName;
    let category = validation.category;
    let thumbFile: File | null = null;

    if (category === "image" && compressImage) {
      const optimized = await this.optimizeImageToWebP(file, 1600);
      if (optimized) {
        preparedFile = optimized.main;
        mimeType = "image/webp";
        fileName = validation.fileName.replace(/\.[^/.]+$/, "") + ".webp";
        category = "image";
        thumbFile = optimized.thumb;
      }
    }

    if (!this.isConfigured()) {
      throw new Error("Supabase Storage is not configured. Set SUPABASE_URL, SUPABASE_ANON_KEY, and SUPABASE_MEDIA_BRIDGE_URL.");
    }

    const idToken = await this.getIdToken();

    let uploadResult: { uploadUrl: string; thumbUploadUrl?: string; readUrl: string; thumbReadUrl?: string; objectPath: string; expiresAtMs: number };

    if (supabaseMediaBridgeUrl) {
      uploadResult = await this.uploadViaAuthBridge(supabaseMediaBridgeUrl, mediaId, conversationId, preparedFile, thumbFile, mimeType, fileName, idToken);
    } else {
      uploadResult = await this.uploadViaSupabaseRest(mediaId, conversationId, preparedFile, thumbFile, mimeType, expiresAtMs);
    }

    return {
      mediaId,
      secureUrl: uploadResult.readUrl,
      thumbnailUrl: uploadResult.thumbReadUrl || "",
      remotePath: uploadResult.objectPath,
      localFilePath: "", // Web doesn't have local file paths
      localThumbPath: "",
      fileName,
      mimeType,
      sizeBytes: preparedFile.size,
      expiresAtMs: uploadResult.expiresAtMs,
      category,
    };
  }

  private async getIdToken(): Promise<string> {
    const user = auth.currentUser;
    if (!user) throw new Error("Sign in required before uploading media.");
    return user.getIdToken(false);
  }

  private async uploadViaAuthBridge(
    bridgeUrl: string,
    mediaId: string,
    conversationId: string,
    file: File,
    thumbFile: File | null,
    mimeType: string,
    fileName: string,
    idToken: string
  ) {
    const reqBody = {
      action: "sign_upload",
      mediaId,
      conversationId,
      category: file.type.startsWith("image/") ? "image" : file.type.startsWith("audio/") ? "voice" : "document",
      mimeType,
      fileName,
      sizeBytes: file.size,
      hasThumbnail: !!thumbFile,
    };

    const bridgeResp = await fetch(bridgeUrl, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${idToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(reqBody),
    });

    if (!bridgeResp.ok) {
      throw new Error(`Bridge returned ${bridgeResp.status}`);
    }

    const bridgeJson = await bridgeResp.json();
    const uploadUrl = bridgeJson.uploadUrl;
    const readUrl = bridgeJson.readUrl;
    const thumbUploadUrl = bridgeJson.thumbUploadUrl;
    const objectPath = bridgeJson.objectPath;
    const expiresAtMs = bridgeJson.expiresAtMs || Date.now() + MEDIA_RETENTION_MS;

    if (!uploadUrl || !readUrl) {
      throw new Error("Bridge returned incomplete Supabase signed URLs.");
    }

    // Upload main file
    await fetch(uploadUrl, {
      method: "PUT",
      body: file,
      headers: { "Content-Type": mimeType },
    });

    // Upload thumbnail
    if (thumbFile && thumbUploadUrl) {
      await fetch(thumbUploadUrl, {
        method: "PUT",
        body: thumbFile,
        headers: { "Content-Type": "image/webp" },
      });
    }

    return { uploadUrl, thumbUploadUrl, readUrl, thumbReadUrl: bridgeJson.thumbReadUrl, objectPath, expiresAtMs };
  }

  private async uploadViaSupabaseRest(
    mediaId: string,
    conversationId: string,
    file: File,
    thumbFile: File | null,
    mimeType: string,
    expiresAtMs: number
  ) {
    const uid = auth.currentUser?.uid;
    if (!uid) throw new Error("User must be signed in.");
    const safeConv = conversationId.replace(/[^a-zA-Z0-9_-]/g, "_") || "personal";
    const ext = file.name.split(".").pop() || "webp";
    const objectPath = `${uid}/${safeConv}/${mediaId}.${ext}`;
    const endpoint = `${supabaseUrl}/storage/v1/object/chat-media/${objectPath}`;

    await fetch(endpoint, {
      method: "POST",
      headers: {
        apikey: supabaseAnonKey!,
        Authorization: `Bearer ${supabaseAnonKey!}`,
        "x-upsert": "true",
      },
      body: file,
    });

    let thumbUrl = "";
    if (thumbFile) {
      const thumbPath = `${uid}/${safeConv}/${mediaId}_thumb.webp`;
      const thumbEndpoint = `${supabaseUrl}/storage/v1/object/chat-media/${thumbPath}`;
      await fetch(thumbEndpoint, {
        method: "POST",
        headers: {
          apikey: supabaseAnonKey!,
          Authorization: `Bearer ${supabaseAnonKey!}`,
          "x-upsert": "true",
        },
        body: thumbFile,
      });
      thumbUrl = thumbEndpoint;
    }

    return {
      uploadUrl: endpoint,
      thumbUploadUrl: thumbFile ? endpoint : undefined,
      readUrl: endpoint,
      thumbReadUrl: thumbUrl,
      objectPath,
      expiresAtMs,
    };
  }

  private async optimizeImageToWebP(file: File, maxDimension: number): Promise<{ main: File; thumb: File } | null> {
    return new Promise((resolve) => {
      const img = new Image();
      const url = URL.createObjectURL(file);
      img.onload = async () => {
        URL.revokeObjectURL(url);
        const canvas = document.createElement("canvas");
        let { width, height } = img;
        const longest = Math.max(width, height);
        if (longest > maxDimension) {
          const scale = maxDimension / longest;
          width = Math.round(width * scale);
          height = Math.round(height * scale);
        }
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext("2d")!;
        ctx.drawImage(img, 0, 0, width, height);

        const mainBlob = await new Promise<Blob | null>((res) => canvas.toBlob(res, "image/webp", 84));
        if (!mainBlob) return resolve(null);

        // Thumbnail 320px
        const thumbCanvas = document.createElement("canvas");
        const thumbSize = 320;
        const tScale = thumbSize / Math.max(width, height);
        const tWidth = Math.max(1, Math.round(width * tScale));
        const tHeight = Math.max(1, Math.round(height * tScale));
        thumbCanvas.width = tWidth;
        thumbCanvas.height = tHeight;
        const tCtx = thumbCanvas.getContext("2d")!;
        tCtx.drawImage(img, 0, 0, tWidth, tHeight);
        const thumbBlob = await new Promise<Blob | null>((res) => thumbCanvas.toBlob(res, "image/webp", 72));

        const mainFile = new File([mainBlob], file.name.replace(/\.[^/.]+$/, "") + ".webp", { type: "image/webp" });
        const thumbFile = thumbBlob ? new File([thumbBlob], `${mediaIdPlaceholder()}_thumb.webp`, { type: "image/webp" }) : null;

        resolve({ main: mainFile, thumb: thumbFile || undefined as unknown as File });
      };
      img.onerror = () => resolve(null);
      img.src = url;
    });
  }

  async downloadMedia(message: ChatMessage, onProgress?: (p: number) => void): Promise<{ localUrl: string; fileName: string }> {
    const url = message.mediaUrl;
    const expired = message.mediaExpired || !url || (message.mediaExpiresAt > 0 && message.mediaExpiresAt <= Date.now());
    if (expired) {
      throw new Error("Media expired from cloud after 7 days and no local copy was saved.");
    }

    const resp = await fetch(url);
    if (!resp.ok) throw new Error(`Download failed (HTTP ${resp.status})`);
    const blob = await resp.blob();
    const localUrl = URL.createObjectURL(blob);
    const fileName = message.fileName || `privacyx_${message.mediaId || message.id}`;
    onProgress?.(1);
    return { localUrl, fileName };
  }

  async clearTemporaryCache(): Promise<number> {
    // Web: clear IndexedDB cache or revoke object URLs
    return 0;
  }
}

function mediaIdPlaceholder(): string {
  return `m_${Math.random().toString(36).slice(2, 20)}`;
}

export const supabaseMediaService = new SupabaseMediaService();
