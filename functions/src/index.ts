import * as crypto from "crypto";
import * as admin from "firebase-admin";
import { onRequest, onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { defineSecret } from "firebase-functions/params";

admin.initializeApp();

const SUPER_ADMIN_UID = "2bL4lJTTt9WMILpCclHeKNYOdz62";
const DATABASE_ID = "ai-studio-android-privacyx-5767d797-31a9-488e-9a25-d28909171c2e";

const cloudinaryApiSecret = defineSecret("CLOUDINARY_API_SECRET");
const cloudinaryApiKey = defineSecret("CLOUDINARY_API_KEY");

/**
 * Generates a short-lived Cloudinary signed upload payload.
 * Client sets CLOUDINARY_SIGNING_ENDPOINT to this HTTPS URL.
 */
export const signCloudinaryUpload = onRequest(
  { secrets: [cloudinaryApiSecret, cloudinaryApiKey], cors: true },
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
      const folderRaw = typeof req.body?.folder === "string" ? req.body.folder : "privacyx/media";
      const safeFolder = folderRaw.replace(/[^a-zA-Z0-9/_-]/g, "").slice(0, 64) || "privacyx/media";
      const timestamp = Math.floor(Date.now() / 1000).toString();
      const paramsToSign = `folder=${safeFolder}&timestamp=${timestamp}${cloudinaryApiSecret.value()}`;
      const signature = crypto.createHash("sha1").update(paramsToSign).digest("hex");

      res.status(200).json({
        uid: decoded.uid,
        signature,
        timestamp,
        apiKey: cloudinaryApiKey.value(),
        folder: safeFolder,
      });
    } catch (err) {
      res.status(401).json({ error: "Invalid Firebase ID token", detail: String(err) });
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

    const db = admin.firestore();
    db.settings({ databaseId: DATABASE_ID });

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
