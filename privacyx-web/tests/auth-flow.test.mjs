import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import http from "node:http";
import { initializeApp, deleteApp } from "firebase/app";
import {
  getAuth,
  connectAuthEmulator,
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
  updateProfile,
  signOut,
} from "firebase/auth";
import {
  getFirestore,
  connectFirestoreEmulator,
  doc,
  getDoc,
  setDoc,
  runTransaction,
  serverTimestamp,
} from "firebase/firestore";

const PROJECT_ID = process.env.GCLOUD_PROJECT || "privacy-x";
const DATABASE_ID = "ai-studio-android-privacyx-5767d797-31a9-488e-9a25-d28909171c2e";
const AUTH_EMULATOR_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST || "127.0.0.1:9099";
const FIRESTORE_EMULATOR_HOST = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085";

function createFirebaseTestClient(name) {
  // Mirrors privacyx-web/src/lib/firebase.ts: initializes WITHOUT appId required
  const app = initializeApp(
    {
      apiKey: "AIzaSyCLmUK0dvsXGCHK6-8YroGGSQm4-S9an_I",
      authDomain: `${PROJECT_ID}.firebaseapp.com`,
      projectId: PROJECT_ID,
      storageBucket: `${PROJECT_ID}.firebasestorage.app`,
      messagingSenderId: "891277106805",
    },
    name
  );
  const auth = getAuth(app);
  connectAuthEmulator(auth, `http://${AUTH_EMULATOR_HOST}`, { disableWarnings: true });
  const db = getFirestore(app, DATABASE_ID);
  const [host, portStr] = FIRESTORE_EMULATOR_HOST.split(":");
  connectFirestoreEmulator(db, host, Number(portStr));
  return { app, auth, db };
}

test("Production static export serves /signup and private routes cleanly without setup-pending text", async () => {
  const outDir = path.resolve(process.cwd(), "privacyx-web/out");
  assert.equal(fs.existsSync(outDir), true, "privacyx-web/out must exist from production build");

  const server = http.createServer((req, res) => {
    const cleanUrl = (req.url || "/").split("?")[0];
    let filePath = path.join(outDir, cleanUrl === "/" ? "index.html" : `${cleanUrl.replace(/\/$/, "")}/index.html`);
    if (!fs.existsSync(filePath)) {
      filePath = path.join(outDir, cleanUrl);
    }
    if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
      res.statusCode = 404;
      res.end("Not found");
      return;
    }
    res.statusCode = 200;
    res.setHeader("Content-Type", "text/html; charset=utf-8");
    res.end(fs.readFileSync(filePath));
  });

  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const addr = server.address();
  const baseUrl = `http://127.0.0.1:${addr.port}`;

  try {
    const homeRes = await fetch(`${baseUrl}/`);
    assert.equal(homeRes.status, 200);
    const homeHtml = await homeRes.text();
    assert.match(homeHtml, /Create account/);
    assert.match(homeHtml, /Sign in/);

    const signupRes = await fetch(`${baseUrl}/signup/`);
    assert.equal(signupRes.status, 200);
    const signupHtml = await signupRes.text();
    assert.doesNotMatch(signupHtml, /Sign up setup pending/);
    assert.match(signupHtml, /PrivacyX/);

    for (const privateRoute of ["/chat/", "/profile/", "/calls/", "/communities/", "/social/", "/stories/"]) {
      const routeRes = await fetch(`${baseUrl}${privateRoute}`);
      assert.equal(routeRes.status, 200, `Route ${privateRoute} should build & serve cleanly`);
      const routeHtml = await routeRes.text();
      assert.match(routeHtml, /Checking session/, `Private route ${privateRoute} must gate content until auth resolves`);
    }
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});

test("End-to-end registration, username reservation, duplicate conflict prevention, login/logout & session rules", async () => {
  const clientA = createFirebaseTestClient(`clientA_${Date.now()}`);
  const clientB = createFirebaseTestClient(`clientB_${Date.now()}`);

  const uniqueSuffix = Date.now().toString(36);
  const emailA = `alice_${uniqueSuffix}@example.com`;
  const emailB = `bob_${uniqueSuffix}@example.com`;
  const password = "StrongPassword123!";
  const usernameA = `alice_${uniqueSuffix}`.slice(0, 28);
  const usernameB = `bob_${uniqueSuffix}`.slice(0, 28);

  try {
    // 1. Register User A with email & password
    const credA = await createUserWithEmailAndPassword(clientA.auth, emailA, password);
    await updateProfile(credA.user, { displayName: "Alice Morgan" });
    assert.ok(credA.user.uid);
    assert.equal(clientA.auth.currentUser?.displayName, "Alice Morgan");

    // 2. Prevent duplicate email registration
    await assert.rejects(
      () => createUserWithEmailAndPassword(clientB.auth, emailA, password),
      (err) => err?.code === "auth/email-already-in-use"
    );

    // 3. Username setup & profile creation transaction (exact AuthRepository flow)
    const uidA = credA.user.uid;
    const usernameRefA = doc(clientA.db, "usernames", usernameA);
    assert.equal((await getDoc(usernameRefA)).exists(), false);

    await runTransaction(clientA.db, async (tx) => {
      const existingUsername = await tx.get(usernameRefA);
      assert.equal(existingUsername.exists(), false);
      const userRef = doc(clientA.db, "users", uidA);
      const privateRef = doc(clientA.db, "users", uidA, "private", "account");
      const settingsRef = doc(clientA.db, "users", uidA, "userSettings", "preferences");

      tx.set(usernameRefA, {
        username: usernameA,
        userId: uidA,
        displayName: "Alice Morgan",
        photoUrl: "",
        createdAt: serverTimestamp(),
      });
      tx.set(userRef, {
        userId: uidA,
        username: usernameA,
        displayName: "Alice Morgan",
        photoUrl: "",
        bio: "Encrypted web test",
        statusText: "Available",
        isOnline: true,
        lastSeen: serverTimestamp(),
        isSuspended: false,
        profileVisibility: "everyone",
        lastSeenPrivacy: "everyone",
        storyPrivacy: "public",
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      tx.set(privateRef, {
        userId: uidA,
        email: emailA,
        closeFriendsIds: [],
        hiddenStoryUserIds: [],
        savedPostIds: [],
        updatedAt: serverTimestamp(),
      });
      tx.set(settingsRef, {
        userId: uidA,
        themeId: "material_liquid_glass",
        appearanceMode: "system",
        accentColorHex: "",
        reducedMotion: false,
        notificationsEnabled: true,
        quietHoursEnabled: false,
        soundEnabled: true,
        vibrationEnabled: true,
        showNotificationPreview: true,
        autoDownloadWifi: true,
        autoDownloadCellular: false,
        aiConsentGranted: false,
        updatedAt: serverTimestamp(),
      });
    });

    // Register web session with >40 char platform string truncated to 40 chars (rules-compliant)
    const longPlatform = "Web Linux x86_64 Custom Very Long UserAgent Platform Name".slice(0, 40);
    await setDoc(doc(clientA.db, "users", uidA, "sessions", `web_session_${Date.now()}`), {
      userId: uidA,
      deviceName: "Web Browser",
      platform: longPlatform,
      isActive: true,
      lastActiveAt: serverTimestamp(),
      createdAt: serverTimestamp(),
    });

    const profileSnapA = await getDoc(doc(clientA.db, "users", uidA));
    assert.equal(profileSnapA.exists(), true);
    assert.equal(profileSnapA.data().username, usernameA);

    // 4. Register User B and verify duplicate username conflict is blocked
    const credB = await createUserWithEmailAndPassword(clientB.auth, emailB, password);
    const takenSnap = await getDoc(doc(clientB.db, "usernames", usernameA));
    assert.equal(takenSnap.exists(), true, "Username taken check must detect existing claim");

    await assert.rejects(
      () =>
        setDoc(doc(clientB.db, "usernames", usernameA), {
          username: usernameA,
          userId: credB.user.uid,
          displayName: "Bob Thief",
          photoUrl: "",
          createdAt: serverTimestamp(),
        }),
      /permission-denied|PERMISSION_DENIED|Missing or insufficient permissions/i
    );

    // User B claims a distinct username cleanly
    await setDoc(doc(clientB.db, "usernames", usernameB), {
      username: usernameB,
      userId: credB.user.uid,
      displayName: "Bob",
      photoUrl: "",
      createdAt: serverTimestamp(),
    });
    assert.equal((await getDoc(doc(clientB.db, "usernames", usernameB))).exists(), true);

    // 5. Logout & unauthorized access block
    await signOut(clientA.auth);
    assert.equal(clientA.auth.currentUser, null);
    await assert.rejects(
      () => getDoc(doc(clientA.db, "users", uidA)),
      /permission-denied|PERMISSION_DENIED|Missing or insufficient permissions/i
    );

    // 6. Wrong password rejection & successful login
    await assert.rejects(
      () => signInWithEmailAndPassword(clientA.auth, emailA, "WrongPassword!"),
      (err) => err?.code === "auth/wrong-password" || err?.code === "auth/invalid-credential"
    );

    const reloginA = await signInWithEmailAndPassword(clientA.auth, emailA, password);
    assert.equal(reloginA.user.uid, uidA);
    const profileAfterLogin = await getDoc(doc(clientA.db, "users", uidA));
    assert.equal(profileAfterLogin.exists(), true);
    assert.equal(profileAfterLogin.data().username, usernameA);
  } finally {
    await deleteApp(clientA.app).catch(() => undefined);
    await deleteApp(clientB.app).catch(() => undefined);
  }
});
