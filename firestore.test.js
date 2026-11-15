const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const SUPER_ADMIN_UID = "2bL4lJTTt9WMILpCclHeKNYOdz62";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const pastTs = () => new Date(Date.now() - 2000);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read users, conversations, or adminLogs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("conversations").get());
  await assertFails(unauthDb.collection("adminLogs").get());
});

test("User can create own profile with isSuspended=false, cannot self-suspend or spoof UID", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastTs();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice_dev",
      displayName: "Alice",
      isSuspended: false,
      profileVisibility: "everyone",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Cannot create profile for Bob
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      username: "bob_dev",
      displayName: "Bob",
      isSuspended: false,
      profileVisibility: "everyone",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Cannot inject isSuspended: true on create or update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isSuspended: true,
      updatedAt: pastTs(),
    })
  );
});

test("PII Isolation: Bob cannot read Alice's private account document", async () => {
  const ts = pastTs();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("private")
      .doc("account")
      .set({
        userId: ALICE_UID,
        email: "alice@example.com",
        updatedAt: ts,
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("private")
      .doc("account")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("private")
      .doc("account")
      .get()
  );
});

test("Unique username reservation blocks duplicate claims", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("usernames").doc("cool_user").set({
      username: "cool_user",
      userId: ALICE_UID,
      displayName: "Alice",
      createdAt: pastTs(),
    })
  );

  await assertFails(
    bobDb.collection("usernames").doc("cool_user").set({
      username: "cool_user",
      userId: BOB_UID,
      displayName: "Bob",
      createdAt: pastTs(),
    })
  );
});

test("Conversation & Message isolation: non-member cannot read or send messages", async () => {
  const ts = pastTs();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("conversations").doc("conv_1").set({
      type: "direct",
      title: "Private Chat",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      adminUids: [ALICE_UID],
      isPublic: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb
      .collection("conversations")
      .doc("conv_1")
      .collection("messages")
      .doc("msg_1")
      .set({
        conversationId: "conv_1",
        senderId: ALICE_UID,
        senderName: "Alice",
        memberUids: [ALICE_UID],
        isPublic: false,
        text: "Secret message",
        mediaType: "text",
        createdAt: ts,
        updatedAt: ts,
      })
  );

  // Bob is not a member
  await assertFails(bobDb.collection("conversations").doc("conv_1").get());
  await assertFails(
    bobDb
      .collection("conversations")
      .doc("conv_1")
      .collection("messages")
      .doc("msg_1")
      .get()
  );
  await assertFails(
    bobDb
      .collection("conversations")
      .doc("conv_1")
      .collection("messages")
      .doc("msg_2")
      .set({
        conversationId: "conv_1",
        senderId: BOB_UID,
        senderName: "Bob",
        memberUids: [ALICE_UID],
        isPublic: false,
        text: "Intrusion",
        mediaType: "text",
        createdAt: ts,
        updatedAt: ts,
      })
  );

  // Alice can query her own conversations
  await assertSucceeds(
    aliceDb
      .collection("conversations")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );
});

test("Sender impersonation is rejected on message creation", async () => {
  const ts = pastTs();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("conversations").doc("conv_2").set({
      type: "group",
      title: "Team",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      adminUids: [ALICE_UID],
      isPublic: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb
      .collection("conversations")
      .doc("conv_2")
      .collection("messages")
      .doc("spoof_msg")
      .set({
        conversationId: "conv_2",
        senderId: ALICE_UID,
        senderName: "Alice",
        memberUids: [ALICE_UID, BOB_UID],
        isPublic: false,
        text: "Spoofed",
        mediaType: "text",
        createdAt: ts,
        updatedAt: ts,
      })
  );
});

test("Super Admin lock: ordinary user blocked from adminLogs, platformSettings, and announcements; exact UID allowed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const adminDb = testEnv.authenticatedContext(SUPER_ADMIN_UID).firestore();
  const ts = pastTs();

  await assertFails(aliceDb.collection("adminLogs").get());
  await assertFails(
    aliceDb.collection("adminLogs").doc("log_1").set({
      actorId: ALICE_UID,
      action: "ESCALATE",
      targetId: ALICE_UID,
      details: "Attempt",
      createdAt: ts,
    })
  );
  await assertFails(
    aliceDb.collection("platformAnnouncements").doc("ann_1").set({
      title: "Fake",
      body: "Fake",
      priority: "urgent",
      createdBy: ALICE_UID,
      createdAt: ts,
    })
  );
  await assertFails(
    aliceDb.collection("platformSettings").doc("global").set({
      maxUploadMb: 50,
      allowNewRegistrations: true,
      maintenanceMode: false,
      updatedBy: ALICE_UID,
      updatedAt: ts,
    })
  );

  // Exact Super Admin UID succeeds
  await assertSucceeds(
    adminDb.collection("adminLogs").doc("log_1").set({
      actorId: SUPER_ADMIN_UID,
      action: "SUSPEND_USER",
      targetId: BOB_UID,
      details: "Abuse verified",
      createdAt: ts,
    })
  );
  await assertSucceeds(adminDb.collection("adminLogs").get());
  await assertSucceeds(
    adminDb.collection("platformAnnouncements").doc("ann_1").set({
      title: "System Maintenance",
      body: "Scheduled upgrade",
      priority: "normal",
      createdBy: SUPER_ADMIN_UID,
      createdAt: ts,
    })
  );
  await assertSucceeds(
    adminDb.collection("platformSettings").doc("global").set({
      maxUploadMb: 25,
      allowNewRegistrations: true,
      maintenanceMode: false,
      updatedBy: SUPER_ADMIN_UID,
      updatedAt: ts,
    })
  );
});

test("Group adminsOnlyMessaging blocks non-admin messages and allows admin messages + member appearance updates", async () => {
  const ts = pastTs();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("conversations").doc("grp_broadcast").set({
      type: "group",
      title: "Admins Broadcast Group",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      adminUids: [ALICE_UID],
      isPublic: false,
      adminsOnlyMessaging: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Non-admin Bob is rejected when adminsOnlyMessaging is true
  await assertFails(
    bobDb
      .collection("conversations")
      .doc("grp_broadcast")
      .collection("messages")
      .doc("bob_msg")
      .set({
        conversationId: "grp_broadcast",
        senderId: BOB_UID,
        senderName: "Bob",
        memberUids: [ALICE_UID, BOB_UID],
        isPublic: false,
        text: "Unauthorized chatter",
        mediaType: "text",
        createdAt: ts,
        updatedAt: ts,
      })
  );

  // Admin Alice succeeds
  await assertSucceeds(
    aliceDb
      .collection("conversations")
      .doc("grp_broadcast")
      .collection("messages")
      .doc("alice_msg")
      .set({
        conversationId: "grp_broadcast",
        senderId: ALICE_UID,
        senderName: "Alice",
        memberUids: [ALICE_UID, BOB_UID],
        isPublic: false,
        text: "Official group update",
        mediaType: "text",
        createdAt: ts,
        updatedAt: ts,
      })
  );

  // Bob can customize his own per-chat theme & wallpaper state
  await assertSucceeds(
    bobDb
      .collection("conversations")
      .doc("grp_broadcast")
      .collection("members")
      .doc(BOB_UID)
      .set({
        conversationId: "grp_broadcast",
        userId: BOB_UID,
        role: "member",
        memberUids: [ALICE_UID, BOB_UID],
        customTheme: "ocean_blue",
        customWallpaper: "starlight_night",
        isMuted: false,
        isArchived: false,
        updatedAt: ts,
      })
  );
});

