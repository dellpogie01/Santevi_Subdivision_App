import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { after, before, beforeEach, test } from "node:test";
import assert from "node:assert/strict";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment
} from "@firebase/rules-unit-testing";
import {
  deleteDoc,
  doc,
  getDoc,
  runTransaction,
  serverTimestamp,
  setDoc,
  updateDoc
} from "firebase/firestore";
import { ref as storageRef, uploadBytes, getBytes, deleteObject } from "firebase/storage";

const projectId = "demo-santevi-rules";
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const firestoreRulesPath = process.env.SANTEVI_FIRESTORE_RULES_TEST_FILE || path.join(root, "firestore.rules");
let testEnv;

const records = {
  announcements: {
    id: "announcement-1",
    data: { title: "Notice", date: "2026-01-01", summary: "Short", message: "Details", publicationStatus: "published", createdAt: new Date("2025-12-01T00:00:00Z") }
  },
  amenity_reservations: {
    id: "reservation-1",
    data: {
      amenityName: "Clubhouse", date: "2026-01-01", startTime: "10:00", instructions: "Reserve with HOA", publicationStatus: "published",
      createdAt: new Date("2025-12-01T00:00:00Z")
    }
  },
  community_events: {
    id: "event-1",
    data: { eventName: "Meeting", date: "2026-01-01", location: "Hall", description: "Details", publicationStatus: "published", createdAt: new Date("2025-12-01T00:00:00Z") }
  },
  marketplace_items: {
    id: "listing-1",
    data: {
      title: "Chair", price: "100", seller: "Resident One", contact: "09123456789",
      blockLotPhase: "A-1", description: "Used chair", imageUrls: [], sellerUid: "resident-one",
      createdAt: new Date("2025-12-01T00:00:00Z")
    }
  },
  community_chat: {
    id: "post-1",
    data: {
      senderName: "Resident One", message: "Hello", isAnonymous: false,
      isLostAndFound: false, imageUrls: [], senderUid: "resident-one",
      createdAt: new Date("2025-12-01T00:00:00Z")
    }
  },
  homeowner_concerns: {
    id: "concern-1",
    data: {
      title: "Light out", location: "Block A", status: "Pending", details: "Please check",
      imageUrls: [], reporterUid: "resident-one", createdAt: new Date("2025-12-01T00:00:00Z")
    }
  }
};

const ref = (db, collection, id = records[collection].id) => doc(db, collection, id);
const resident = () => testEnv.authenticatedContext("resident-one").firestore();
const otherResident = () => testEnv.authenticatedContext("resident-two").firestore();
const admin = () => testEnv.authenticatedContext("trusted-admin", { admin: true }).firestore();
const forgedAdmin = () => testEnv.authenticatedContext("forged-admin", { role: "admin" }).firestore();
const profileRoleOnlyAdmin = () => testEnv.authenticatedContext("profile-role-admin").firestore();

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId,
    firestore: {
      host: "127.0.0.1",
      port: 8085,
      rules: fs.readFileSync(firestoreRulesPath, "utf8")
    },
    storage: {
      host: "127.0.0.1",
      port: 9199,
      rules: fs.readFileSync(path.join(root, "storage.rules"), "utf8")
    }
  });
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.clearStorage();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    for (const [collection, record] of Object.entries(records)) {
      await setDoc(ref(db, collection, record.id), record.data);
    }
    await setDoc(doc(db, "community_chat/post-1/replies/reply-1"), {
      senderUid: "resident-one", senderName: "Resident One", text: "Reply",
      createdAt: new Date("2025-12-01T00:00:00Z")
    });
    await setDoc(doc(db, "community_chat/post-1/reactions/resident-one"), {
      uid: "resident-one", reaction: "👍", updatedAt: new Date("2025-12-01T00:00:00Z")
    });
    await setDoc(doc(db, "users/profile-role-admin"), {
      uid: "profile-role-admin", email: "role@example.com", username: "role_admin",
      accountStatus: "active", createdAt: new Date("2025-12-01T00:00:00Z"),
      updatedAt: new Date("2025-12-01T00:00:00Z"), lastLoginAt: new Date("2025-12-01T00:00:00Z"),
      role: "HOA Officer", isAdmin: false, roleRequestStatus: "none"
    });
    for (const [uid, role, roleVerified] of [
      ["resident-one", "Homeowner", true],
      ["resident-two", "Security", true],
      ["unverified-resident", "HOA", false]
    ]) {
      await setDoc(doc(db, `users/${uid}`), {
        uid, email: `${uid}@example.com`, username: uid.replaceAll("-", "_"),
        accountStatus: "active", role, roleVerified, isAdmin: false, roleRequestStatus: "none",
        createdAt: new Date("2025-12-01T00:00:00Z"),
        updatedAt: new Date("2025-12-01T00:00:00Z"), lastLoginAt: new Date("2025-12-01T00:00:00Z")
      });
    }
  });
});

after(async () => {
  await testEnv?.cleanup();
});

test("signed-out users cannot read any of the six collections", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  for (const collection of Object.keys(records)) {
    await assertFails(getDoc(ref(db, collection)));
  }
});

test("signed-in residents can read community information and marketplace/chat", async () => {
  const db = resident();
  for (const collection of ["announcements", "community_events", "marketplace_items", "community_chat"]) {
    await assertSucceeds(getDoc(ref(db, collection)));
  }
});

test("signed-in marketplace owners can store resident-only listing images", async () => {
  const ownerImage = storageRef(testEnv.authenticatedContext("resident-one").storage("gs://demo-santevi-rules.appspot.com"),
    "marketplace/resident-one/listing-1/image_0");
  const residentImage = storageRef(testEnv.authenticatedContext("resident-two").storage("gs://demo-santevi-rules.appspot.com"),
    "marketplace/resident-one/listing-1/image_0");
  const signedOutImage = storageRef(testEnv.unauthenticatedContext().storage("gs://demo-santevi-rules.appspot.com"),
    "marketplace/resident-one/listing-1/image_0");
  const bytes = new Uint8Array([1, 2, 3]);

  await assertSucceeds(uploadBytes(ownerImage, bytes, { contentType: "image/jpeg" }));
  await assertSucceeds(getBytes(residentImage));
  await assertFails(uploadBytes(residentImage, bytes, { contentType: "image/jpeg" }));
  await assertFails(getBytes(signedOutImage));
  await assertFails(uploadBytes(storageRef(testEnv.authenticatedContext("resident-one").storage("gs://demo-santevi-rules.appspot.com"),
    "marketplace/resident-one/listing-2/image_0"), bytes, { contentType: "text/plain" }));
  await assertFails(uploadBytes(storageRef(testEnv.authenticatedContext("resident-one").storage("gs://demo-santevi-rules.appspot.com"),
    "marketplace/resident-one/listing-1/image_3"), bytes, { contentType: "image/jpeg" }));
  const versionedImage = storageRef(testEnv.authenticatedContext("resident-one").storage("gs://demo-santevi-rules.appspot.com"),
    `marketplace/resident-one/listing-1/image_0_${"a".repeat(32)}`);
  await assertSucceeds(uploadBytes(versionedImage, bytes, { contentType: "image/jpeg" }));
  await assertSucceeds(deleteObject(versionedImage));
});

test("marketplace rules accept numeric PHP prices and only the seller's storage paths", async () => {
  const db = resident();
  await assertSucceeds(setDoc(doc(db, "marketplace_items/numeric-price"), {
    title: "Desk", price: 1500.5, seller: "Resident One", contact: "09123456789",
    blockLotPhase: "A-1", description: "Desk", imageUrls: [],
    imagePaths: ["marketplace/resident-one/numeric-price/image_0_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"],
    sellerUid: "resident-one", availabilityStatus: "AVAILABLE", createdAt: serverTimestamp()
  }));
  await assertFails(setDoc(doc(db, "marketplace_items/foreign-image"), {
    title: "Desk", price: 1500.5, seller: "Resident One", contact: "09123456789",
    blockLotPhase: "A-1", description: "Desk", imageUrls: [],
    imagePaths: ["marketplace/resident-two/foreign-image/image_0"],
    sellerUid: "resident-one", createdAt: serverTimestamp()
  }));
});

test("notification preferences and FCM tokens are owner controlled", async () => {
  const userRef = doc(resident(), "users/resident-one");
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "users/resident-one"), {
      uid: "resident-one", email: "resident@example.com", username: "resident_one",
      accountStatus: "active", createdAt: new Date("2025-12-01T00:00:00Z"),
      updatedAt: new Date("2025-12-01T00:00:00Z"), lastLoginAt: new Date("2025-12-01T00:00:00Z")
    });
  });
  await assertSucceeds(updateDoc(userRef, { notifyMarketplace: false }));
  await assertSucceeds(getDoc(userRef));
  await assertFails(updateDoc(doc(otherResident(), "users/resident-one"), { notifyMarketplace: true }));
  await assertFails(updateDoc(userRef, { notifyMarketplace: "yes" }));

  const ownToken = doc(resident(), "users/resident-one/fcmTokens/device-1");
  const otherToken = doc(otherResident(), "users/resident-one/fcmTokens/device-2");
  await assertSucceeds(setDoc(ownToken, {
    token: "test-device-token-1234567890", platform: "android", updatedAt: serverTimestamp()
  }));
  await assertFails(setDoc(otherToken, {
    token: "forged-device-token-1234567890", platform: "android", updatedAt: serverTimestamp()
  }));
  await assertFails(getDoc(ownToken));
});

test("Google-authenticated users can create and read their own profile and reserve their username", async () => {
  const uid = "google-resident-12345678";
  const username = "resident_12345678";
  const db = testEnv.authenticatedContext(uid).firestore();
  const userRef = doc(db, `users/${uid}`);
  const usernameRef = doc(db, `usernames/${username}`);

  await assertSucceeds(runTransaction(db, async (transaction) => {
    const existingUser = await transaction.get(userRef);
    const existingUsername = await transaction.get(usernameRef);
    assert.equal(existingUser.exists(), false);
    assert.equal(existingUsername.exists(), false);
    transaction.set(usernameRef, {
      username,
      uid,
      createdAt: serverTimestamp()
    });
    transaction.set(userRef, {
      uid,
      email: "resident@example.com",
      username,
      displayName: "Resident",
      photoUrl: "",
      providerIds: ["google.com"],
      emailVerified: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
      lastLoginAt: serverTimestamp(),
      termsAcceptedAt: serverTimestamp(),
      privacyPolicyAcceptedAt: serverTimestamp(),
      accountStatus: "active", role: "Visitor", isAdmin: false, roleRequestStatus: "none"
    });
  }));

  await assertSucceeds(getDoc(userRef));
  await assertSucceeds(getDoc(usernameRef));
  await assertFails(getDoc(doc(db, "users/another-resident")));
  const signedOutDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(signedOutDb, `users/${uid}`)));
  await assertFails(getDoc(doc(signedOutDb, `usernames/${username}`)));
  await assertFails(setDoc(doc(db, "usernames/claimed_by_someone_else"), {
    username: "claimed_by_someone_else",
    uid: "another-resident",
    createdAt: serverTimestamp()
  }));
});

test("new users must use the fixed Visitor defaults and cannot write privilege fields", async () => {
  const uid = "new-resident-12345678";
  const db = testEnv.authenticatedContext(uid).firestore();
  const profile = {
    uid, email: "new@example.com", username: "new_resident",
    accountStatus: "active", role: "Visitor", isAdmin: false, roleRequestStatus: "none",
    createdAt: serverTimestamp(), updatedAt: serverTimestamp(), lastLoginAt: serverTimestamp()
  };
  await assertSucceeds(setDoc(doc(db, `users/${uid}`), profile));
  await assertFails(updateDoc(doc(db, `users/${uid}`), { role: "HOA Officer" }));
  await assertFails(updateDoc(doc(db, `users/${uid}`), { isAdmin: true }));
  await assertFails(updateDoc(doc(db, `users/${uid}`), { roleRequestStatus: "approved" }));
  await assertFails(updateDoc(doc(db, `users/${uid}`), { requestedRole: "Security" }));
  await assertFails(setDoc(doc(db, "users/missing-defaults"), { ...profile, uid: "missing-defaults", role: "HOA Officer", isAdmin: true }));
});

test("role request records are private and client writes are denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "roleRequests/request-one"), {
      requestId: "request-one", requesterUid: "resident-one", requestedRole: "Security", status: "pending"
    });
  });
  await assertSucceeds(getDoc(doc(resident(), "roleRequests/request-one")));
  await assertFails(getDoc(doc(otherResident(), "roleRequests/request-one")));
  await assertSucceeds(getDoc(doc(admin(), "roleRequests/request-one")));
  await assertFails(setDoc(doc(resident(), "roleRequests/forged"), { requesterUid: "resident-one", status: "approved" }));
  await assertFails(updateDoc(doc(resident(), "roleRequests/request-one"), { status: "approved" }));
  await assertFails(updateDoc(doc(forgedAdmin(), "roleRequests/request-one"), { status: "approved" }));
});

test("concerns stay private while published reservations are visible to signed-in residents", async () => {
  await assertSucceeds(getDoc(ref(resident(), "homeowner_concerns")));
  await assertFails(getDoc(ref(otherResident(), "homeowner_concerns")));
  await assertSucceeds(getDoc(ref(admin(), "homeowner_concerns")));
  await assertFails(getDoc(ref(forgedAdmin(), "homeowner_concerns")));
  await assertSucceeds(getDoc(ref(resident(), "amenity_reservations")));
  await assertSucceeds(getDoc(ref(otherResident(), "amenity_reservations")));
  await assertSucceeds(getDoc(ref(admin(), "amenity_reservations")));
  await assertSucceeds(getDoc(ref(forgedAdmin(), "amenity_reservations")));
});

test("only trusted admins can publish community content or edit protected roles", async () => {
  const newAnnouncement = (uid) => ({
    title: "Notice", date: "2026-01-01", summary: "Short", fullText: "Details",
    createdByUid: uid, createdAt: serverTimestamp()
  });
  await assertFails(setDoc(doc(resident(), "announcements/homeowner"), newAnnouncement("resident-one")));
  await assertFails(setDoc(doc(testEnv.authenticatedContext("unverified-resident").firestore(), "announcements/pending"), newAnnouncement("unverified-resident")));
  await assertFails(updateDoc(doc(resident(), "users/resident-one"), { role: "HOA", roleVerified: true }));
  await assertFails(updateDoc(doc(resident(), "users/resident-one"), { requestedRole: "HOA" }));
  await assertFails(updateDoc(doc(otherResident(), "users/resident-two"), { isAdmin: true }));
  await assertFails(setDoc(doc(otherResident(), "announcements/verified-role"), newAnnouncement("resident-two")));
  await assertFails(setDoc(doc(resident(), "community_events/homeowner"), {
    eventName: "Meeting", date: "2026-01-01", location: "Hall", description: "Details",
    createdByUid: "resident-one", createdAt: serverTimestamp()
  }));
  await assertFails(setDoc(doc(forgedAdmin(), "announcements/forged"), newAnnouncement("forged-admin")));
  await assertFails(setDoc(doc(profileRoleOnlyAdmin(), "announcements/profile-role"), newAnnouncement("profile-role-admin")));
});

test("trusted admin claim manages announcements and community events", async () => {
  await assertSucceeds(setDoc(doc(admin(), "announcements/new"), {
    title: "Notice", date: "2026-01-01", summary: "Short", fullText: "Details",
    createdByUid: "trusted-admin", createdAt: serverTimestamp()
  }));
  await assertSucceeds(updateDoc(ref(admin(), "announcements"), { summary: "Updated" }));
  await assertSucceeds(deleteDoc(ref(admin(), "community_events")));
  await assertSucceeds(setDoc(doc(admin(), "community_events/new"), {
    eventName: "Meeting", date: "2026-01-01", location: "Hall", description: "Details",
    createdByUid: "trusted-admin", createdAt: serverTimestamp()
  }));
});

test("owners can create their marketplace listings and chat posts without spoofing UIDs", async () => {
  const db = resident();
  await assertSucceeds(setDoc(doc(db, "marketplace_items/new"), {
    title: "Desk", price: "200", seller: "Resident One", contact: "09123456789",
    blockLotPhase: "A-1", description: "Desk", imageUrls: [], sellerUid: "resident-one",
    availabilityStatus: "AVAILABLE", createdAt: serverTimestamp()
  }));
  await assertFails(setDoc(doc(db, "marketplace_items/spoof"), {
    title: "Desk", price: "200", seller: "Resident One", contact: "09123456789",
    blockLotPhase: "A-1", description: "Desk", imageUrls: [], sellerUid: "resident-two",
    createdAt: serverTimestamp()
  }));
  await assertSucceeds(setDoc(doc(db, "community_chat/new"), {
    senderName: "Resident One", message: "Hello", isAnonymous: false,
    isLostAndFound: false, imageUrls: [], senderUid: "resident-one",
    createdAt: serverTimestamp()
  }));
  await assertFails(setDoc(doc(db, "community_chat/spoof"), {
    senderName: "Resident One", message: "Hello", isAnonymous: false,
    isLostAndFound: false, imageUrls: [], senderUid: "resident-two",
    createdAt: serverTimestamp()
  }));
});

test("marketplace owners can edit listing details and change availability without changing creation time", async () => {
  const listingRef = ref(resident(), "marketplace_items");
  const before = await getDoc(listingRef);
  const originalCreatedAt = before.get("createdAt");
  await assertSucceeds(updateDoc(listingRef, {
    price: "120", updatedAt: serverTimestamp()
  }));
  await assertSucceeds(updateDoc(listingRef, {
    availabilityStatus: "SOLD", updatedAt: serverTimestamp()
  }));
  await assertSucceeds(updateDoc(listingRef, {
    availabilityStatus: "AVAILABLE", updatedAt: serverTimestamp()
  }));
  await assertFails(updateDoc(ref(otherResident(), "marketplace_items"), {
    availabilityStatus: "SOLD", updatedAt: serverTimestamp()
  }));
  await assertFails(updateDoc(ref(otherResident(), "marketplace_items"), {
    availabilityStatus: "AVAILABLE", updatedAt: serverTimestamp()
  }));
  await assertFails(updateDoc(listingRef, { sellerUid: "resident-two", updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(listingRef, {
    createdAt: new Date("2020-01-01T00:00:00Z"), updatedAt: serverTimestamp()
  }));
  const after = await getDoc(listingRef);
  assert.equal(after.get("availabilityStatus"), "AVAILABLE");
  assert.equal(after.get("createdAt").toMillis(), originalCreatedAt.toMillis());
});

test("marketplace owner detail edits support current image paths and preserve legacy fields", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "marketplace_items/current-schema"), {
      title: "Chair", price: 100, seller: "Resident One", contact: "09123456789",
      blockLotPhase: "A-1", description: "Used chair", imageUrls: [],
      imagePaths: ["marketplace/resident-one/current-schema/image_0"],
      sellerUid: "resident-one", availabilityStatus: "AVAILABLE",
      createdAt: new Date("2025-12-01T00:00:00Z"), legacyCategory: "furniture"
    });
  });

  const ownerListing = doc(resident(), "marketplace_items/current-schema");
  const changedImages = [
    "marketplace/resident-one/current-schema/image_0_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
  ];
  await assertSucceeds(updateDoc(ownerListing, {
    title: "Updated chair", price: 125, description: "Improved description",
    imagePaths: changedImages, imageUrls: [], updatedAt: serverTimestamp()
  }));
  await assertFails(updateDoc(doc(otherResident(), "marketplace_items/current-schema"), {
    title: "Changed by non-owner", updatedAt: serverTimestamp()
  }));
  await assertFails(updateDoc(ownerListing, {
    sellerUid: "resident-two", updatedAt: serverTimestamp()
  }));
  await assertFails(updateDoc(ownerListing, {
    createdAt: new Date("2020-01-01T00:00:00Z"), updatedAt: serverTimestamp()
  }));
  await assertFails(updateDoc(ownerListing, {
    title: "Combined status edit", availabilityStatus: "SOLD", updatedAt: serverTimestamp()
  }));
});

test("marketplace owner status-only transition permits legacy unrelated fields but no other edits", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "marketplace_items/legacy-extra-field"), {
      title: "Legacy listing", sellerUid: "resident-one", legacyCategory: "furniture"
    });
  });
  const ownerListing = doc(resident(), "marketplace_items/legacy-extra-field");
  await assertSucceeds(updateDoc(ownerListing, {
    availabilityStatus: "SOLD", updatedAt: serverTimestamp()
  }));
  await assertSucceeds(updateDoc(ownerListing, {
    availabilityStatus: "AVAILABLE", updatedAt: serverTimestamp()
  }));
  await assertFails(updateDoc(ownerListing, {
    availabilityStatus: "SOLD", title: "Changed by status operation", updatedAt: serverTimestamp()
  }));
  await assertFails(updateDoc(ownerListing, {
    availabilityStatus: "PAUSED", updatedAt: serverTimestamp()
  }));
  await assertFails(updateDoc(ownerListing, {
    availabilityStatus: "SOLD", sellerUid: "resident-two", updatedAt: serverTimestamp()
  }));
  await assertFails(updateDoc(ownerListing, {
    availabilityStatus: "SOLD", createdAt: new Date("2020-01-01T00:00:00Z"),
    updatedAt: serverTimestamp()
  }));
  await assertFails(updateDoc(doc(otherResident(), "marketplace_items/legacy-extra-field"), {
    availabilityStatus: "AVAILABLE", updatedAt: serverTimestamp()
  }));
  await assertFails(updateDoc(doc(forgedAdmin(), "marketplace_items/legacy-extra-field"), {
    availabilityStatus: "AVAILABLE", updatedAt: serverTimestamp()
  }));
});

test("marketplace legacy listings without a creation timestamp remain undated after edits", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "marketplace_items/legacy-without-date"), {
      title: "Legacy", price: "100", seller: "Resident One", contact: "09123456789",
      blockLotPhase: "A-1", description: "Legacy listing", imageUrls: [], sellerUid: "resident-one"
    });
  });
  const legacyRef = doc(resident(), "marketplace_items/legacy-without-date");
  await assertSucceeds(updateDoc(legacyRef, {
    availabilityStatus: "SOLD", updatedAt: serverTimestamp()
  }));
  const updated = await getDoc(legacyRef);
  assert.equal(updated.get("createdAt"), undefined);
  assert.equal(updated.get("availabilityStatus"), "SOLD");
});

test("only the owner or trusted admin can delete marketplace listings and chat posts", async () => {
  await assertFails(deleteDoc(ref(otherResident(), "marketplace_items")));
  await assertSucceeds(deleteDoc(ref(admin(), "marketplace_items")));
  await assertSucceeds(updateDoc(ref(resident(), "community_chat"), { message: "Edited" }));
  await assertFails(deleteDoc(ref(otherResident(), "community_chat")));
  await assertSucceeds(deleteDoc(ref(admin(), "community_chat")));
});

test("resident concerns require the reporter UID and admins can manage them", async () => {
  await assertSucceeds(setDoc(doc(resident(), "homeowner_concerns/new"), {
    title: "Repair", location: "Block A", status: "Pending", details: "Please repair",
    imageUrls: [], reporterUid: "resident-one", createdAt: serverTimestamp()
  }));
  await assertFails(setDoc(doc(resident(), "homeowner_concerns/spoof"), {
    title: "Repair", location: "Block A", status: "Pending", details: "Please repair",
    imageUrls: [], reporterUid: "resident-two", createdAt: serverTimestamp()
  }));
  await assertSucceeds(updateDoc(ref(admin(), "homeowner_concerns"), { status: "Resolved" }));
  await assertFails(updateDoc(ref(otherResident(), "homeowner_concerns"), { status: "Resolved" }));
});

test("only admins can publish amenity information and residents can only read published entries", async () => {
  const data = {
    amenityName: "Clubhouse", date: "2026-02-01", startTime: "10:00", endTime: "12:00",
    instructions: "Contact the HOA to request a reservation", publicationStatus: "published",
    createdByUid: "trusted-admin", createdAt: serverTimestamp()
  };
  await assertFails(setDoc(doc(resident(), "amenity_reservations/resident-new"), { ...data, createdByUid: "resident-one" }));
  await assertSucceeds(getDoc(ref(resident(), "amenity_reservations")));
  await assertSucceeds(setDoc(doc(admin(), "amenity_reservations/admin-new"), data));
  await assertSucceeds(getDoc(doc(resident(), "amenity_reservations/admin-new")));
  await assertFails(updateDoc(doc(resident(), "amenity_reservations/admin-new"), { instructions: "forged" }));
  await assertFails(deleteDoc(doc(resident(), "amenity_reservations/admin-new")));
});

test("chat replies and reactions use owner-bound subcollection paths", async () => {
  const owner = resident();
  await assertSucceeds(setDoc(doc(owner, "community_chat/post-1/replies/new"), {
    senderUid: "resident-one", senderName: "Resident One", text: "Reply",
    createdAt: serverTimestamp()
  }));
  await assertFails(setDoc(doc(owner, "community_chat/post-1/replies/spoof"), {
    senderUid: "resident-two", senderName: "Resident Two", text: "Reply",
    createdAt: serverTimestamp()
  }));
  await assertSucceeds(setDoc(doc(owner, "community_chat/post-1/reactions/resident-one"), {
    uid: "resident-one", reaction: "❤️", updatedAt: serverTimestamp()
  }));
  await assertFails(setDoc(doc(owner, "community_chat/post-1/reactions/resident-two"), {
    uid: "resident-two", reaction: "❤️", updatedAt: serverTimestamp()
  }));
  await assertFails(deleteDoc(doc(otherResident(), "community_chat/post-1/replies/reply-1")));
  await assertSucceeds(deleteDoc(doc(owner, "community_chat/post-1/replies/reply-1")));
});
