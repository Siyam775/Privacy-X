// PrivacyX Supabase Edge Function: Verified Firebase Auth -> Supabase Storage Bridge
// Deploy without GitHub: supabase functions deploy media-bridge --no-verify-jwt
// Required secrets in Supabase: FIREBASE_PROJECT_ID (privacy-x), SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { createRemoteJWKSet, jwtVerify } from "https://deno.land/x/jose@v5.9.6/index.ts";

const BUCKET = "chat-media";
const RETENTION_MS = 7 * 24 * 60 * 60 * 1000;
const SUPER_ADMIN_UID = "2bL4lJTTt9WMILpCclHeKNYOdz62";
const FIREBASE_PROJECT_ID = Deno.env.get("FIREBASE_PROJECT_ID") || "privacy-x";
const FIRESTORE_DATABASE_ID =
  Deno.env.get("FIRESTORE_DATABASE_ID") ||
  "ai-studio-android-privacyx-5767d797-31a9-488e-9a25-d28909171c2e";

const JWKS = createRemoteJWKSet(
  new URL(
    "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"
  )
);

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, content-type, x-client-info, apikey",
};

async function verifyFirebaseToken(authHeader: string | null): Promise<{ uid: string; token: string }> {
  if (!authHeader?.startsWith("Bearer ")) {
    throw new Error("Missing Bearer Firebase ID token");
  }
  const token = authHeader.slice(7).trim();
  const { payload } = await jwtVerify(token, JWKS, {
    issuer: `https://securetoken.google.com/${FIREBASE_PROJECT_ID}`,
    audience: FIREBASE_PROJECT_ID,
  });
  const uid = typeof payload.sub === "string" ? payload.sub : "";
  if (!uid) throw new Error("Firebase token missing sub claim");
  return { uid, token };
}

async function verifyConversationAccess(
  idToken: string,
  uid: string,
  conversationId: string,
  ownerUid: string,
  mode: "upload" | "download"
): Promise<boolean> {
  if (uid === SUPER_ADMIN_UID) return true;
  if (conversationId === "personal") {
    return ownerUid === uid;
  }
  if (conversationId === "avatars" || conversationId === "stories" || conversationId === "posts") {
    return mode === "download" ? true : ownerUid === uid;
  }

  // Verify conversation membership directly against Firestore Security Rules using the caller's Firebase ID token.
  const url =
    `https://firestore.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/databases/${FIRESTORE_DATABASE_ID}/documents/conversations/${encodeURIComponent(conversationId)}`;
  const resp = await fetch(url, {
    headers: { Authorization: `Bearer ${idToken}` },
  });
  if (!resp.ok) {
    return false;
  }
  const doc = await resp.json();
  const memberValues: Array<{ stringValue?: string }> =
    doc?.fields?.memberUids?.arrayValue?.values || [];
  const members = memberValues.map((v) => v.stringValue).filter(Boolean);
  if (members.includes(uid)) {
    return true;
  }
  const isPublic = doc?.fields?.isPublic?.booleanValue === true;
  return mode === "download" && isPublic;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: CORS_HEADERS });
  }

  try {
    const { uid, token } = await verifyFirebaseToken(req.headers.get("Authorization"));
    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!supabaseUrl || !serviceRoleKey) {
      return Response.json({ error: "Server storage secrets are not configured" }, { status: 500, headers: CORS_HEADERS });
    }
    const supabase = createClient(supabaseUrl, serviceRoleKey);

    const body = await req.json();
    const action = String(body.action || "sign_upload");

    if (action === "sign_upload") {
      const conversationId = String(body.conversationId || "personal").replace(/[^a-zA-Z0-9_-]/g, "_");
      const mediaId = String(body.mediaId || crypto.randomUUID()).replace(/[^a-zA-Z0-9_-]/g, "_");
      const category = String(body.category || "image");
      const mimeType = String(body.mimeType || "application/octet-stream").toLowerCase();
      const fileName = String(body.fileName || "file");
      const sizeBytes = Number(body.sizeBytes || 0);
      const hasThumbnail = Boolean(body.hasThumbnail);

      if (!["image", "voice", "document", "video", "avatar", "story", "post"].includes(category)) {
        return Response.json({ error: "Unsupported category" }, { status: 400, headers: CORS_HEADERS });
      }
      if (category === "video" && !mimeType.startsWith("video/")) {
        return Response.json({ error: "Video files must use a video MIME type." }, { status: 400, headers: CORS_HEADERS });
      }
      if (category !== "video" && mimeType.startsWith("video/")) {
        return Response.json({ error: "Video MIME types must use the video category." }, { status: 400, headers: CORS_HEADERS });
      }
      if (sizeBytes < 0 || sizeBytes > 25 * 1024 * 1024) {
        return Response.json({ error: "File exceeds 25MB limit" }, { status: 400, headers: CORS_HEADERS });
      }

      const canUpload = await verifyConversationAccess(token, uid, conversationId, uid, "upload");
      if (!canUpload) {
        return Response.json({ error: "Forbidden: not a member of this conversation" }, { status: 403, headers: CORS_HEADERS });
      }

      const ext = fileName.includes(".") ? fileName.split(".").pop()?.replace(/[^a-zA-Z0-9]/g, "") : "bin";
      const objectPath = `${uid}/${conversationId}/${mediaId}.${ext || "bin"}`;
      const thumbPath = hasThumbnail ? `${uid}/${conversationId}/${mediaId}_thumb.webp` : null;
      const expiresAtIso = new Date(Date.now() + RETENTION_MS).toISOString();

      const { data: uploadSigned, error: uploadErr } = await supabase.storage
        .from(BUCKET)
        .createSignedUploadUrl(objectPath);
      if (uploadErr || !uploadSigned) {
        return Response.json({ error: uploadErr?.message || "Failed to sign upload URL" }, { status: 500, headers: CORS_HEADERS });
      }

      let thumbSignedUrl = "";
      if (thumbPath) {
        const { data: thumbSigned } = await supabase.storage
          .from(BUCKET)
          .createSignedUploadUrl(thumbPath);
        thumbSignedUrl = thumbSigned?.signedUrl || "";
      }

      await supabase.from("media_objects").upsert({
        media_id: mediaId,
        object_path: objectPath,
        thumb_path: thumbPath,
        owner_uid: uid,
        conversation_id: conversationId,
        category,
        mime_type: mimeType,
        size_bytes: sizeBytes,
        expires_at: expiresAtIso,
        deleted_from_storage: false,
      });

      const { data: readSigned } = await supabase.storage
        .from(BUCKET)
        .createSignedUrl(objectPath, 7 * 24 * 60 * 60);
      const { data: thumbReadSigned } = thumbPath
        ? await supabase.storage.from(BUCKET).createSignedUrl(thumbPath, 7 * 24 * 60 * 60)
        : { data: null };

      return Response.json(
        {
          mediaId,
          objectPath,
          thumbPath: thumbPath || "",
          uploadUrl: uploadSigned.signedUrl,
          thumbUploadUrl: thumbSignedUrl,
          readUrl: readSigned?.signedUrl || "",
          thumbReadUrl: thumbReadSigned?.signedUrl || "",
          expiresAtIso,
        },
        { headers: CORS_HEADERS }
      );
    }

    if (action === "sign_download") {
      const objectPath = String(body.objectPath || "");
      const { data: row } = await supabase
        .from("media_objects")
        .select("*")
        .eq("object_path", objectPath)
        .maybeSingle();

      if (!row || row.deleted_from_storage || new Date(row.expires_at).getTime() <= Date.now()) {
        return Response.json({ error: "Media expired after 7 days", expired: true }, { status: 410, headers: CORS_HEADERS });
      }

      const canDownload = await verifyConversationAccess(
        token,
        uid,
        String(row.conversation_id || "personal"),
        String(row.owner_uid || ""),
        "download"
      );
      if (!canDownload) {
        return Response.json({ error: "Forbidden: no access to this media object" }, { status: 403, headers: CORS_HEADERS });
      }

      const { data: readSigned, error } = await supabase.storage
        .from(BUCKET)
        .createSignedUrl(objectPath, 3600);
      if (error || !readSigned) {
        return Response.json({ error: "Unable to sign read URL" }, { status: 404, headers: CORS_HEADERS });
      }
      return Response.json({ readUrl: readSigned.signedUrl, expiresAtIso: row.expires_at }, { headers: CORS_HEADERS });
    }

    if (action === "usage") {
      const { data: rows } = await supabase
        .from("media_objects")
        .select("size_bytes")
        .eq("owner_uid", uid)
        .eq("deleted_from_storage", false);

      const totalBytes = (rows || []).reduce((acc, r) => acc + Number(r.size_bytes || 0), 0);
      return Response.json(
        {
          activeFiles: (rows || []).length,
          totalBytes,
        },
        { headers: CORS_HEADERS }
      );
    }

    return Response.json({ error: "Unsupported action" }, { status: 400, headers: CORS_HEADERS });
  } catch (err) {
    return Response.json({ error: String(err) }, { status: 401, headers: CORS_HEADERS });
  }
});
