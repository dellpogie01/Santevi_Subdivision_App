const admin = require("firebase-admin");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { isOrdinaryRole, isElevatedRole } = require("./rolePolicy");

const db = admin.firestore();
const serverTimestamp = admin.firestore.FieldValue.serverTimestamp;

function requireUid(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in to continue.");
  return uid;
}

exports.setOrdinaryRole = onCall(async (request) => {
  const uid = requireUid(request);
  const role = request.data?.role;
  if (!isOrdinaryRole(role)) throw new HttpsError("invalid-argument", "Select Visitor or Homeowner.");
  const userRef = db.collection("users").doc(uid);
  await db.runTransaction(async (tx) => {
    const user = await tx.get(userRef);
    if (!user.exists) throw new HttpsError("failed-precondition", "Complete your profile first.");
    if (user.get("isAdmin") === true || request.auth.token.admin === true || isElevatedRole(user.get("role"))) {
      throw new HttpsError("failed-precondition", "An elevated role needs administrator review.");
    }
    if (user.get("roleRequestStatus") === "pending") throw new HttpsError("failed-precondition", "Your role request is under review.");
    tx.update(userRef, {
      role, roleVerified: false, requestedRole: "", roleRequestStatus: "none",
      updatedAt: serverTimestamp()
    });
  });
  return { role };
});

exports.submitRoleRequest = onCall(async (request) => {
  const uid = requireUid(request);
  const requestedRole = request.data?.requestedRole;
  const reason = typeof request.data?.reason === "string" ? request.data.reason.trim().slice(0, 1000) : "";
  if (!isElevatedRole(requestedRole)) throw new HttpsError("invalid-argument", "Select an eligible community role.");
  const userRef = db.collection("users").doc(uid);
  const requestRef = db.collection("roleRequests").doc();
  await db.runTransaction(async (tx) => {
    const user = await tx.get(userRef);
    if (!user.exists) throw new HttpsError("failed-precondition", "Complete your profile first.");
    if (user.get("roleRequestStatus") === "pending") throw new HttpsError("already-exists", "You already have a pending role request.");
    const authUser = await admin.auth().getUser(uid);
    const data = user.data();
    tx.create(requestRef, {
      requestId: requestRef.id, requesterUid: uid,
      displayName: data.displayName || data.username || "Community member",
      email: authUser.email || data.email || "",
      currentRole: data.role || "Visitor", requestedRole, reason,
      status: "pending", submittedAt: serverTimestamp(),
      reviewingAdminUid: "", reviewedAt: null, reviewNote: ""
    });
    tx.update(userRef, { requestedRole, roleRequestStatus: "pending", updatedAt: serverTimestamp() });
  });
  return { requestId: requestRef.id, status: "pending" };
});

exports.reviewRoleRequest = onCall(async (request) => {
  const adminUid = requireUid(request);
  if (request.auth.token.admin !== true) throw new HttpsError("permission-denied", "Administrator access is required.");
  const { requestId, decision } = request.data || {};
  const reviewNote = typeof request.data?.reviewNote === "string" ? request.data.reviewNote.trim().slice(0, 1000) : "";
  if (typeof requestId !== "string" || !["approved", "rejected"].includes(decision)) {
    throw new HttpsError("invalid-argument", "A request and approve/reject decision are required.");
  }
  const requestRef = db.collection("roleRequests").doc(requestId);
  let requesterUid;
  let requestedRole;
  await db.runTransaction(async (tx) => {
    const roleRequest = await tx.get(requestRef);
    if (!roleRequest.exists || roleRequest.get("status") !== "pending") throw new HttpsError("failed-precondition", "This request is no longer pending.");
    requesterUid = roleRequest.get("requesterUid");
    requestedRole = roleRequest.get("requestedRole");
    if (requesterUid === adminUid) throw new HttpsError("permission-denied", "You cannot review your own request.");
    if (!isElevatedRole(requestedRole)) throw new HttpsError("failed-precondition", "The requested role is invalid.");
    const userRef = db.collection("users").doc(requesterUid);
    const user = await tx.get(userRef);
    if (!user.exists || user.get("roleRequestStatus") !== "pending" || user.get("requestedRole") !== requestedRole) {
      throw new HttpsError("failed-precondition", "The user's pending request does not match.");
    }
    const now = serverTimestamp();
    if (decision === "approved") {
      tx.update(userRef, {
        role: requestedRole, roleVerified: true, isAdmin: true,
        roleRequestStatus: "approved", requestedRole: "", updatedAt: now
      });
    } else {
      tx.update(userRef, { roleRequestStatus: "rejected", requestedRole: "", updatedAt: now });
    }
    tx.update(requestRef, {
      status: decision, reviewingAdminUid: adminUid, reviewedAt: now, reviewNote
    });
  });
  if (decision === "approved") {
    const user = await admin.auth().getUser(requesterUid);
    await admin.auth().setCustomUserClaims(requesterUid, { ...(user.customClaims || {}), admin: true });
  }
  return { status: decision };
});

// Repairs the small distributed-transaction gap between committing an approved
// role in Firestore and updating the Authentication custom claim.
exports.reconcileApprovedAdminClaims = onSchedule("every 5 minutes", async () => {
  const approvedUsers = await db.collection("users").where("isAdmin", "==", true).get();
  for (const profile of approvedUsers.docs) {
    const user = await admin.auth().getUser(profile.id);
    if (user.customClaims?.admin !== true) {
      await admin.auth().setCustomUserClaims(profile.id, { ...(user.customClaims || {}), admin: true });
    }
  }
});
