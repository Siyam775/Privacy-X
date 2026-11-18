import * as admin from "firebase-admin";
import { onRequest, onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { defineSecret } from "firebase-functions/params";

admin.initializeApp();

const SUPER_ADMIN_UID = "2bL4lJTTt9WMILpCclHeKNYOdz62";
const DATABASE_ID = "ai-studio-android-privacyx-5767d797-31a9-488e-9a25-d28909171c2e";
const SUPABASE_BUCKET = "chat-media";
const RETENTION_MS = 7 * 24 * 60 * 60 * 1000;

const supabaseUrlSecret = defineSecret("SUPABASE_URL");
const supabaseServiceRoleSecret = defineSecret("SUPABASE_SERVICE_ROLE_KEY");

function getDb() {
  const db = admin.firestore();
  try {
    db.settings({ databaseId: DATABASE_ID });
  } catch {
    // Already initialized
  }
  return db;
}

async function createSupabaseSignedUploadUrl(
  baseUrl: string,
  serviceRoleKey: string,
  objectPath: string
): Promise<string> {
  const cleanUrl = baseUrl.replace(/\/+$/, "");
  const resp = await fetch(`${cleanUrl}/storage/v1/object/upload/sign/${SUPABASE_BUCKET}/${objectPath}`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${serviceRoleKey}`,
      apikey: serviceRoleKey,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({}),
  });
  if (!resp.ok) {
    const errText = await resp.text();
    throw new Error(`Supabase sign upload failed: HTTP ${resp.status} ${errText}`);
  }
  const data = (await resp.json()) as { url?: string };
  if (!data.url) throw new Error("Supabase response missing signed upload URL");
  return data.url.startsWith("http") ? data.url : `${cleanUrl}/storage/v1${data.url}`;
}

async function createSupabaseSignedReadUrl(
  baseUrl: string,
  serviceRoleKey: string,
  objectPath: string,
  expiresInSeconds: number
): Promise<string> {
  const cleanUrl = baseUrl.replace(/\/+$/, "");
  const resp = await fetch(`${cleanUrl}/storage/v1/object/sign/${SUPABASE_BUCKET}/${objectPath}`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${serviceRoleKey}`,
      apikey: serviceRoleKey,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ expiresIn: expiresInSeconds }),
  });
  if (!resp.ok) {
    const errText = await resp.text();
    throw new Error(`Supabase sign read failed: HTTP ${resp.status} ${errText}`);
  }
  const data = (await resp.json()) as { signedURL?: string; signedUrl?: string };
  const rel = data.signedURL || data.signedUrl;
  if (!rel) throw new Error("Supabase response missing signed read URL");
  return rel.startsWith("http") ? rel : `${cleanUrl}/storage/v1${rel}`;
}

async function deleteSupabaseObject(
  baseUrl: string,
  serviceRoleKey: string,
  objectPath: string
): Promise<boolean> {
  const cleanUrl = baseUrl.replace(/\/+$/, "");
  const resp = await fetch(`${cleanUrl}/storage/v1/object/${SUPABASE_BUCKET}/${objectPath}`, {
    method: "DELETE",
    headers: {
      Authorization: `Bearer ${serviceRoleKey}`,
      apikey: serviceRoleKey,
    },
  });
  return resp.ok || resp.status === 404;
}

/**
 * Trusted Firebase Auth -> Supabase Storage Bridge.
 * Verifies Firebase ID token, checks conversation membership/ownership,
 * issues short-lived signed upload URLs + 7-day read URLs, and records 7-day expiration metadata.
 */
export const authorizeSupabaseMedia = onRequest(
  { secrets: [supabaseUrlSecret, supabaseServiceRoleSecret], cors: true },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({ error: "Method Not Allowed" });
      return;
    }
    const authHeader = req.headers.authorization;
    if (!authHeader?.startsWith("Bearer ")) {
      res.status(401).json({ error: "Missing Firebase ID token" });
      return;
    }
    try {
      const idToken = authHeader.substring(7);
      const decoded = await admin.auth().verifyIdToken(idToken);
      const uid = decoded.uid;
      const db = getDb();
      const action = typeof req.body?.action === "string" ? req.body.action : "sign_upload";
      const baseUrl = supabaseUrlSecret.value();
      const serviceKey = supabaseServiceRoleSecret.value();

      if (action === "sign_upload") {
        const conversationId = String(req.body?.conversationId || "personal").replace(/[^a-zA-Z0-9_-]/g, "_");
        const mediaId = String(req.body?.mediaId || `m_${Date.now()}`).replace(/[^a-zA-Z0-9_-]/g, "_");
        const category = String(req.body?.category || "image");
        const mimeType = String(req.body?.mimeType || "application/octet-stream");
        const fileName = String(req.body?.fileName || "file");
        const sizeBytes = Number(req.body?.sizeBytes || 0);
        const messageId = typeof req.body?.messageId === "string" ? req.body.messageId : "";
        const hasThumbnail = Boolean(req.body?.hasThumbnail);

        if (!["image", "voice", "document", "video", "avatar", "story", "post"].includes(category)) {
          res.status(400).json({ error: "Unsupported media category." });
          return;
        }
        if (category === "video" && !mimeType.toLowerCase().startsWith("video/")) {
          res.status(400).json({ error: "Video files must use a video MIME type." });
          return;
        }
        if (category !== "video" && mimeType.toLowerCase().startsWith("video/")) {
          res.status(400).json({ error: "Video MIME types must use the video category." });
          return;
        }
        if (sizeBytes < 0 || sizeBytes > 25 * 1024 * 1024) {
          res.status(400).json({ error: "Media file exceeds 25MB limit." });
          return;
        }

        // Verify conversation membership if targeting a conversation
        if (conversationId !== "personal" && conversationId !== "avatars" && conversationId !== "stories" && conversationId !== "posts") {
          const convSnap = await db.doc(`conversations/${conversationId}`).get();
          if (!convSnap.exists) {
            res.status(404).json({ error: "Conversation not found" });
            return;
          }
          const members: string[] = Array.isArray(convSnap.get("memberUids")) ? convSnap.get("memberUids") : [];
          if (!members.includes(uid) && uid !== SUPER_ADMIN_UID) {
            res.status(403).json({ error: "Not a member of this conversation" });
            return;
          }
        }

        const ext = fileName.includes(".") ? fileName.split(".").pop()?.replace(/[^a-zA-Z0-9]/g, "") : "bin";
        const objectPath = `${uid}/${conversationId}/${mediaId}.${ext || "bin"}`;
        const thumbPath = hasThumbnail ? `${uid}/${conversationId}/${mediaId}_thumb.webp` : "";
        const expiresAtMs = Date.now() + RETENTION_MS;

        const uploadUrl = await createSupabaseSignedUploadUrl(baseUrl, serviceKey, objectPath);
        const thumbUploadUrl = thumbPath
          ? await createSupabaseSignedUploadUrl(baseUrl, serviceKey, thumbPath)
          : "";
        const readUrl = await createSupabaseSignedReadUrl(baseUrl, serviceKey, objectPath, 7 * 24 * 60 * 60);
        const thumbReadUrl = thumbPath
          ? await createSupabaseSignedReadUrl(baseUrl, serviceKey, thumbPath, 7 * 24 * 60 * 60)
          : "";

        await db.collection("mediaObjects").doc(mediaId).set({
          mediaId,
          objectPath,
          thumbPath,
          ownerUid: uid,
          conversationId,
          messageId,
          category,
          mimeType,
          sizeBytes,
          expiresAtMs,
          expiresAt: admin.firestore.Timestamp.fromMillis(expiresAtMs),
          deletedConfirmed: false,
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
        });

        res.status(200).json({
          mediaId,
          objectPath,
          thumbPath,
          uploadUrl,
          thumbUploadUrl,
          readUrl,
          thumbReadUrl,
          expiresAtMs,
        });
        return;
      }

      if (action === "sign_download") {
        const objectPath = String(req.body?.objectPath || "");
        const snap = await db.collection("mediaObjects").where("objectPath", "==", objectPath).limit(1).get();
        if (snap.empty) {
          res.status(404).json({ error: "Media object not found" });
          return;
        }
        const doc = snap.docs[0];
        const expiresAtMs = Number(doc.get("expiresAtMs") || 0);
        if (doc.get("deletedConfirmed") === true || expiresAtMs <= Date.now()) {
          res.status(410).json({ error: "Media expired after 7 days", expired: true });
          return;
        }
        const readUrl = await createSupabaseSignedReadUrl(baseUrl, serviceKey, objectPath, 3600);
        res.status(200).json({ readUrl, expiresAtMs });
        return;
      }

      if (action === "usage") {
        const snap = await db
          .collection("mediaObjects")
          .where("ownerUid", "==", uid)
          .where("deletedConfirmed", "==", false)
          .get();
        const totalBytes = snap.docs.reduce((acc, d) => acc + Number(d.get("sizeBytes") || 0), 0);
        res.status(200).json({ activeFiles: snap.size, totalBytes });
        return;
      }

      res.status(400).json({ error: "Unsupported action" });
    } catch (err) {
      res.status(401).json({ error: "Unauthorized or invalid request", detail: String(err) });
    }
  }
);

async function runExpiredMediaCleanupInternal(baseUrl: string, serviceKey: string): Promise<{ checked: number; deleted: number }> {
  const db = getDb();
  const nowMs = Date.now();
  const expiredSnap = await db
    .collection("mediaObjects")
    .where("deletedConfirmed", "==", false)
    .where("expiresAtMs", "<=", nowMs)
    .limit(150)
    .get();

  let deleted = 0;
  for (const docSnap of expiredSnap.docs) {
    const objectPath = String(docSnap.get("objectPath") || "");
    const thumbPath = String(docSnap.get("thumbPath") || "");
    const conversationId = String(docSnap.get("conversationId") || "");
    const messageId = String(docSnap.get("messageId") || "");

    const mainOk = objectPath ? await deleteSupabaseObject(baseUrl, serviceKey, objectPath) : true;
    const thumbOk = thumbPath ? await deleteSupabaseObject(baseUrl, serviceKey, thumbPath) : true;

    // Only mark metadata expired AFTER storage deletion is confirmed
    if (mainOk && thumbOk) {
      await docSnap.ref.update({
        deletedConfirmed: true,
        deletedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      if (conversationId && messageId) {
        const msgRef = db.doc(`conversations/${conversationId}/messages/${messageId}`);
        const msgSnap = await msgRef.get();
        if (msgSnap.exists) {
          await msgRef.update({
            mediaUrl: "",
            mediaThumbnailUrl: "",
            mediaExpired: true,
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
          });
        }
      }
      deleted += 1;
    }
  }
  return { checked: expiredSnap.size, deleted };
}

/**
 * Automated hourly 7-day cloud media cleanup job.
 */
export const cleanupExpiredCloudMedia = onSchedule(
  { schedule: "every 60 minutes", secrets: [supabaseUrlSecret, supabaseServiceRoleSecret] },
  async () => {
    await runExpiredMediaCleanupInternal(supabaseUrlSecret.value(), supabaseServiceRoleSecret.value());
  }
);

/**
 * Authenticated manual/app-open cleanup trigger endpoint.
 */
export const triggerExpiredMediaCleanup = onRequest(
  { secrets: [supabaseUrlSecret, supabaseServiceRoleSecret], cors: true },
  async (req, res) => {
    const authHeader = req.headers.authorization;
    if (!authHeader?.startsWith("Bearer ")) {
      res.status(401).json({ error: "Missing Firebase ID token" });
      return;
    }
    try {
      await admin.auth().verifyIdToken(authHeader.substring(7));
      const result = await runExpiredMediaCleanupInternal(
        supabaseUrlSecret.value(),
        supabaseServiceRoleSecret.value()
      );
      res.status(200).json(result);
    } catch (err) {
      res.status(401).json({ error: "Unauthorized", detail: String(err) });
    }
  }
);

/**
 * Synchronizes Super Admin custom claim strictly for the designated UID.
 */
export const syncSuperAdminClaim = onCall(async (request) => {
  if (!request.auth || request.auth.uid !== SUPER_ADMIN_UID) {
    throw new HttpsError("permission-denied", "Restricted to the designated Super Admin UID.");
  }
  await admin.auth().setCustomUserClaims(SUPER_ADMIN_UID, { superAdmin: true });
  return { status: "ok", uid: SUPER_ADMIN_UID };
});

/**
 * Dispatches FCM push notifications whenever a new message is written to a conversation.
 */
export const onMessageCreatedNotify = onDocumentCreated(
  {
    document: "conversations/{conversationId}/messages/{messageId}",
    database: DATABASE_ID,
  },
  async (event) => {
    const msg = event.data?.data();
    if (!msg) return;

    const senderId: string = msg.senderId || "";
    const senderName: string = msg.senderName || "New Message";
    const memberUids: string[] = Array.isArray(msg.memberUids) ? msg.memberUids : [];
    const recipients = memberUids.filter((u) => u && u !== senderId);
    if (recipients.length === 0) return;

    const db = getDb();
    const tokens: string[] = [];
    for (const uid of recipients.slice(0, 50)) {
      const privDoc = await db.doc(`users/${uid}/private/account`).get();
      const token = privDoc.get("fcmToken");
      if (typeof token === "string" && token.length > 10) {
        tokens.push(token);
      }
    }

    if (tokens.length === 0) return;
    await admin.messaging().sendEachForMulticast({
      tokens,
      notification: {
        title: senderName,
        body: typeof msg.text === "string" && msg.text.length > 0 ? msg.text.slice(0, 140) : `Sent ${msg.mediaType || "attachment"}`,
      },
      data: {
        conversationId: event.params.conversationId,
        messageId: event.params.messageId,
      },
    });
  }
);
