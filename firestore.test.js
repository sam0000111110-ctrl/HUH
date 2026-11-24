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
const ADMIN_UID = "admin_001";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user cannot read or write any protected collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("reports").get());
  await assertFails(unauthDb.collection("calls").doc("c1").get());
  await assertFails(unauthDb.collection("age_verifications").doc(ALICE_UID).get());
});

test("User profile creation blocks privilege escalation and allows safe defaults", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 1000);

  // Reject self-promotion on create
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Alice",
      isAdmin: true,
      ageVerified: true,
      pointsBalance: 99999,
      profileVisibility: "PUBLIC",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Allow safe create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Alice",
      isAdmin: false,
      isAgent: false,
      isHost: false,
      ageVerified: false,
      ageVerificationStatus: "UNVERIFIED",
      pointsBalance: 0,
      profileVisibility: "PUBLIC",
      directMessagePermission: "EVERYONE",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject post-create privilege escalation on update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAgent: true,
      pointsBalance: 5000,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Allow safe profile field update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bio: "Hello HUH!",
      profileVisibility: "FRIENDS_ONLY",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Cross-user isolation on private_settings, blocks, and age_verifications", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { admin: true }).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("blocks").doc(BOB_UID).set({
      blockedUid: BOB_UID,
      createdAt: now,
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("blocks").doc(BOB_UID).get()
  );

  // Age verification submission by owner (must be >= 18 and PENDING_REVIEW)
  await assertFails(
    aliceDb.collection("age_verifications").doc(ALICE_UID).set({
      userId: ALICE_UID,
      dob: "2012-01-01",
      ageYears: 14,
      acceptedTermsAt: now,
      acceptedPrivacyAt: now,
      status: "PENDING_REVIEW",
      submittedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("age_verifications").doc(ALICE_UID).set({
      userId: ALICE_UID,
      dob: "1998-05-12",
      ageYears: 28,
      acceptedTermsAt: now,
      acceptedPrivacyAt: now,
      status: "VERIFIED",
      submittedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("age_verifications").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      dob: "1998-05-12",
      ageYears: 28,
      acceptedTermsAt: now,
      acceptedPrivacyAt: now,
      status: "PENDING_REVIEW",
      submittedAt: now,
    })
  );
  await assertFails(
    bobDb.collection("age_verifications").doc(ALICE_UID).get()
  );
  await assertSucceeds(
    adminDb.collection("age_verifications").doc(ALICE_UID).update({
      status: "VERIFIED",
      reviewedBy: ADMIN_UID,
    })
  );
});

test("Call signaling and private chats restrict access strictly to participants", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext("eve_789").firestore();

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").set({
      callId: "call_1",
      roomName: "ayer_1on1_call_1",
      callerId: ALICE_UID,
      callerName: "Alice",
      calleeId: BOB_UID,
      callType: "VIDEO",
      status: "DIALING",
      updatedAt: Date.now(),
    })
  );
  await assertSucceeds(bobDb.collection("calls").doc("call_1").get());
  await assertFails(eveDb.collection("calls").doc("call_1").get());

  // Cannot impersonate another caller
  await assertFails(
    eveDb.collection("calls").doc("call_2").set({
      callId: "call_2",
      roomName: "ayer_1on1_call_2",
      callerId: ALICE_UID,
      callerName: "Alice",
      calleeId: BOB_UID,
      callType: "AUDIO",
      status: "DIALING",
      updatedAt: Date.now(),
    })
  );

  // Private chat messages strictly scoped to participantIds
  await assertSucceeds(
    aliceDb.collection("private_chats").doc("alice_123_bob_456").collection("messages").doc("m1").set({
      senderId: ALICE_UID,
      recipientId: BOB_UID,
      participantIds: [ALICE_UID, BOB_UID],
      text: "Hello Bob!",
      timestamp: Date.now(),
    })
  );
  await assertSucceeds(
    bobDb.collection("private_chats").doc("alice_123_bob_456").collection("messages").where("participantIds", "array-contains", BOB_UID).get()
  );
  await assertFails(
    eveDb.collection("private_chats").doc("alice_123_bob_456").collection("messages").get()
  );
});

test("Moderation reports enforce reporter identity and admin-only resolution", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { admin: true }).firestore();

  await assertSucceeds(
    aliceDb.collection("reports").doc("rep_1").set({
      reporterId: ALICE_UID,
      reporterName: "Alice",
      reportType: "USER_PROFILE",
      targetUserId: BOB_UID,
      targetUserName: "Bob",
      reason: "Harassment",
      details: "Abusive chat",
      status: "PENDING_REVIEW",
      timestamp: Date.now(),
    })
  );

  await assertFails(bobDb.collection("reports").doc("rep_1").get());
  await assertFails(
    aliceDb.collection("reports").doc("rep_1").update({ status: "ACTION_TAKEN" })
  );
  await assertSucceeds(
    adminDb.collection("reports").doc("rep_1").update({ status: "ACTION_TAKEN" })
  );
});
