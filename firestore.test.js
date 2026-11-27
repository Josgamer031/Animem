const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_1";
const BOB_UID = "bob_uid_2";
const EPISODE_KEY = "jkanime__one-piece__1";

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

function buildValidCommentPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    authorName: "KitsuneFan",
    authorAvatarUrl: "https://example.com/avatar.png",
    episodeKey: EPISODE_KEY,
    animeTitle: "One Piece",
    episodeNumber: 1,
    text: "Excelente primer episodio!",
    spoiler: false,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function buildValidFavoritePayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    animeSlug: "one-piece",
    animeTitle: "One Piece",
    coverUrl: "https://example.com/onepiece.jpg",
    type: "TV",
    status: "En emision",
    totalEpisodesText: "1100+",
    providerId: "jkanime",
    profileId: "default",
    watchStatus: "WATCHING",
    addedAtMillis: 1700000000000,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function buildValidHistoryPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    animeSlug: "one-piece",
    animeTitle: "One Piece",
    coverUrl: "https://example.com/onepiece.jpg",
    episodeNumber: 1,
    positionMs: 125000,
    durationMs: 1420000,
    updatedAtMillis: 1700000000000,
    providerId: "jkanime",
    episodeUrl: "https://jkanime.net/one-piece/1/",
    profileId: "default",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

test("Unauthenticated user cannot list, get, or write episode comments", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const commentsCol = unauthDb
    .collection("episodes")
    .doc(EPISODE_KEY)
    .collection("comments");

  await assertFails(commentsCol.orderBy("createdAt", "desc").limit(50).get());
  await assertFails(commentsCol.doc("comment_1").get());
  await assertFails(
    commentsCol.doc("comment_1").set(buildValidCommentPayload(ALICE_UID))
  );
});

test("Authenticated user can post, read, update own comment, and delete own comment", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const aliceComments = aliceDb
    .collection("episodes")
    .doc(EPISODE_KEY)
    .collection("comments");

  await assertSucceeds(
    aliceComments.doc("comment_1").set(buildValidCommentPayload(ALICE_UID))
  );

  await assertSucceeds(
    aliceComments.orderBy("createdAt", "desc").limit(60).get()
  );

  await assertSucceeds(
    aliceComments.doc("comment_1").update({
      text: "Editado: increíble animación!",
      spoiler: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(aliceComments.doc("comment_1").delete());
});

test("Authenticated user can post threaded reply with @mention and cannot mutate reply parent after create", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceComments = aliceDb
    .collection("episodes")
    .doc(EPISODE_KEY)
    .collection("comments");
  const bobComments = bobDb
    .collection("episodes")
    .doc(EPISODE_KEY)
    .collection("comments");

  await assertSucceeds(
    aliceComments.doc("root_1").set(buildValidCommentPayload(ALICE_UID))
  );

  await assertSucceeds(
    bobComments.doc("reply_1").set(
      buildValidCommentPayload(BOB_UID, {
        authorName: "BobOtaku",
        parentCommentId: "root_1",
        replyToCommentId: "root_1",
        replyToAuthorName: "KitsuneFan",
        text: "@KitsuneFan totalemente de acuerdo con esa escena!",
      })
    )
  );

  // Invalid parentCommentId format rejected
  await assertFails(
    bobComments.doc("reply_bad").set(
      buildValidCommentPayload(BOB_UID, {
        parentCommentId: "bad/id/with/slashes",
      })
    )
  );

  // Mutating parentCommentId on update rejected
  await assertFails(
    bobComments.doc("reply_1").update({
      parentCommentId: "other_root",
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Authenticated user can read another user's comment but cannot edit, delete, or spoof userId", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("episodes")
      .doc(EPISODE_KEY)
      .collection("comments")
      .doc("bob_comment")
      .set(buildValidCommentPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const aliceComments = aliceDb
    .collection("episodes")
    .doc(EPISODE_KEY)
    .collection("comments");

  await assertSucceeds(aliceComments.doc("bob_comment").get());
  await assertSucceeds(aliceComments.orderBy("createdAt", "desc").get());

  await assertFails(
    aliceComments.doc("bob_comment").update({
      text: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(aliceComments.doc("bob_comment").delete());

  await assertFails(
    aliceComments.doc("spoof_comment").set(buildValidCommentPayload(BOB_UID))
  );
});

test("Schema validation rejects @DocumentId id field, ghost fields, oversized text, and future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const aliceComments = aliceDb
    .collection("episodes")
    .doc(EPISODE_KEY)
    .collection("comments");

  await assertFails(
    aliceComments
      .doc("bad_id_field")
      .set(buildValidCommentPayload(ALICE_UID, { id: "bad_id_field" }))
  );

  await assertFails(
    aliceComments
      .doc("ghost_field")
      .set(buildValidCommentPayload(ALICE_UID, { isVerified: true }))
  );

  await assertFails(
    aliceComments
      .doc("empty_text")
      .set(buildValidCommentPayload(ALICE_UID, { text: "" }))
  );

  await assertFails(
    aliceComments
      .doc("long_text")
      .set(buildValidCommentPayload(ALICE_UID, { text: "a".repeat(501) }))
  );

  await assertFails(
    aliceComments
      .doc("future_ts")
      .set(
        buildValidCommentPayload(ALICE_UID, {
          createdAt: new Date(Date.now() + 60000),
        })
      )
  );

  await assertSucceeds(
    aliceComments.doc("valid_doc").set(buildValidCommentPayload(ALICE_UID))
  );
  await assertFails(
    aliceComments.doc("valid_doc").update({
      createdAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Cloud Favorites and History enforce strict per-user ownership and schema validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  const aliceFavRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("favorites")
    .doc("default__jkanime__one-piece");
  const aliceHistRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("history")
    .doc("default__jkanime__one-piece__1");

  // Unauthenticated denied
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("favorites").get()
  );

  // Alice can write, list, update, and delete her own favorites & history
  await assertSucceeds(aliceFavRef.set(buildValidFavoritePayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("favorites").get()
  );
  await assertSucceeds(
    aliceFavRef.update({
      watchStatus: "COMPLETED",
      updatedAt: new Date(Date.now() - 300),
    })
  );

  await assertSucceeds(aliceHistRef.set(buildValidHistoryPayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("history").get()
  );
  await assertSucceeds(
    aliceHistRef.update({
      positionMs: 420000,
      updatedAtMillis: 1700000099000,
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Bob cannot read, write, update, or delete Alice's backup
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("favorites").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("favorites")
      .doc("default__jkanime__one-piece")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("history")
      .doc("default__jkanime__one-piece__1")
      .set(buildValidHistoryPayload(ALICE_UID))
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("favorites")
      .doc("default__jkanime__one-piece")
      .delete()
  );

  // Invalid watchStatus or negative positionMs rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("favorites")
      .doc("bad_status")
      .set(buildValidFavoritePayload(ALICE_UID, { watchStatus: "UNKNOWN" }))
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("history")
      .doc("bad_pos")
      .set(buildValidHistoryPayload(ALICE_UID, { positionMs: -10 }))
  );

  // Alice can delete her own backup entries
  await assertSucceeds(aliceFavRef.delete());
  await assertSucceeds(aliceHistRef.delete());
});
